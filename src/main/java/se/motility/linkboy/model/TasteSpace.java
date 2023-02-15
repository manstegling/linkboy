/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy.model;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * @author M Tegling
 */
public class TasteSpace {

    private final int[] indexToId;
    private final Int2IntMap idToIndex;
    private final float[][] coordinates;
    private final int n;

    public TasteSpace(int[] clusterIds, float[][] coordinates) {
        this.n = clusterIds.length;
        this.indexToId = clusterIds;
        this.idToIndex = new Int2IntOpenHashMap(clusterIds.length);
        for (int i = 0; i < clusterIds.length; i++) {
            this.idToIndex.put(clusterIds[i], i);
        }
        this.coordinates = coordinates;
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

    public TasteSpace subspace(int[] dimensions) {
        float[][] subspace = new float[n][dimensions.length];
        float[] fullRow;
        float[] subRow;
        for (int i = 0; i < n; i++) {
            fullRow = coordinates[i];
            subRow = subspace[i];
            for (int j = 0; j < dimensions.length; j++) {
                subRow[j] = fullRow[dimensions[j]];
            }
        }
        return new TasteSpace(indexToId, subspace);
    }

}
