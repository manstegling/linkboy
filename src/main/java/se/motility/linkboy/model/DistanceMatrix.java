/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy.model;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.VectorMath;

/**
 * @author M Tegling
 */
public class DistanceMatrix {

    private static final Logger LOG = LoggerFactory.getLogger(DistanceMatrix.class);

    private final Int2IntMap idToIndex;
    private final float[][] distanceMatrix;

    public static DistanceMatrix compute(int[] clusterIds, float[][] coordinates) {
        int n = clusterIds.length;
        Int2IntMap idToIndex = new Int2IntOpenHashMap(clusterIds.length);
        for (int i = 0; i < clusterIds.length; i++) {
            idToIndex.put(clusterIds[i], i);
        }
        long start = System.currentTimeMillis();
        float[][] distanceMatrix = new float[n][n];

         float[] coord1;
        float[] coord2;
        float norm;
        for (int i = 0; i < n; i++) {
            coord1 = coordinates[i];
            for (int j = i + 1; j < n; j++) {
                coord2 = coordinates[j];
                norm = VectorMath.norm2(coord1, coord2);
                distanceMatrix[i][j] = norm;
            }
        }
        LOG.info("Distance matrix computed. Took {} ms", System.currentTimeMillis() - start);
        return new DistanceMatrix(idToIndex, distanceMatrix);
    }

    private DistanceMatrix(Int2IntMap idToIndex, float[][] distanceMatrix) {
        this.idToIndex = idToIndex;
        this.distanceMatrix = distanceMatrix;
    }

    public int getClusterIndex(int clusterId) {
        return idToIndex.get(clusterId);
    }

    public float getDistance(int index1, int index2) {
        // cIdx is the array index, not the cluster ID
        return distanceMatrix[Math.min(index1, index2)][Math.max(index1, index2)];
    }

    // recommended to use getDistance(...) whenever possible instead
    public float getDistanceById(int clusterId1, int clusterId2) {
        int index1 = idToIndex.get(clusterId1);
        int index2 = idToIndex.get(clusterId2);
        return getDistance(index1, index2);
    }

    public int getNumClusters() {
        return distanceMatrix.length;
    }

//    public TasteSpace createNormalized(UnaryOperator<float[]> normalizer) {
//        final int k = coordinates[0].length;
//        float[][] transposed = VectorMath.transpose(coordinates); // converts it to column-major
//        float[] column;
//        for (int i = 0; i < k; i++) {
//            column = transposed[i]; // normalize one whole column at a time
//            transposed[i] = normalizer.apply(column);
//        }
//        return new TasteSpace(indexToId, VectorMath.transpose(transposed)); // transpose back
//    }

}
