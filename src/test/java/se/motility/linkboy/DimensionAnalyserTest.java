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

}
