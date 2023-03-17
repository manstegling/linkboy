package se.motility.linkboy;

import java.util.Arrays;
import java.util.function.IntFunction;

import org.junit.BeforeClass;
import org.junit.Test;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;
import se.motility.linkboy.util.SampleIndexSequence;

import static org.junit.Assert.assertEquals;
import static se.motility.linkboy.TestUtil.open;

public class PredictionTest {

    private static final double DELTA = 1e-4;

    private static MovieLookup movieLookup;
    private static TasteSpace tasteSpace;
    private static UserData training;
    private static UserData test;

    @Test
    public void predictionError2Midpoint() throws Exception {
        float mse = predictionError(2, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.5673, mse, DELTA);
    }

    @Test
    public void predictionError7Midpoint() throws Exception {
        float mse = predictionError(7, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.5166, mse, DELTA);
    }

    @Test
    public void predictionError10Midpoint() throws Exception {
        float mse = predictionError(10, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.4475, mse, DELTA);
    }


    @Test
    public void predictionError12Midpoint() throws Exception {
        float mse = predictionError(12, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.4380, mse, DELTA);
    }

    @Test
    public void predictionError14Midpoint() throws Exception {
        float mse = predictionError(14, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.4867, mse, DELTA);
    }


    @Test
    public void predictionError18Midpoint() throws Exception {
        float mse = predictionError(18, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.4997, mse, DELTA);
    }

    @Test
    public void predictionError2MidpointGaussian() throws Exception {
        float mse = predictionError(2, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.GAUSSIAN);
        assertEquals(0.6024, mse, DELTA);
    }

    @Test
    public void predictionError10MidpointGaussian() throws Exception {
        float mse = predictionError(10, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.GAUSSIAN);
        assertEquals(0.4713, mse, DELTA);
    }

    @Test
    public void predictionError12MidpointGaussian() throws Exception {
        float mse = predictionError(12, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.GAUSSIAN);
        assertEquals(0.4569, mse, DELTA);
    }

    @Test
    public void predictionError14MidpointGaussian() throws Exception {
        float mse = predictionError(14, DimensionAnalyser.MIDPOINT_FUNCTION, PathFinder.PredictionKernel.GAUSSIAN);
        assertEquals(0.5162, mse, DELTA);
    }


    @Test
    public void predictionError2Inverse() throws Exception {
        float mse = predictionError(2, DimensionAnalyser.INVERSE_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.5686, mse, DELTA);
    }

    @Test
    public void predictionError7Inverse() throws Exception {
        float mse = predictionError(7, DimensionAnalyser.INVERSE_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.5043, mse, DELTA);
    }

    @Test
    public void predictionError12Inverse() throws Exception {
        float mse = predictionError(12, DimensionAnalyser.INVERSE_FUNCTION, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
        assertEquals(0.4855, mse, DELTA);
    }

    // Baseline, always predicting mean rating from training set
    @Test
    public void predictionErrorBaseline() {
        float[] trainingRatings = training.getRatings();
        float mean = VectorMath.sum(trainingRatings) / trainingRatings.length;
        float mse = predictionError(mId -> new Prediction(null, mean,null), "mean rating", "0");
        assertEquals(0.8765, mse, DELTA);
    }

    @BeforeClass
    public static void setup() throws Exception {
        movieLookup = DataLoader.readMovieMap(() -> open("moviemap.dat.gz", true));
        tasteSpace = DataLoader.readTasteSpace(() -> open("tastespace.dat.gz", true));
        UserData userData = DataLoader.readUserDataFull(
                () -> open("uXXX.csv.gz", true), movieLookup, tasteSpace);
        Split split = createSplit(userData, 0.1f);

        training = split.training;
        test = split.test;
    }

    private float predictionError(int dimensions, DimensionAnalyser analyser, PathFinder.PredictionKernel kernel) {
        PathFinder finder = new PathFinder(movieLookup, tasteSpace, training, dimensions, analyser);
        IntFunction<Prediction> predictor = mId -> finder.predict(mId, kernel);
        return predictionError(predictor, analyser.getName(), String.valueOf(dimensions));
    }

    private float predictionError(IntFunction<Prediction> predictor, String analyserName, String dims) {
        int[] testMovieIds = test.getMovieIds();
        float[] ratings = test.getRatings();
        float[] sse = new float[testMovieIds.length];
        float[] absSum = new float[testMovieIds.length];
        for (int i = 0; i < testMovieIds.length; i++) {
            Prediction p = predictor.apply(testMovieIds[i]);
            sse[i] = (p.getPredictedRating() - ratings[i]) * (p.getPredictedRating() - ratings[i]);
            absSum[i] = Math.abs(p.getPredictedRating() - ratings[i]);
        }
        float mse = VectorMath.sum(sse) / testMovieIds.length;
        float l1e = VectorMath.sum(absSum) / testMovieIds.length;

        System.out.println("Analyser: " + analyserName + ", Dimensions: " + dims +
                           ", Training: " + training.getNumPoints() + ", Test: " +
                           test.getNumPoints() + ", MSE: " + mse + ", MSD:" + Math.sqrt(mse) +
                           ", L1: " + l1e);
        return mse;
    }


    private static Split createSplit(UserData userData, float testRatio) {
        if (testRatio <= 0f || testRatio >= 1f) {
            throw new IllegalArgumentException("testRatio must be strictly within range (0,1) to create split");
        }
        SampleIndexSequence sequence = new SampleIndexSequence(0, userData.getNumPoints()-1);
        int[] indexSeq = sequence.getRandomSequence(); // will always be the same!
        int testSamples = (int) (testRatio * indexSeq.length);
        if (testSamples < 1) {
            testSamples = 1;
        } else if (testSamples == indexSeq.length) {
            testSamples = indexSeq.length - 1;
        }
        int[] movieIds = userData.getMovieIds();
        int[] clusterIds = userData.getClusterIds();
        float[] ratings = userData.getRatings();
        float[][] coordinates = userData.getSpace().getCoordinates();
        UserData test = new UserData(
                Arrays.copyOfRange(movieIds, 0, testSamples),
                Arrays.copyOfRange(clusterIds, 0, testSamples),
                Arrays.copyOfRange(ratings, 0, testSamples),
                Arrays.copyOfRange(coordinates, 0, testSamples));
        UserData training = new UserData(
                Arrays.copyOfRange(movieIds, testSamples, movieIds.length),
                Arrays.copyOfRange(clusterIds, testSamples, clusterIds.length),
                Arrays.copyOfRange(ratings, testSamples, ratings.length),
                Arrays.copyOfRange(coordinates, testSamples, coordinates.length));

        return new Split(training, test);
    }

    private static class Split {
        final UserData training;
        final UserData test;
        public Split(UserData training, UserData test) {
            this.training = training;
            this.test = test;
        }
    }



}
