/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.util.function.Function;

import se.motility.linkboy.model.DimensionStat;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;

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

}
