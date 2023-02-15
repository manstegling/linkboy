package se.motility.linkboy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
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
    public void pathLong() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2);

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
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 2);

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

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7);

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

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7);

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

        PathFinder finder = new PathFinder(movieLookup, tasteSpace, userData, 7);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            for (int cId = 1; cId <= 1000; cId++) {
                List<Movie> c1 = movieLookup.getCluster(cId);
                Prediction prediction = finder.predict(c1.get(0).getId());
                w.write(prediction.toString());
                w.newLine();
            }
        }
    }

}