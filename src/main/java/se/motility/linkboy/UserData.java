/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

/**
 * @author M Tegling
 */
public class UserData {

    private final int n;
    private final int[] movieIds;
    private final int[] clusterIds;
    private final float[] ratings;
    private final TasteSpace space;

    public UserData(int[] movieIds, int[] clusterIds, float[] ratings, float[][] coordinates) {
        this.n = movieIds.length;
        this.movieIds = movieIds;
        //TODO validate all input has length n
        this.clusterIds = clusterIds;
        this.ratings = ratings;
        this.space = new TasteSpace(clusterIds, coordinates);
    }

    public boolean containsCluster(int clusterId) {
        for (int cId : clusterIds) {
            if (cId == clusterId) {
                return true;
            }
        }
        return false;
    }

    public int[] getMovieIds() {
        return movieIds;
    }

    public int getNumPoints() {
        return n;
    }

    public float getRating(int movieId) {
        for (int i = 0; i < movieIds.length; i++) {
            if (movieIds[i] == movieId) {
                return ratings[i];
            }
        }
        return Float.NaN;
    }

    public int getDimensions() {
        return space.getDimensions();
    }

    public TasteSpace getSpace() {
        return space;
    }

    public UserData[] groupByRating() {
        UserData[] datasets = new UserData[10];
        float r;
        int[] mIds;
        int[] cIds;
        float[] rats;
        float[][] coords;
        for (int i = 1; i < 11; i++) {
            r = i * 0.5f;
            int k = 0;
            for (float rating : ratings) {
                if (rating == r) {
                    k++;
                }
            }
            mIds = new int[k];
            cIds = new int[k];
            rats  = new float[k];
            coords  = new float[k][space.getDimensions()];
            k = 0;
            for (int j = 0; j < n; j++) {
                if (ratings[j] == r) {
                    mIds[k] = movieIds[j];
                    cIds[k] = clusterIds[j];
                    rats[k] = ratings[j];
                    coords[k] = space.getCoordinate(space.getClusterIndex(clusterIds[j]));
                    k++;
                }
            }
            datasets[i-1] = new UserData(mIds, cIds, rats, coords);
        }
        return datasets;
    }

}
