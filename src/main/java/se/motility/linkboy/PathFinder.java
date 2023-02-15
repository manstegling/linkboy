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
import java.util.List;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // Hyper-parameters TODO make configurable
    private final float threshold = 4.5f;
    private final int maxJumps = 5;
    private final int nNearest = 10;

    private final MovieLookup movieLookup;
    private final TasteSpace tasteSpace;
    private final UserData defaultUserData;
    private final TasteSpace scaledDefaultUserSpace;
    private final int userDims;

    public PathFinder(MovieLookup movieLookup, TasteSpace tasteSpace, UserData defaultUserData, int userDims) {
        this.movieLookup = movieLookup;
        this.tasteSpace = tasteSpace;
        this.defaultUserData = defaultUserData;
        this.userDims = userDims;
        this.scaledDefaultUserSpace = TasteOperations.scaleToUser(tasteSpace, defaultUserData, userDims, DimensionAnalyser.INVERSE_FUNCTION);
    }

    public Movie findNearest(int movieId, IOExceptionThrowingSupplier<InputStream> userDataSupplier) {
        UserData userData = loadUserData(userDataSupplier);
        TasteSpace scaledSpace = userDataSupplier != null
                ? TasteOperations.scaleToUser(tasteSpace, userData, userDims, DimensionAnalyser.INVERSE_FUNCTION)
                : scaledDefaultUserSpace;
        // TODO: we need another version of this in which we find nearest _global movie not in user dataset_
        return findNearestSuitable(movieId, threshold, scaledSpace, movieLookup, userData);
    }

    public MoviePath find(int movieId1, int movieId2, IOExceptionThrowingSupplier<InputStream> userDataSupplier) {
        if (!movieLookup.contains(movieId2)) {
            LOG.error("Unknown target movie ID '{}'", movieId2);
            return null;
        }
        UserData userData = loadUserData(userDataSupplier);
        TasteSpace scaledSpace = userDataSupplier != null
                ? TasteOperations.scaleToUser(tasteSpace, userData, userDims, DimensionAnalyser.INVERSE_FUNCTION)
                : scaledDefaultUserSpace;
        return find(movieId1, movieId2, movieLookup, userData, scaledSpace);
    }

    public Prediction predict(int movieId) {
        Result[] nearest = findNearestRated(movieId, defaultUserData, scaledDefaultUserSpace, nNearest);
        Prediction.Component[] components = preparePrediction(nearest);
        float predictedRating = computedWeightedAvg(components);
        return new Prediction(movieLookup.getMovie(movieId), predictedRating, components);
    }

    private Prediction.Component[] preparePrediction(Result[] results) {
        double[] factors = new double[results.length];
        float[] ratings = new float[results.length];
        Result result;
        double factor;
        double factorSum = 0d;
        for (int i = 0; i < results.length; i++) {
            result = results[i];
            factor = result.distance > 0.05d ? 1/ result.distance : 20d; // cap at factor 20
            factors[i] = factor;
            factorSum += factor;
            ratings[i] = result.rating;
        }

        if (factorSum == 0d) {
            LOG.warn("Cannot computed weighted avg for {}", Arrays.toString(results));
            throw new IllegalArgumentException("Cannot compute weighted avg for " + Arrays.toString(results));
        }

        double proportion;
        Prediction.Component[] components = new Prediction.Component[results.length];
        for (int i = 0; i < results.length; i++) {
            result = results[i];
            proportion = factors[i] / factorSum;
            components[i] = new Prediction.Component(result.movieId, movieLookup.getMovie(result.movieId).getTitle(),
                    ratings[i], result.distance, proportion);
        }
        return components;
    }

    private float computedWeightedAvg(Prediction.Component[] components) {
        float weightedAvg = 0f;
        for (Prediction.Component s : components) {
            weightedAvg += s.getProportion() * s.getUserRating();
        }
        return weightedAvg;
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

    private MoviePath find(int movieId1, int movieId2, MovieLookup movieLookup, UserData userdata, TasteSpace scaledSpace) {
        Movie movie1 = movieId1 != 0
                ? movieLookup.getMovie(movieId1)
                : findNearestSuitable(movieId2, threshold, scaledSpace, movieLookup, userdata);

        ClusterPath path = findPath(movie1.getId(), movieId2, maxJumps, scaledSpace, movieLookup);

        if (path == null || Double.isInfinite(path.distance)) {
            Movie mov2 = movieLookup.getMovie(movieId2);
            LOG.warn("No suitable path found between {} (C{}) and {} (C{}) with {} or fewer jumps",
                    movie1.getTitle(), movie1.getClusterId(), mov2.getTitle(), mov2.getClusterId(), maxJumps);
            return null;
        }

        List<List<Movie>> clusters = new ArrayList<>();
        List<Integer> clusterIds = new ArrayList<>();
        for (int idx : path.clusterIndexes) {
            int clusterId = scaledSpace.getClusterId(idx);
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
    private Movie findNearestSuitable(int targetMovieId, float minRating,
            TasteSpace space, MovieLookup movieLookup, UserData userdata) {
        int targetClusterId = movieLookup.getClusterId(targetMovieId);
        int[] movieIds = userdata.getMovieIds();

        Result m1 = findNearestConstrained(movieIds, targetClusterId, minRating, space, movieLookup, userdata);
        if (m1 == null) {
            // search again but without ratings threshold
            m1 = findNearestConstrained(movieIds, targetClusterId, Float.NEGATIVE_INFINITY, space, movieLookup, userdata);
        }

        if (m1 == null) {
            LOG.error("Could not find any starting point in the user data");
            System.exit(1);
            throw new IllegalStateException("Could not find any suitable starting point");
        } else {
            Movie m = movieLookup.getMovie(m1.movieId);
            LOG.info("Optimal starting point is {} (C{}), rating: {}, distance: {}", m.getTitle(),
                    m.getClusterId(), String.format("%.1f", m1.rating), String.format("%.3f", m1.distance));
            return m;
        }
    }

    // Finds the movie nearest 'targetMovieId' in user sub-space, with a rating of at least 'minRating'
    private Result findNearestConstrained(int[] movieIds, int targetClusterId, float minRating,
            TasteSpace space, MovieLookup movieLookup, UserData userdata) {
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
                    d = space.getDistanceById(cId, targetClusterId);
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


    private Result[] findNearestRated(int movieId, UserData userdata, TasteSpace scaledSpace, int nNearest) {
        PriorityQueue<Result> queue = new ObjectHeapPriorityQueue<>(
                nNearest, DISTANCE_COMPARATOR);

        int clusterId = movieLookup.getClusterId(movieId);
        int[] movieIds = userdata.getMovieIds();

        int cId;
        float r;
        float d;
        int mId;
        for (int i = 0; i < movieIds.length; i++) {
            mId = movieIds[i];
            cId = movieLookup.getClusterId(mId);
            d = scaledSpace.getDistanceById(clusterId, cId);
            r = userdata.getRating(mId);
            if (i < nNearest) {
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

    private ClusterPath findPath(int movie1, int movie2, int maxJumps, TasteSpace space, MovieLookup movieLookup) {
        int cIdx1 = space.getClusterIndex(movieLookup.getClusterId(movie1));
        int cIdx2 = space.getClusterIndex(movieLookup.getClusterId(movie2));
        float clusterDist = space.getDistance(cIdx1, cIdx2);
        int jumps = maxJumps;
        ClusterPath path = null;
        // Gradually lower 'maxJumps' until a path can be found
        while ((path == null || Double.isInfinite(path.distance)) && jumps > 0) {
            double maxDist = (clusterDist / (double) jumps) * 1.5d;
            path = findPathRecursive(space, cIdx1, cIdx2, jumps, maxDist);
            jumps--;
        }
        if (path == null || jumps < 0) {
            return null;
        }
        IntList complete = IntList.of(cIdx1);
        complete.addAll(path.clusterIndexes);
        return new ClusterPath(complete, path.distance);
    }

    private ClusterPath findPathRecursive(TasteSpace space, int cIdx1, int cIdx2, int remaining, double maxDist) {
        if (remaining == 0 && cIdx1 == cIdx2) {
            return new ClusterPath(IntLists.EMPTY_LIST, 0d);
        } else if (remaining == 1) {
            float distance = space.getDistance(cIdx1, cIdx2);
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
        for (int i = 0; i < space.getNumClusters(); i++) {
            if (cIdx1 != i && cIdx2 != i && (d = space.getDistance(cIdx1, i)) < maxDist) {
                next = findPathRecursive(space, i, cIdx2, remaining-1, maxDist);
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

}
