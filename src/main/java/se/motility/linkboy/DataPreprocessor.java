package se.motility.linkboy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

public class DataPreprocessor {

    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
        Thread.setDefaultUncaughtExceptionHandler(new MotUncaughtExceptionHandler());
    }

    // Target is to calculate 200 million pair-wise distances (~20 000 movies)

    private static final int MIN_COUNT = 20; // only consider movies with at least 20 ratings
    private static final String SEPARATOR = ",";
    private static final double AVG_DISSIMILARITY = 0.975d; // empirical value from data
    private static final double USER20_WEIGHT = computeWeight(20); // weight for a user with 20 ratings
    // We have ~12% missing pairs (19.9M out of 169.8M)

    private static final Logger LOG = LoggerFactory.getLogger(DataPreprocessor.class);

    private final Int2ObjectOpenHashMap<UserContext> userContextMap = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IntOpenHashSet> movieToUserMap = new Int2ObjectOpenHashMap<>();

    private final double defaultDistanceTerm;
    private final double defaultWeight;

    public static void main(String[] args) {
        //run preprocessor with no a priori information
//        DataPreprocessor preprocessor = new DataPreprocessor(0d, 0d);

        //run preprocessor and incorporate information about the target distribution
        DataPreprocessor preprocessor = new DataPreprocessor(AVG_DISSIMILARITY, USER20_WEIGHT);


        preprocessor.preprocess();
    }


    DataPreprocessor(double defaultDistance, double defaultWeight) {
        this.defaultDistanceTerm = defaultDistance * defaultWeight;
        this.defaultWeight = defaultWeight;
    }

    private void preprocess() {
        IntOpenHashSet movieIndices = readMovies();
        int[] idxArray = movieIndices.toArray(new int[0]);
        Arrays.sort(idxArray);

        Int2DoubleOpenHashMap userWeights = readUserWeights();

        readData(movieIndices);

        File f = new File("../data/dissimilarity-matrix.csv.gz");

        try (FileOutputStream fos = new FileOutputStream(f);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             OutputStreamWriter w = new OutputStreamWriter(gos);
             BufferedWriter buf = new BufferedWriter(w)) {

            // Write movie ID header
            write(buf, Arrays.toString(idxArray)); //FIXME get rid of '[' and ']'

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

        try (FileInputStream fis = new FileInputStream("../data/ratings.csv.gz");
             GZIPInputStream gis = new GZIPInputStream(fis);
             InputStreamReader r = new InputStreamReader(gis);
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
        try (Stream<String> s = Files.lines(Paths.get("../data/movie-counts.csv"))){
            return s.map(str -> str.split(","))
                    .filter(d -> Integer.parseInt(d[0]) >= MIN_COUNT)
                    .mapToInt(d -> Integer.parseInt(d[1]))
                    .collect(IntOpenHashSet::new, IntOpenHashSet::add, IntOpenHashSet::addAll);
        } catch (Exception e) {
            throw new RuntimeException("fail!", e);
        }
    }

    private static Int2DoubleOpenHashMap readUserWeights() {
        try (Stream<String> s = Files.lines(Paths.get("../data/user-counts.csv"))){
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

    private double computeDissimilarity(int movieIdx1, int movieIdx2, Int2DoubleOpenHashMap userWeights) {

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

        double distanceTermSum = defaultDistanceTerm;
        double weightSum = defaultWeight;
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
            return Double.NaN;  // no user has rated both movies
        } else if (distanceTermSum == 0d) {
            return 0d;          // all users have rated both movies with the exact same score
        } else {
            return distanceTermSum / weightSum;  // aggregated distance score
        }
    }

    private static class UserContext {

        final Int2DoubleOpenHashMap map = new Int2DoubleOpenHashMap();

        public void add(int movieIdx, double rating) {
            map.put(movieIdx, rating);
        }
    }



}
