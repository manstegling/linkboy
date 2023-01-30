package se.motility.linkboy;

/**
 * Utility class providing dimension analysing functionality correlating
 * individual taste dimensions with model explanatory power.
 */
public class DimensionAnalyser {

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
