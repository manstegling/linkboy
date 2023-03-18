package se.motility.linkboy.util;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

public class SampleIndexSequence {

    private final RandomGenerator rng = new Well19937c(987654312L);
    private final int minIdx;
    private final int maxIdx;

    public SampleIndexSequence(int minIdxInclusive, int maxIdxInclusive) {
        this.minIdx = minIdxInclusive;
        this.maxIdx = maxIdxInclusive;
    }

    public int[] getRandomSequence() {
        int n = maxIdx - minIdx + 1;
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i + minIdx;
        }
        for (int i = n; i > 1; i--) {
            swap(idx, i - 1, rng.nextInt(i));
        }
        return idx;
    }

    /**
     * Adopted from {@code swap(Object[] arr, int i, int j)} implementation in {@link java.util.Collections}.
     * Swaps places of two elements in an {@code int[]}. Note that {@code i == j} is accepted.
     *
     * @param arr index array
     * @param i   index 1
     * @param j   index 2
     */
    private static void swap(int[] arr, int i, int j) {
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

}