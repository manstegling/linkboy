/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.model.DistanceMatrix;
import se.motility.linkboy.model.Movie;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;
import se.motility.linkboy.util.IOExceptionThrowingSupplier;

/**
 *
 *
 * @author M Tegling
 */
public class PathFinder {

    private static final Logger LOG = LoggerFactory.getLogger(PathFinder.class);
    private static final Comparator<Result> DISTANCE_COMPARATOR = Comparator.comparingDouble(Result::getDistance)
                                                                            .reversed();
    private static final Comparator<ClusterResult> C_DISTANCE_COMPARATOR = Comparator.comparingDouble(ClusterResult::getDistance)
                                                                            .reversed();

    // Hyper-parameters TODO make configurable
    private final double expansion = 1.15d; // 15% expansion of range
    private final float threshold = 4.5f;
    private final int maxJumps = 5;
    private final int nNearest = 10;

    private final MovieLookup movieLookup;
    private final TasteSpace tasteSpace;
    private final UserData defaultUserData;
    private final DistanceMatrix scaledDefaultDistances;
    private final int userDims;

    public enum PredictionKernel {
        INVERSE_PROPORTIONAL(x -> x > 0.05d ? 1/ x : 20d),
        GAUSSIAN(x -> Math.exp(-x*x*0.5)); //sigma=1 works well with my dataset
        final DoubleUnaryOperator weightFn;
        PredictionKernel(DoubleUnaryOperator weightFn) {
            this.weightFn = weightFn;
        }
    }

    public PathFinder(MovieLookup movieLookup, TasteSpace tasteSpace, UserData defaultUserData, int userDims, DimensionAnalyser analyser) {
        this.movieLookup = movieLookup;
        this.tasteSpace = tasteSpace;
        this.defaultUserData = defaultUserData;
        this.userDims = userDims;
        this.scaledDefaultDistances = TasteOperations.scaleToUser(tasteSpace, defaultUserData, userDims, analyser);
    }

    public MoviePath find(int movieId1, int movieId2, IOExceptionThrowingSupplier<InputStream> userDataSupplier) {
        if (!movieLookup.contains(movieId2)) {
            LOG.error("Unknown target movie ID '{}'", movieId2);
            return null;
        }
        UserData userData = loadUserData(userDataSupplier);
        DistanceMatrix scaledDistances = userDataSupplier != null
                ? TasteOperations.scaleToUser(tasteSpace, userData, userDims, DimensionAnalyser.MIDPOINT_FUNCTION)
                : scaledDefaultDistances;

        if (movieId1 == 0) {
            Result m1 = findNearestSuitable(movieId2, threshold, scaledDistances, movieLookup, userData);
            Movie m = movieLookup.getMovie(m1.movieId);
            LOG.info("Optimal starting point is {} (C{}), rating: {}, distance: {}", m.getTitle(),
                    m.getClusterId(), String.format("%.1f", m1.rating), String.format("%.3f", m1.distance));
            movieId1 = m1.movieId;
        }

        return findMoviePath(movieId1, movieId2, scaledDistances);
    }

    public Prediction predict(int movieId, PredictionKernel kernel) {
        Movie movie = movieLookup.getMovie(movieId);
        ClusterPrediction p = predictCluster(movie.getClusterId(), kernel);
        return new Prediction(movie, p.predictedRating, p.components);
    }

    private ClusterPrediction predictCluster(int clusterId, PredictionKernel kernel) {
        Result[] nearest = findNearestRated(clusterId, defaultUserData, scaledDefaultDistances, nNearest);
        Prediction.Component[] components = preparePrediction(nearest, kernel.weightFn);
        float predictedRating = computedWeightedAvg(components);
        double meanRating = VectorMath.mean(defaultUserData.getRatings());
        // Expand range to account for kNN prediction causing prediction to be biased towards mean
        float scaledPredictedRating = (float) ((predictedRating - meanRating) * expansion + meanRating);
        scaledPredictedRating = Math.max(0f, Math.min(5f, scaledPredictedRating)); // Prediction in [0,5]
        return new ClusterPrediction(clusterId, scaledPredictedRating, components);
    }

    private Prediction.Component[] preparePrediction(Result[] results, DoubleUnaryOperator weightFn) {
        double[] weights = new double[results.length];
        float[] ratings = new float[results.length];
        Result result;
        double weight;
        for (int i = 0; i < results.length; i++) {
            result = results[i];
            weight = weightFn.applyAsDouble(result.distance);
            weights[i] = weight;
            ratings[i] = result.rating;
        }

        double weightSum = VectorMath.sum(weights);

        if (weightSum <= 0d) {
            LOG.warn("Cannot computed weighted avg for {}", Arrays.toString(results));
            throw new IllegalArgumentException("Cannot compute weighted avg for " + Arrays.toString(results));
        }

        double proportion;
        Prediction.Component[] components = new Prediction.Component[results.length];
        for (int i = 0; i < results.length; i++) {
            result = results[i];
            proportion = weights[i] / weightSum;
            components[i] = new Prediction.Component(result.movieId, movieLookup.getMovie(result.movieId).getTitle(),
                    ratings[i], result.distance, proportion);
        }
        return components;
    }

    private float computedWeightedAvg(Prediction.Component[] components) {
        double[] normalizedWeights = new double[components.length];
        for (int i = 0; i < components.length; i++) {
            normalizedWeights[i] = components[i].getProportion() * components[i].getUserRating();
        }
        return (float) VectorMath.sum(normalizedWeights);
    }

    private UserData loadUserData(IOExceptionThrowingSupplier<InputStream> userDataSupplier) {
        if (userDataSupplier != null) {
            LOG.info("Loading provided user data");
            UserData userData = DataLoader.readUserDataFull(userDataSupplier, movieLookup, tasteSpace);
            if (userData == null) {
                LOG.error("Could not read user ratings. Falling back to default profile");
                return defaultUserData;
            } else {
                return userData;
            }
        }
        return defaultUserData;
    }

    public Prediction[] findRecommended(int movieId, IOExceptionThrowingSupplier<InputStream> userDataSupplier) {

        int kNearest = 5;

        UserData userData = loadUserData(userDataSupplier);

        DistanceMatrix scaledDistances = userDataSupplier != null
                ? TasteOperations.scaleToUser(tasteSpace, userData, userDims, DimensionAnalyser.MIDPOINT_FUNCTION)
                : scaledDefaultDistances;


        int clusterId = movieLookup.getClusterId(movieId);
        int index = scaledDistances.getClusterIndex(clusterId);
        int n = scaledDistances.getNumClusters();

        PriorityQueue<ClusterResult> queue = new ObjectHeapPriorityQueue<>(
                2*kNearest, C_DISTANCE_COMPARATOR);

        float d;
        int cId;
        for (int i = 0; i < n; i++) {
            if (i != index) {
                d = scaledDistances.getDistance(index, i);
                if (i < 2*kNearest) {
                    cId = tasteSpace.getClusterId(i);
                    queue.enqueue(new ClusterResult(cId, d));
                } else if (d < queue.first().distance) {
                    queue.dequeue();
                    cId = tasteSpace.getClusterId(i);
                    queue.enqueue(new ClusterResult(cId, d));
                }
            }
        }

        int[] clusterIds = new int[queue.size() + 1];
        float[][] coordinates = new float[queue.size() + 1][tasteSpace.getDimensions()];

        int i = 0;
        // Put the original movie cluster at position 0 to make it look nicer
        clusterIds[i] = clusterId;
        coordinates[i] = tasteSpace.getCoordinate(index);
        i++;
        ClusterResult result;
        while (!queue.isEmpty()) {
            result = queue.dequeue();
            clusterIds[i] = result.clusterId;
            coordinates[i] = tasteSpace.getCoordinate(tasteSpace.getClusterIndex(result.clusterId));
            i++;
        }

        DistanceMatrix globalDistances = DistanceMatrix.compute(clusterIds, coordinates);

        ClusterPrediction[] highestPredicted = new ClusterPrediction[clusterIds.length - 1];
        ClusterResult[] highestDistance = new ClusterResult[clusterIds.length - 1];
        for (int j = 0; j < clusterIds.length - 1; j++) {
            ClusterPrediction p = predictCluster(clusterIds[j+1], PredictionKernel.INVERSE_PROPORTIONAL);
            highestPredicted[j] = p;
            highestDistance[j] = new ClusterResult(clusterIds[j+1], globalDistances.getDistanceById(clusterIds[0], clusterIds[j+1]));
        }
        Arrays.sort(highestPredicted, Comparator.comparingDouble(ClusterPrediction::getPredictedRating).reversed());
        Arrays.sort(highestDistance, Comparator.comparingDouble(ClusterResult::getDistance).reversed());

        i = 0;
        int j = 0;
        int k = 0;
        Set<Integer> clusters = new HashSet<>();
        while (clusters.size() < kNearest) {
            if (i % 2 == 0) {
                clusters.add(highestPredicted[j].clusterId);
                j++;
            } else {
                clusters.add(highestDistance[k].clusterId);
                k++;
            }
            i++;
        }

        Prediction[] predictions = new Prediction[kNearest];
        j = 0;
        for (int id : clusters) {
            for (i = 0; i < highestPredicted.length; i++) {
                if (id == highestPredicted[i].clusterId) {
                    ClusterPrediction p = highestPredicted[i];
                    predictions[j] = new Prediction(movieLookup.getCluster(id).get(0), p.predictedRating, p.components);
                    j++;
                    break;
                }
            }
        }

        return predictions;
    }

    private MoviePath findMoviePath(int movieId1, int movieId2, DistanceMatrix distances) {
        Movie movie1 = movieLookup.getMovie(movieId1);

        int cIdx1 = distances.getClusterIndex(movieLookup.getClusterId(movieId1));
        int cIdx2 = distances.getClusterIndex(movieLookup.getClusterId(movieId2));
        ClusterPath path = findClusterPath(cIdx1, cIdx2, maxJumps, distances);

        if (path == null || Double.isInfinite(path.distance)) {
            Movie mov2 = movieLookup.getMovie(movieId2);
            LOG.warn("No suitable path found between {} (C{}) and {} (C{}) with {} or fewer jumps",
                    movie1.getTitle(), movie1.getClusterId(), mov2.getTitle(), mov2.getClusterId(), maxJumps);
            return null;
        }

        List<List<Movie>> clusters = new ArrayList<>();
        List<Integer> clusterIds = new ArrayList<>();
        for (int idx : path.clusterIndexes) {
            int clusterId = tasteSpace.getClusterId(idx);
            List<Movie> movies = movieLookup.getCluster(clusterId);
            clusters.add(movies.stream()
                               .limit(4)
                               .collect(Collectors.toList()));
            clusterIds.add(clusterId);
        }

        Movie movie2 = movieLookup.getMovie(movieId2);
        return new MoviePath(movie1, movie2, clusters, clusterIds, path.distance);
    }

    // Finds the movie nearest 'targetMovieId' in user sub-space, with a rating of at least 'minRating'
    // If no movie has sufficiently high rating, the nearest highest rated movie is returned.
    private Result findNearestSuitable(int targetMovieId, float minRating,
            DistanceMatrix distances, MovieLookup movieLookup, UserData userdata) {
        int targetClusterId = movieLookup.getClusterId(targetMovieId);
        int[] movieIds = userdata.getMovieIds();

        Result m1 = findNearestConstrained(movieIds, targetClusterId, minRating, distances, movieLookup, userdata);
        if (m1 == null) {
            // search again but without ratings threshold
            m1 = findNearestConstrained(movieIds, targetClusterId, Float.NEGATIVE_INFINITY, distances, movieLookup, userdata);
        }

        if (m1 == null) {
            LOG.error("No starting point in user data. Target mId {}, minRating {}", targetMovieId, minRating);
            throw new IllegalStateException("Could not find any suitable starting point");
        } else {
            return m1;
        }
    }

    // Finds the movie nearest 'targetMovieId' in user sub-space, with a rating of at least 'minRating'
    private Result findNearestConstrained(int[] movieIds, int targetClusterId, float minRating,
            DistanceMatrix distances, MovieLookup movieLookup, UserData userdata) {
        int movieId = -1;
        float rating = minRating;
        float distance = Float.POSITIVE_INFINITY;
        int cId;
        float r;
        float d;
        for (int mId : movieIds) {
            r = userdata.getRating(mId);
            if (r >= rating) {
                cId = movieLookup.getClusterId(mId);
                if (cId != targetClusterId) {
                    d = distances.getDistanceById(cId, targetClusterId);
                    if (d < distance) {
                        distance = d;
                        movieId = mId;
                        rating = r;
                    }
                }
            }
        }
        return movieId < 0 ? null : new Result(movieId, rating, distance);
    }


    private Result[] findNearestRated(int clusterId, UserData userdata, DistanceMatrix distances, int kNearest) {
        PriorityQueue<Result> queue = new ObjectHeapPriorityQueue<>(
                kNearest, DISTANCE_COMPARATOR);

        int[] movieIds = userdata.getMovieIds();

        int cId;
        float r;
        float d;
        int mId;
        for (int i = 0; i < movieIds.length; i++) {
            mId = movieIds[i];
            cId = movieLookup.getClusterId(mId);
            d = distances.getDistanceById(clusterId, cId);
            r = userdata.getRating(mId);
            if (i < kNearest) {
                queue.enqueue(new Result(mId, r, d));
            } else if (d < queue.first().distance) {
                queue.dequeue();
                queue.enqueue(new Result(mId,r , d));
            }
        }

        int n = queue.size();
        Result[] results = new Result[n];
        for (int i = 0; i < n; i++) {
            results[i] = queue.dequeue();
        }
        return results;
    }

    private ClusterPath findClusterPath(int clusterIndex1, int clusterIndex2, int maxJumps, DistanceMatrix distances) {
        float clusterDist = distances.getDistance(clusterIndex1, clusterIndex2);
        int jumps = maxJumps;
        ClusterPath path = null;
        // Gradually lower 'maxJumps' until a path can be found
        while ((path == null || Double.isInfinite(path.distance)) && jumps > 0) {
            double maxDist = (clusterDist / (double) jumps) * 1.5d;
            path = findPathRecursive(distances, clusterIndex1, clusterIndex2, jumps, maxDist);
            jumps--;
        }
        if (path == null || jumps < 0) {
            return null;
        }
        IntList complete = IntList.of(clusterIndex1);
        complete.addAll(path.clusterIndexes);
        return new ClusterPath(complete, path.distance);
    }

    private ClusterPath findPathRecursive(DistanceMatrix distances, int cIdx1, int cIdx2, int remaining, double maxDist) {
        if (remaining == 0 && cIdx1 == cIdx2) {
            return new ClusterPath(IntLists.EMPTY_LIST, 0d);
        } else if (remaining == 1) {
            float distance = distances.getDistance(cIdx1, cIdx2);
            if (distance > maxDist) {
                return null;
            } else {
                return new ClusterPath(IntList.of(cIdx2), distance);
            }
        } else if (remaining <= 0) {
            return null;
        }

        double distance = Double.POSITIVE_INFINITY;
        IntList path0 = IntLists.EMPTY_LIST;
        double d;
        double dUpd;
        ClusterPath next;
        for (int i = 0; i < distances.getNumClusters(); i++) {
            if (cIdx1 != i && cIdx2 != i && (d = distances.getDistance(cIdx1, i)) < maxDist) {
                next = findPathRecursive(distances, i, cIdx2, remaining-1, maxDist);
                if (next != null && (dUpd = d + next.distance) < distance) {
                    path0 = IntList.of(i);
                    path0.addAll(next.clusterIndexes);
                    distance = dUpd;
                }
            }
        }
        return new ClusterPath(path0, distance);
    }

    private static class ClusterPath {
        final IntList clusterIndexes;
        final double distance;

        public ClusterPath(IntList clusterIndexes, double distance) {
            this.clusterIndexes = clusterIndexes;
            this.distance = distance;
        }
    }

    private static class Result {
        final int movieId;
        final float rating;
        final double distance;

        public Result(int movieId, float rating, double distance) {
            this.movieId = movieId;
            this.rating = rating;
            this.distance = distance;
        }

        public double getDistance() {
            return distance;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Result{");
            sb.append("movieId=").append(movieId);
            sb.append(", rating=").append(rating);
            sb.append(", distance=").append(distance);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class ClusterResult {
        final int clusterId;
        final double distance;
        public ClusterResult(int clusterId, double distance) {
            this.clusterId = clusterId;
            this.distance = distance;
        }
        public double getDistance() {
            return distance;
        }
    }

    private static class ClusterPrediction {
        final int clusterId;
        final float predictedRating;
        final Prediction.Component[] components;
        public ClusterPrediction(int clusterId, float predictedRating,
                Prediction.Component[] components) {
            this.clusterId = clusterId;
            this.predictedRating = predictedRating;
            this.components = components;
        }
        public float getPredictedRating() {
            return predictedRating;
        }
    }

}
