/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.util.MotUncaughtExceptionHandler;

/**
 * A standalone preprocessor responsible for turning the full dataset of user-movie-rating triplets
 * into a movie-movie dissimilarity matrix. The matrix is written online to a file and is stored
 * in lower-triangular format (since the movie-movie graph is undirected).
 * <p>
 * See {@link #computeWeight} for more info on the dissimilarity measure. This is all also described
 * in the Linkboy documentation.
 *
 * @author M Tegling
 */
public class DataPreprocessor {

    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
        Thread.setDefaultUncaughtExceptionHandler(new MotUncaughtExceptionHandler());
    }

    // Target is to calculate 200 million pair-wise distances (~20 000 movies)

    private static final int MIN_COUNT = 20; // only consider movies with at least 20 ratings
    private static final String SEPARATOR = ",";
    private static final double DEFAULT_BIAS = 0.975d;  // empirical value from data
    private static final double DEFAULT_BIAS_W = computeWeight(20); // weight for a user with 20 ratings
    // We have ~12% missing pairs (19.9M out of 169.8M)

    private static final String DATA_PATH = "../data/movielens-datasets/20231013-32m/ml-32m/";

    private static final Logger LOG = LoggerFactory.getLogger(DataPreprocessor.class);

    private final Int2ObjectOpenHashMap<UserContext> userContextMap = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IntOpenHashSet> movieToUserMap = new Int2ObjectOpenHashMap<>();

    private final double biasTerm;
    private final double biasWeight;

    public static void main(String[] args) {
        DataPreprocessor preprocessor = null;
        if (args.length == 0) {
            preprocessor = new DataPreprocessor(0d, 0d);
        } else if (args.length == 1) {
            if ("default".equalsIgnoreCase(args[0])) {
                preprocessor = new DataPreprocessor(DEFAULT_BIAS, DEFAULT_BIAS_W);
            }
            throw new UnsupportedOperationException("do not use, since it forces the whole space" +
                                                    "towards the mean, introducing unwanted noise");
        } else if (args.length == 2) {
            try {
                int ratings = Integer.parseInt(args[0]);
                double bias = Double.parseDouble(args[1]);
                preprocessor = new DataPreprocessor(bias, computeWeight(ratings));
            } catch (RuntimeException e) {
                LOG.error("Incorrect argument format", e);
            }
        }

        if (preprocessor != null) {
            preprocessor.preprocess();
        } else {
            LOG.info("The program may take 0, 1 or 2 arguments" +
                     "\n - <no arg>: Computes an unbiased dissimilarity matrix with missing values as 'NaN'" +
                     "\n - Arg 'default', computes a matrix according to default (mean) bias" +
                     "\n - Args '<ratings> <bias>', computes a biased matrix accordingly. E.g. '200 0.975'");
        }
    }


    DataPreprocessor(double bias, double biasWeight) {
        this.biasTerm = bias * biasWeight;
        this.biasWeight = biasWeight;
    }

    private void preprocess() {
        IntOpenHashSet movieIndices = readMovies();
        int[] idxArray = movieIndices.toArray(new int[0]);
        Arrays.sort(idxArray);

        Int2DoubleOpenHashMap userWeights = readUserWeights();

        readData(movieIndices);

        File f = new File(DATA_PATH + "dissimilarity-matrix.csv.gz");

        try (FileOutputStream fos = new FileOutputStream(f);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             OutputStreamWriter w = new OutputStreamWriter(gos);
             BufferedWriter buf = new BufferedWriter(w)) {

            // Write movie ID header
            String header = Arrays
                    .stream(idxArray)
                    .mapToObj(Integer::toString)
                    .collect(Collectors.joining(SEPARATOR));
            write(buf, header);

            // Loop over all movie-pairs and write lower-triangular dissimilarity matrix
            for (int idx = 0; idx < idxArray.length; idx++) {
                StringBuilder sb = new StringBuilder();
                boolean init = false;
                // Compute dissimilarities for pairs left of diagonal
                for (int idx2 = 0; idx2 < idx; idx2++) {
                    if (init) {
                        sb.append(SEPARATOR);
                    }
                    sb.append(computeDissimilarity(idxArray[idx], idxArray[idx2], userWeights));
                    init = true;
                }
                // Fill rest of row with zeros
                for (int i = idx; i < idxArray.length; i++) {
                    if (init) {
                        sb.append(SEPARATOR);
                    }
                    sb.append(0d);
                    init = true;
                }
                write(buf, sb.toString());
                if (idx % 100 == 0) {
                    LOG.info("Row #{} written successfully", idx);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("fail", e);
        }
    }

    private void write(BufferedWriter buf, String line) throws IOException {
        buf.write(line);
        buf.newLine();
        buf.flush();
    }

    private void readData(IntOpenHashSet movies) {

        try (FileInputStream fis = new FileInputStream(DATA_PATH + "ratings.csv.gz");
             GZIPInputStream gis = new GZIPInputStream(fis, 4096);
             InputStreamReader r = new InputStreamReader(gis, StandardCharsets.UTF_8);
             BufferedReader buf = new BufferedReader(r)) {

            int rows = 1;

            // Skip header row
            buf.readLine();

            String[] arr;
            String line;
            while ((line = buf.readLine()) != null) {
                arr = line.split(",");
                int idx = Integer.parseInt(arr[1]);
                if (movies.contains(idx)) {
                    int uId = Integer.parseInt(arr[0]);
                    double rating = Double.parseDouble(arr[2]);
                    userContextMap.computeIfAbsent(uId, id -> new UserContext())
                                  .add(idx, rating);
                    movieToUserMap.computeIfAbsent(idx, i -> new IntOpenHashSet())
                                  .add(uId);
                }
                if (rows % 1000000 == 0) {
                    LOG.info("Rows read: {}", rows);
                }
                rows++;
            }
        } catch (Exception e) {
            throw new RuntimeException("fail!", e);
        }

    }

    private static IntOpenHashSet readMovies() {
        try (Stream<String> s = Files.lines(Paths.get(DATA_PATH + "movie-counts.csv"))){
            return s.map(str -> str.split(","))
                    .filter(d -> Integer.parseInt(d[0]) >= MIN_COUNT)
                    .mapToInt(d -> Integer.parseInt(d[1]))
                    .collect(IntOpenHashSet::new, IntOpenHashSet::add, IntOpenHashSet::addAll);
        } catch (Exception e) {
            throw new RuntimeException("fail!", e);
        }
    }

    private static Int2DoubleOpenHashMap readUserWeights() {
        try (Stream<String> s = Files.lines(Paths.get(DATA_PATH + "user-counts.csv"))){
            return s.map(str -> str.split(","))
                    .collect(Collectors.toMap(
                            p -> Integer.parseInt(p[1]),
                            p -> computeWeight(Integer.parseInt(p[0])),
                            (e1, e2) -> { throw new IllegalStateException("Same user appearing repeatedly: " + e1); },
                            Int2DoubleOpenHashMap::new));
        } catch (Exception e) {
            throw new RuntimeException("fail!", e);
        }
    }

    /**
     * Returns the weight for a user that has rated a certain number of movies. This weight is
     * used when computing the aggregated pair-wise distances across multiple users.
     * <p>
     * Due to the quadratic growth in movie pairs (with respect to number of movies rated) we need to
     * balance out frequent rater's global impact. I.e. the global impact of a user that has rated 20
     * movies should not be negligible compared to a user who has rated 200 movies. On the other hand,
     * the impact for a particular movie pair for a user who has rated 200 movies should not be
     * negligible compared to a user who has rated 20.
     * <p>
     * This weight function assigns approximately linear global impact with respect to number of movies
     * rated (the log-factor is almost constant). And conversely, the impact of any individual movie pair
     * is decreasing almost linearly with respect to the number of movies rated (the log-factor balances
     * the decrease slightly).
     *
     * @param moviesRated total number of movies rated by the user of interest
     * @return movie pair weight for a user with the specified number of movies rated
     */
    private static double computeWeight(int moviesRated) {
        return Math.log(moviesRated) / moviesRated;
    }

    // Returns float since we don't have much more accuracy anyway. This reduces disk size required for the
    // dissimilarity matrix. Accuracy can be improved e.g. by using VectorMath#sum.
    private float computeDissimilarity(int movieIdx1, int movieIdx2, Int2DoubleOpenHashMap userWeights) {

        IntOpenHashSet set1 = movieToUserMap.get(movieIdx1);
        IntOpenHashSet set2 = movieToUserMap.get(movieIdx2);

        IntOpenHashSet smaller;
        IntOpenHashSet larger;

        if (set1.size() <= set2.size()) {
            smaller = set1;
            larger = set2;
        } else {
            smaller = set2;
            larger = set1;
        }

        // Bias according to configuration
        double distanceTermSum = biasTerm;
        double weightSum = biasWeight;
        IntIterator iter = smaller.iterator();
        int i;
        while (iter.hasNext()) {
            i = iter.nextInt();
            if (larger.contains(i)) {
                UserContext userCtx = userContextMap.get(i);
                double w = userWeights.get(i);
                distanceTermSum += Math.abs(userCtx.map.get(movieIdx1) - userCtx.map.get(movieIdx2)) * w;
                weightSum += w;
            }
        }

        if (weightSum == 0d) {
            return Float.NaN;   // no user has rated both movies
        } else if (distanceTermSum == 0d) {
            return 0f;          // all users have rated both movies with the exact same score
        } else {
            return (float) (distanceTermSum / weightSum);  // aggregated distance score
        }
    }

    private static class UserContext {

        final Int2DoubleOpenHashMap map = new Int2DoubleOpenHashMap();

        public void add(int movieIdx, double rating) {
            map.put(movieIdx, rating);
        }
    }



}
