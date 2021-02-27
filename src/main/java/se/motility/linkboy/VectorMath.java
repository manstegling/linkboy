/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

/**
 * Utility class providing various vector and matrix operations.
 *
 * @author M Tegling
 */
public class VectorMath {

    /**
     * Creates and returns a transposed copy of the provided matrix
     * @param matrix to transpose
     * @return a transposed copy of provided matrix
     */
    public static float[][] transpose(float[][] matrix) {
        final int n = matrix.length;
        final int k = n > 0 ? matrix[0].length : 0;
        float[][] transposed = new float[k][n];
        float[] row;
        for (int i = 0; i < n; i++) {
            row = matrix[i];
            for (int j = 0; j < k; j++) {
                transposed[j][i] = row[j]; // is this slow?
            }
        }
        return transposed;
    }

    /**
     * Applies the provided {@code R^d -> R} function to each row of the provided {@code nxd} matrix
     * and collects and returns the result
     * @param matrix to operate on
     * @param fn {@code R^d -> R} function to apply to each row
     * @return a n-dimensional vector of the results
     */
    public static float[] byRow(float[][] matrix, ToDoubleFunction<float[]> fn) {
        final int k = matrix.length;
        float[] result = new float[k];
        for (int i = 0; i < k; i++) {
            result[i] = (float) fn.applyAsDouble(matrix[i]);
        }
        return result;
    }

    /**
     * Applies the provided {@code R^d -> R} function to each row of the provided {@code nxd} matrix
     * and collects and returns the result
     * @param matrix to operate on
     * @param fn {@code R^d -> R} function to apply to each row
     * @return a n-dimensional vector of the results
     */
    public static <T> void byIndexedRow(float[][] matrix, T[] resultHolder, BiFunction<Integer, float[], T> fn) {
        final int k = matrix.length;
        for (int i = 0; i < k; i++) {
            resultHolder[i] = fn.apply(i, matrix[i]);
        }
    }

    /**
     * Applies the provided {@code R^n -> R} function to each column of the provided {@code nxd} matrix
     * and collects and returns the result
     * @param matrix to operate on
     * @param fn {@code R^n -> R} function to apply to each column
     * @return a d-dimensional vector of the results
     */
    public static float[] byCol(float[][] matrix, ToDoubleFunction<float[]> fn) {
        float[][] transpose = transpose(matrix);
        return byRow(transpose, fn);
    }

    /**
     * Applies the provided {@code R^n -> R} function to each column of the provided {@code nxd} matrix
     * and collects and returns the result
     * @param matrix to operate on
     * @param fn {@code R^n -> R} function to apply to each column
     * @return a d-dimensional vector of the results
     */
    public static <T> void byIndexedCol(float[][] matrix, T[] resultHolder, BiFunction<Integer, float[], T> fn) {
        float[][] transpose = transpose(matrix);
        byIndexedRow(transpose, resultHolder, fn);
    }

    /**
     * Calculates the Euclidean norm between two n-dimensional points.
     * @param p1 first point
     * @param p2 second point
     * @return Euclidean norm between two n-dimensional points
     */
    public static float norm2(float[] p1, float[] p2) {
        double result = 0;
        for (int i=0; i < p1.length; i++) {
            result += p1[i]*p1[i] - 2*p1[i]*p2[i] + p2[i]*p2[i];
        }
        return (float) Math.sqrt(result);
    }

    /**
     * Calculates the mean value of the provided vector of values.
     * @param x vector of values
     * @return mean value of the provided vector
     */
    public static double mean(float[] x) {
        double res = 0d;
        for (double xi : x) {
            res += xi;
        }
        return res / x.length;
    }

    /**
     * Calculates the sum of all values in the provided vector.
     * Using Kahan's summation algorithm for high numerical stability.
     * @param x of values to be summed
     * @return sum of all values
     */
    public static float sum(float[] x) {
        double sum = 0d;
        double c = 0d;
        for (int i = 0; i < x.length; i++) {
            double y = x[i] - c;
            double t = sum + y;
            c = (t - sum) - y;
            sum = t;
        }
        return (float) sum;
    }

    /**
     * Scale and translate vector by scalars {@code a} and {@code b}, respectively.
     * @param x vector
     * @param a scale factor
     * @param b translation term
     * @return a new vector that has been scaled and translated
     */
    public static float[] axpb(float[] x, float a, float b) {
        float[] result = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = a*x[i] + b;
        }
        return result;
    }

    /**
     * Element-wise addition of the two provided n-dimensional vectors.
     * Returns a copy of the result.
     * @param x1 first vector
     * @param x2 second vector
     * @return a vector containing element-wise addition of the provided vectors
     */
    public static float[] add(float[] x1, float[] x2) {
        float[] result = new float[x1.length];
        for (int i = 0; i < x1.length; i++) {
            result[i] = x1[i] + x2[i];
        }
        return result;
    }

    /**
     * In-place element-wise addition of the two provided n-dimensional vectors.
     * The result is written to the first vector.
     * @param x1 first vector (will contain the result)
     * @param x2 second vector (added to the first vector)
     */
    public static void addi(float[] x1, float[] x2) {
        for (int i = 0; i < x1.length; i++) {
            x1[i] += x2[i];
        }
    }

    /**
     * Calculates the sum of squared deviations from the provided target value
     * @param x vector of values
     * @param target value
     * @return sum of squared deviations from the target value
     */
    public static double sumOfSquared(float[] x, double target) {
        double sse = 0d;
        for (double xi : x) {
            sse += (xi - target) * (xi - target);
        }
        return sse;
    }

    // Welford's online algorithm for computing variance
    public static float var(float[] x) {
        int count = 0;
        double mean = 0d;
        double m2 = 0d;
        double delta;
        double delta2;
        for (float xi : x) {
            count++;
            delta = xi - mean;
            mean += delta / count;
            delta2 = xi - mean;
            m2 += delta * delta2;
        }
        return count > 1 ? (float) (m2 / count) : Float.NaN;
    }


    private VectorMath() {
        throw new UnsupportedOperationException("Do not instantiate utility class");
    }

}
