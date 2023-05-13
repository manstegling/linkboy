/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.util.Arrays;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.model.DimensionStat;
import se.motility.linkboy.model.DistanceMatrix;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;

/**
 * @author M Tegling
 */
public class TasteOperations {

    private static final Logger LOG = LoggerFactory.getLogger(TasteOperations.class);
    private static final Comparator<DimensionStat> COMPARATOR = Comparator
            .comparingDouble(DimensionStat::getExplainedEntropy)
            .reversed();

    public static DistanceMatrix scaleToUser(TasteSpace space, UserData userdata, int rank, DimensionAnalyser analyser) {
        DimensionStat[] stats = analyser.analyse(userdata);
        Arrays.sort(stats, COMPARATOR);

        int[] dims = new int[rank];
        float[] explained = new float[rank];
        for (int i = 0; i < rank; i++) {
            dims[i] = stats[i].getDimIndex();
            explained[i] = (float) stats[i].getExplainedEntropy();
        }
        LOG.info("User preference from analyser '{}': {}", analyser.getName(), formatPreference(dims, explained));

        TasteSpace subspace = space.subspace(dims); // only focus on the dimensions relevant to the user
        TasteSpace localSpace = userdata.getSpace().subspace(dims);

        float[][] userCoordsRaw = subspace.getCoordinates();
        float[][] localColSpace = VectorMath.transpose(localSpace.getCoordinates());
        float[][] normalizedCols = VectorMath.byIndexedCol(userCoordsRaw, (i,x) -> normalize(x, localColSpace[i], explained[i]));

        return DistanceMatrix.compute(subspace.getClusterIds(), VectorMath.transpose(normalizedCols));
    }

    // Scale global set so that local subset has a variance of 'scale'
    private static float[] normalize(float[] xglobal, float[] xlocal, float scale) {
        float v = VectorMath.var(xlocal);
        float a = (float) Math.sqrt(scale / v);
        return VectorMath.axpb(xglobal, a,0f);
    }

    private static String formatPreference(int[] dims, float[] explained) {
        StringBuilder sb = new StringBuilder()
                .append("{");
        for (int i = 0; i < dims.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("D")
              .append(dims[i])
              .append(": ")
              .append(String.format("%.1f", explained[i]*100))
              .append("%");
        }
        return sb.append("}")
                 .toString();
    }

    private TasteOperations() {
        throw new UnsupportedOperationException("Do not instantiate utility class");
    }

}
