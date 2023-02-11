/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy.model;

import java.util.function.UnaryOperator;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.VectorMath;

/**
 * @author M Tegling
 */
public class TasteSpace {

    private static final Logger LOG = LoggerFactory.getLogger(TasteSpace.class);

    private final int[] indexToId;
    private final Int2IntMap idToIndex;
    private final float[][] coordinates;
    private final int n;

    private float[][] distanceMatrix;
    private boolean init = false;

    public TasteSpace(int[] clusterIds, float[][] coordinates) {
        this.n = clusterIds.length;
        this.indexToId = clusterIds;
        this.idToIndex = new Int2IntOpenHashMap(clusterIds.length);
        for (int i = 0; i < clusterIds.length; i++) {
            this.idToIndex.put(clusterIds[i], i);
        }
        this.coordinates = coordinates;
    }

    public void computeDistances() {
        if (!init) {
            long start = System.currentTimeMillis();
            distanceMatrix = new float[n][n];
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
            init = true;
            LOG.info("Distance matrix computed. Took {} ms", System.currentTimeMillis() - start);
        }
    }

    public float getDistance(int index1, int index2) {
        // cIdx is the array index, not the cluster ID
        return distanceMatrix[Math.min(index1, index2)][Math.max(index1, index2)];
    }

    // recommended to use getDistance(...) whenever possible instead
    public float getDistanceById(int id1, int id2) {
        int index1 = getClusterIndex(id1);
        int index2 = getClusterIndex(id2);
        return getDistance(index1, index2);
    }

    public float[] getCoordinate(int index) {
        return coordinates[index]; // make it return a deep copy?
    }

    public float[][] getCoordinates() {
        return coordinates;
    }

    public int[] getClusterIds() {
        return indexToId;
    }

    public int getNumClusters() {
        return n;
    }

    public int getDimensions() {
        return coordinates[0].length;
    }

    public int getClusterId(int index) {
        return indexToId[index];
    }

    public int getClusterIndex(int id) {
        return idToIndex.get(id);
    }

    public TasteSpace createSubspace(int[] dims) {
        float[][] subspace = new float[n][dims.length];
        float[] fullRow;
        float[] subRow;
        for (int i = 0; i < n; i++) {
            fullRow = coordinates[i];
            subRow = subspace[i];
            for (int j = 0; j < dims.length; j++) {
                subRow[j] = fullRow[dims[j]];
            }
        }
        return new TasteSpace(indexToId, subspace);
    }

    public TasteSpace createNormalized(UnaryOperator<float[]> normalizer) {
        final int k = coordinates[0].length;
        float[][] transposed = VectorMath.transpose(coordinates); // converts it to column-major
        float[] column;
        for (int i = 0; i < k; i++) {
            column = transposed[i]; // normalize one whole column at a time
            transposed[i] = normalizer.apply(column);
        }
        return new TasteSpace(indexToId, VectorMath.transpose(transposed)); // transpose back
    }

}
