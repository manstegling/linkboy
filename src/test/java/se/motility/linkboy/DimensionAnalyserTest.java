package se.motility.linkboy;

import org.junit.Test;
import se.motility.linkboy.model.DimensionStat;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;

import static org.junit.Assert.*;
import static se.motility.linkboy.TestUtil.*;

public class DimensionAnalyserTest {

    private static final double DELTA = 1e-4;

    @Test
    public void simpleTest() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);

        DimensionStat[] stats = DimensionAnalyser.analyseInverseFunction(userData);

        DimensionStat stat0 = stats[0];
        assertEquals(0, stat0.getDimIndex());
        assertEquals(0.0529, stat0.getModelEntropy(), DELTA);
        assertEquals(0.1643, stat0.getBaselineEntropy(), DELTA);
        assertEquals(0.6782, stat0.getExplainedEntropy(), DELTA);

        DimensionStat stat1 = stats[1];
        assertEquals(1, stat1.getDimIndex());
        assertEquals(0.0135, stat1.getModelEntropy(), DELTA);
        assertEquals(0.0382, stat1.getBaselineEntropy(), DELTA);
        assertEquals(0.6470, stat1.getExplainedEntropy(), DELTA);

        DimensionStat stat2 = stats[2];
        assertEquals(2, stat2.getDimIndex());
        assertEquals(0.0092, stat2.getModelEntropy(), DELTA);
        assertEquals(0.0110, stat2.getBaselineEntropy(), DELTA);
        assertEquals(0.1615, stat2.getExplainedEntropy(), DELTA);
    }

    @Test
    public void testMidpoint() throws Exception {

        MovieLookup movieLookup = DataLoader.readMovieMap(() -> open("test-movie-map.csv", false));
        TasteSpace tasteSpace = DataLoader.readTasteSpace(() -> open("simple-test-taste-space.csv", false));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("simple-test-profile_dims-1-2.csv", false), movieLookup, tasteSpace);

        DimensionStat[] stats = DimensionAnalyser.analyseMidpointFit(userData);

        DimensionStat stat0 = stats[0];
        assertEquals(0, stat0.getDimIndex());
        assertEquals(1.0, stat0.getModelEntropy(), DELTA);
        assertEquals(3.9846, stat0.getBaselineEntropy(), DELTA);
        assertEquals(0.7490, stat0.getExplainedEntropy(), DELTA);

        DimensionStat stat1 = stats[1];
        assertEquals(1, stat1.getDimIndex());
        assertEquals(1.0, stat1.getModelEntropy(), DELTA);
        assertEquals(3.9847, stat1.getBaselineEntropy(), DELTA);
        assertEquals(0.7490, stat1.getExplainedEntropy(), DELTA);

        DimensionStat stat2 = stats[2];
        assertEquals(2, stat2.getDimIndex());
        assertEquals(4.4000, stat2.getModelEntropy(), DELTA);
        assertEquals(3.9847, stat2.getBaselineEntropy(), DELTA);
        assertEquals(-0.1042, stat2.getExplainedEntropy(), DELTA); // worse than random
    }

    @Test
    public void testArrayIndexSort() {
        float[] arr = {0.1f, 0.2f, 0.05f, 12f, -0.1f, 0f, -0f};
        int[] indices = DimensionAnalyser.indexSort(arr);
        int[] expected = {4, 6, 5, 2, 0, 1, 3};
        assertArrayEquals(expected, indices);
    }

    @Test
    public void testMidpointLinear() throws Exception {
        int[] idx = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        float[] y = new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f};
        // Only internal ranking in each input vector is key.
        // Since the y is monotonic, then any x that are monotonic score perfectly
        // x1: perfect fit linear y = 10x
        // x2: perfect fit linear y = 20x
        // x3: not perfect fit linear y ~= 10x (but monotonic => ranking-perfect)
        // x4: non-monotonic but better MSE fit than 3 (only 1 outlier),
        float[][] x = new float[][]{
        //       x1    x2    x3     x4
                {0.1f, 0.2f, 0.09f, 0.1f},
                {0.2f, 0.4f, 0.21f, 0.2f},
                {0.3f, 0.6f, 0.31f, 0.3f},
                {0.4f, 0.8f, 0.39f, 0.4f},
                {0.5f, 1.0f, 0.55f, 0.5f},
                {0.6f, 1.2f, 0.61f, 0.49f},
                {0.7f, 1.4f, 0.68f, 0.7f},
                {0.8f, 1.6f, 0.88f, 0.8f},
                {0.9f, 1.8f, 0.89f, 0.9f},
                {1.0f, 2.0f, 0.95f, 1.0f}
        };
        UserData userData = new UserData(idx, idx, y, x);
        DimensionStat[] stats = DimensionAnalyser.analyseMidpointFit(userData);
        assertEquals(4, stats.length);
        assertEquals(1d, stats[0].getExplainedEntropy(), DELTA);
        assertEquals(1d, stats[1].getExplainedEntropy(), DELTA);
        assertEquals(1d, stats[2].getExplainedEntropy(), DELTA);
        assertEquals(0.9545d, stats[3].getExplainedEntropy(), DELTA);
    }

    @Test
    public void testMidpointSine() throws Exception {
        int[] idx = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        float[] y = new float[]{0.841f,  0.909f,  0.141f, -0.756f, -0.958f, -0.279f, 0.656f,  0.989f,  0.412f, -0.544f};
        // x1: perfect fit y = sin(x)
        // x2: bad fit but positive monotonic (x square series)
        // x3: bad fit but negative monotonic (negative linear)
        // x4: flat (all x are equal)
        // x5: non-monotonic
        float[][] x = new float[][]{
        //       x1   x2    x3    x4  x5
                {1f,  1f,   1.0f, 1f, 1f},
                {2f,  4f,   0.9f, 1f, 3f},
                {3f,  9f,   0.8f, 1f, 2f},
                {4f,  16f,  0.7f, 1f, 5f},
                {5f,  25f,  0.6f, 1f, 4f},
                {6f,  36f,  0.5f, 1f, 6f},
                {7f,  49f,  0.4f, 1f, 5f},
                {8f,  64f,  0.3f, 1f, 7f},
                {9f,  81f,  0.2f, 1f, 8f},
                {10f, 100f, 0.1f, 1f, 9f}
        };
        UserData userData = new UserData(idx, idx, y, x);
        DimensionStat[] stats = DimensionAnalyser.analyseMidpointFit(userData);
        assertEquals(5, stats.length);
        assertEquals(0.8682d, stats[0].getExplainedEntropy(), DELTA);
        assertEquals(0.8682d, stats[1].getExplainedEntropy(), DELTA);
        assertEquals(0.8682d, stats[2].getExplainedEntropy(), DELTA);
        assertEquals(0.8682d, stats[3].getExplainedEntropy(), DELTA); //lucky to score good, depends on y's original order
        assertEquals(-0.1226d, stats[4].getExplainedEntropy(), DELTA); //worse than random
    }

}
