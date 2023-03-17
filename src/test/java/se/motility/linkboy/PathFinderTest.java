package se.motility.linkboy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import se.motility.linkboy.model.Movie;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;

import static org.junit.Assert.*;
import static se.motility.linkboy.TestUtil.*;

public class PathFinderTest {

    private static final double DELTA = 1e-4;
    private static final String PREDICTION_BASELINE_FILE = "src/test/resources/baseline/prediction-baseline.dat";
    private static final String PREDICTION_CURRENT_FILE = "src/test/resources/prediction-current.dat";

    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);

    /* Random batches of 10 ints betwen 0 and 500 to use for masking my ratings file */
    private static final int[][] PREDICTION_SAMPLES = {
            {5,12,50,52,65,71,92,254,355,392},
            {66,166,181,202,211,295,314,425,426,446},
            {163,164,244,267,308,339,356,358,373,383},
            {58,85,143,160,213,305,309,424,442,493},
            {46,54,69,127,144,234,256,321,438,457},
            {11,26,27,139,222,262,354,435,482,498},
            {16,18,61,141,198,218,304,369,422,469},
            {76,90,112,148,158,219,221,330,361,386},
            {14,38,74,79,162,241,302,402,470,479},
            {28,51,124,125,126,179,197,407,454,495},
            {70,110,122,140,155,193,226,264,316,466},
            {4,36,40,68,75,118,186,200,276,389},
            {146,165,230,328,335,359,375,396,406,474},
            {35,101,152,240,331,337,366,428,461,481},
            {9,60,131,205,239,298,322,332,409,459},
            {7,24,43,119,120,154,175,311,343,408},
            {21,23,56,116,176,199,214,238,387,390},
            {10,111,121,191,216,220,292,315,326,357},
            {53,170,173,190,261,269,282,379,382,384},
            {22,95,138,185,194,203,251,277,286,351}};


    //Uncomment to generate a new baseline
    //@Test
    public void createPredictionRegressionBaseline() throws Exception {
        writePredictionOutput(PREDICTION_BASELINE_FILE);
    }

    @Test
    public void predictionRegressionTest() throws Exception {
        writePredictionOutput(PREDICTION_CURRENT_FILE);
        try (BufferedReader baseline = new BufferedReader(new FileReader(PREDICTION_BASELINE_FILE));
             BufferedReader current = new BufferedReader(new FileReader(PREDICTION_CURRENT_FILE))) {
            String base;
            String curr;
            while ((base = baseline.readLine()) != null) {
                curr = current.readLine();
                assertEquals(base, curr);
            }
            assertNull(current.readLine());
        }
    }

    @Test
    public void predictionAccuracyTest() throws Exception {
        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("moviemap.dat.gz", true));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("tastespace.dat.gz", true));
        List<Pair> awesome = new ArrayList<>();
        List<Pair> bland = new ArrayList<>();
        float sse = 0f;
        int n = 0;
        for (int i = 0; i < PREDICTION_SAMPLES.length; i++) {
            int[] masked = PREDICTION_SAMPLES[i];


            ByteArrayOutputStream model = new ByteArrayOutputStream();
            ByteArrayOutputStream test = new ByteArrayOutputStream();

            try (InputStream in = open("uXXX.csv.gz", true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

                String line = reader.readLine(); //header
                model.write(line.getBytes(StandardCharsets.UTF_8));
                model.write(NEWLINE);
                test.write(line.getBytes(StandardCharsets.UTF_8));
                test.write(NEWLINE);

                int k = 0;
                for (int mask : masked) {
                    while ((line = reader.readLine()) != null && k++ < mask) {
                        model.write(line.getBytes(StandardCharsets.UTF_8));
                        model.write(NEWLINE);
                    }
                    test.write(line.getBytes(StandardCharsets.UTF_8));
                    test.write(NEWLINE);
                }
                while ((line = reader.readLine()) != null) {
                    model.write(line.getBytes(StandardCharsets.UTF_8));
                    model.write(NEWLINE);
                }
            }

            UserData userData = DataLoader.readUserDataFull(
                    () -> new ByteArrayInputStream(model.toByteArray()), movieLookup, tasteSpace);

            UserData testData = DataLoader.readUserDataFull(
                    () -> new ByteArrayInputStream(test.toByteArray()), movieLookup, tasteSpace);

            PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7, DimensionAnalyser.INVERSE_FUNCTION);



            int[] testMovieIds = testData.getMovieIds();
            int mId;
            for (int j = 0; j < testMovieIds.length; j++) {
                mId = testMovieIds[j];
                Prediction p = finder.predict(mId, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
                float rating = testData.getRating(mId);
                sse += (p.getPredictedRating() - rating) * (p.getPredictedRating() - rating);
                n++;
                if (rating >= 4f) {
                    awesome.add(new Pair(rating, p.getPredictedRating()));
                } else {
                    bland.add(new Pair(rating, p.getPredictedRating()));
                }
            }

        }

        System.out.println("Loss (root-mean-square deviation) in sample: " + Math.sqrt(sse/n));
        System.out.println("Awesome: " + awesome.toString());
        System.out.println("Bland: " + bland.toString());

    }

    @Test
    public void pathLong() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2, DimensionAnalyser.INVERSE_FUNCTION);

        MoviePath path = finder.find(3, 2, null);


        assertEquals(3, path.getMov1().getId());
        assertEquals(2, path.getMov2().getId());
        assertEquals(2.4467, path.getDistance(), DELTA);

        assertEquals(3, path.getPath().size());
        assertEquals(1, path.getPath().get(0).size());
        assertEquals(1, path.getPath().get(1).size());
        assertEquals(1, path.getPath().get(2).size());

        assertEquals(3, path.getPath().get(0).get(0).getId()); //top right in the x-y plane
        assertEquals(7, path.getPath().get(1).get(0).getId()); //origo in the x-y plane
        assertEquals(2, path.getPath().get(2).get(0).getId()); //bottom left in the x-y plane
    }

    @Test
    public void pathShort() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2, DimensionAnalyser.INVERSE_FUNCTION);

        MoviePath path = finder.find(6, 3, null);

        assertEquals(6, path.getMov1().getId());
        assertEquals(3, path.getMov2().getId());
        assertEquals(0.3908, path.getDistance(), DELTA);

        assertEquals(2, path.getPath().size());
        assertEquals(1, path.getPath().get(0).size());
        assertEquals(1, path.getPath().get(1).size());

        assertEquals(6, path.getPath().get(0).get(0).getId()); //top right in the x-y plane
        assertEquals(3, path.getPath().get(1).get(0).getId()); //top right in the x-y plane, too

    }

    // The operation never completes when identifying a path between clusters 9 and 209. Needs investigation.
    //@Test
    public void perfTestWeirdBehavior() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("moviemap.dat.gz", true));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("tastespace.dat.gz", true));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("uXXX.csv.gz", true), movieLookup, tasteSpace);

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7, DimensionAnalyser.MIDPOINT_FUNCTION);

        int cId = 9;
        int cId2 = 209;
        List<Movie> c1 = movieLookup.getCluster(cId);
        List<Movie> c2 = movieLookup.getCluster(cId2);

        MoviePath path = finder.find(c1.get(0).getId(), c2.get(0).getId(), null);

        System.out.println(path.toString());
    }

    // Performance issues
    //@Test
    public void regressionTestPath() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("moviemap.dat.gz", true));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("tastespace.dat.gz", true));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("uXXX.csv.gz", true), movieLookup, tasteSpace);

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7, DimensionAnalyser.MIDPOINT_FUNCTION);

        int cId2;
        for (int cId = 1; cId <= 100; cId++) {
            cId2 = 200 + cId;
            List<Movie> c1 = movieLookup.getCluster(cId);
            List<Movie> c2 = movieLookup.getCluster(cId2);

            MoviePath path = finder.find(c1.get(0).getId(), c2.get(0).getId(), null);

            System.out.println(path.toString());
        }
    }

    private void writePredictionOutput(String filename) throws Exception {
        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("moviemap.dat.gz", true));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("tastespace.dat.gz", true));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("uXXX.csv.gz", true), movieLookup, tasteSpace);

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7, DimensionAnalyser.MIDPOINT_FUNCTION);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            for (int cId = 1; cId <= 1000; cId++) {
                List<Movie> c1 = movieLookup.getCluster(cId);
                Prediction prediction = finder.predict(c1.get(0).getId(), PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
                w.write(prediction.toString());
                w.newLine();
            }
        }
    }

    private static class Pair {

        private final float actual;
        private final float predicted;

        public Pair(float actual, float predicted) {
            this.actual = actual;
            this.predicted = predicted;
        }

        @Override
        public String toString() {
            return "[" + actual + ": " + predicted + "]";
        }
    }

} 