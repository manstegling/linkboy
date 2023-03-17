/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

import se.motility.linkboy.model.DimensionStat;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;
import se.motility.linkboy.util.SampleIndexSequence;

/**
 * Analysers for identifying user preference in individual taste dimensions,
 * along with model explanatory power.
 *
 * @author M Tegling
 */
public class DimensionAnalyser {

    /** The 'inverse function' analyser. Uses {@link DimensionAnalyser#analyseInverseFunction */
    public static final DimensionAnalyser INVERSE_FUNCTION = new DimensionAnalyser(
            DimensionAnalyser::analyseInverseFunction, "inverse function");

    /** The 'midpoint function' analyser. Uses {@link DimensionAnalyser#analyseMidpointFit}  */
    public static final DimensionAnalyser MIDPOINT_FUNCTION = new DimensionAnalyser(
            DimensionAnalyser::analyseMidpointFit, "midpoint function");

    private static final int MC_SAMPLES = 10_000;

    private final Function<UserData, DimensionStat[]> function;
    private final String name;

    private DimensionAnalyser(Function<UserData, DimensionStat[]> function, String name) {
        this.function = function;
        this.name = name;
    }

    public DimensionStat[] analyse(UserData data) {
        return function.apply(data);
    }

    public String getName() {
        return name;
    }

    /**
     * Calculates explained variance of the inverse ratings function. A dimension with
     * high explained variance has strong monotonicity and consistency. In plain language this
     * means we expect movies with the exact same rating to be placed close to each other
     * within a dimension for that dimension to be meaningful.
     * <p>
     * This requires the user data to include at least 2 movies with the exact same rating. For example,
     * this won't work if the user has only rated 11 movies and each movie has a unique rating
     * (0.0, 0.5, ..., 5.0). Ideally, we need several ratings included with 10+ movies each.
     * @param data user data
     * @return an Array containing statistics about each dimension based on the user's data
     */
    public static DimensionStat[] analyseInverseFunction(UserData data) {
        int k = data.getDimensions();
        UserData[] byRating = data.groupByRating();

        TasteSpace fullSpace = data.getSpace();

        float[] fullSse = VectorMath.byCol(fullSpace.getCoordinates(),
                arr -> VectorMath.sumOfSquared(arr, VectorMath.mean(arr)));

        float[] sse = new float[k];
        float[] tmp;
        for (UserData d : byRating) {
            tmp = VectorMath.byCol(
                    d.getSpace().getCoordinates(), arr -> VectorMath.sumOfSquared(arr, VectorMath.mean(arr)));
            if (tmp.length > 0) {
                VectorMath.addi(sse, tmp);
            }
        }

        DimensionStat[] result = new DimensionStat[k];
        for (int i = 0; i < k; i++) {
            result[i] = new DimensionStat(i, sse[i], fullSse[i]);
        }
        return result;
    }

    /**
     * Calculates explained variance of midpoint interpolation. In plain language this means we expect movies
     * close to each other in a particular dimension to have similar ratings.
     * <p>
     * In essence, we want to find dimensions that have an associated function f that maps the coordinate in
     * that dimension to a rating. We do not want to make any assumptions about f more than that is nice.
     * To accomplish this, we use midpoint interpolation to calculate the predicted rating r&#x0302;<sub>i</sub>
     * = 0.5 r<sub>i-1</sub> + 0.5 r<sub>i+1</sub>, with indexing based on movie coordinates in the current
     * dimension (from lowest to highest). The baseline is calculated using random movies instead of the movies
     * closest to the movie of interest.
     *
     * @implNote Uses Monte Carlo sampling to produce the baseline.
     * @param data user ratings
     * @return statistics about each dimension
     */
    public static DimensionStat[] analyseMidpointFit(UserData data) {
        int k = data.getDimensions();
        TasteSpace fullSpace = data.getSpace();

        float[][] columnSpace = VectorMath.transpose(fullSpace.getCoordinates());

        float[] ratings = data.getRatings();

        // Calculate baseline mse (independent of coordinate)
        SampleIndexSequence indexSampler = new SampleIndexSequence(0, ratings.length - 1);
        float[] mseParts = new float[MC_SAMPLES];
        int[] indices;
        for (int j = 0; j < mseParts.length; j++) {
            indices = indexSampler.getRandomSequence();
            mseParts[j] = calculateMidpointMse(ratings, indices);
        }
        float baselineMse = VectorMath.sum(mseParts) / mseParts.length;

        // Calculate model mse for each dimension
        float[] coordinates1d;
        float[] modelMse = new float[k];
        for (int i = 0; i < k; i++) {
            coordinates1d = columnSpace[i]; // Should we weight based on distance? Or is just midpoint enough?
            int[] sortedIndices = indexSort(coordinates1d);
            modelMse[i] = calculateMidpointMse(ratings, sortedIndices);
        }

        DimensionStat[] result = new DimensionStat[k];
        for (int i = 0; i < k; i++) {
            result[i] = new DimensionStat(i, modelMse[i], baselineMse);
        }
        return result;

    }

    private static float calculateMidpointMse(float[] ratings, int[] indices) {
        int n = ratings.length - 2; // endpoints not included
        float[] sse = new float[n];
        float predicted;
        float actual;
        for (int j = 1; j < indices.length - 1; j++) {
            predicted = (ratings[indices[j-1]] + ratings[indices[j+1]]) * 0.5f; // midpoint
            actual = ratings[indices[j]];
            sse[j-1] = (predicted - actual) * (predicted - actual);
        }
        return VectorMath.sum(sse) / sse.length;
    }

    static int[] indexSort(float[] arr) {
        ArrayIndexComparator cmp = new ArrayIndexComparator(arr);
        Integer[] indices = cmp.createIndexArray();
        Arrays.sort(indices, cmp);
        int[] result = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = indices[i];
        }
        return result;
    }

    private static class ArrayIndexComparator implements Comparator<Integer> {
        private final float[] array;

        public ArrayIndexComparator(float[] array) {
            this.array = array;
        }

        public Integer[] createIndexArray() {
            Integer[] indexes = new Integer[array.length];
            for (int i = 0; i < array.length; i++) {
                indexes[i] = i;
            }
            return indexes;
        }

        @Override
        public int compare(Integer k1, Integer k2) {
            return Float.compare(array[k1], array[k2]);
        }

    }

}
