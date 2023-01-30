package se.motility.linkboy;

import org.junit.Test;

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
        assertEquals(0, stat0.dimIndex);
        assertEquals(0.0529, stat0.modelEntropy, DELTA);
        assertEquals(0.1643, stat0.baselineEntropy, DELTA);
        assertEquals(0.6782, stat0.explainedEntropy, DELTA);

        DimensionStat stat1 = stats[1];
        assertEquals(1, stat1.dimIndex);
        assertEquals(0.0135, stat1.modelEntropy, DELTA);
        assertEquals(0.0382, stat1.baselineEntropy, DELTA);
        assertEquals(0.6470, stat1.explainedEntropy, DELTA);

        DimensionStat stat2 = stats[2];
        assertEquals(2, stat2.dimIndex);
        assertEquals(0.0092, stat2.modelEntropy, DELTA);
        assertEquals(0.0110, stat2.baselineEntropy, DELTA);
        assertEquals(0.1615, stat2.explainedEntropy, DELTA);
    }

}
