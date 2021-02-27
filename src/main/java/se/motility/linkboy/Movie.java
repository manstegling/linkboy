/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import com.opencsv.bean.CsvBindByPosition;

/**
 * @author M Tegling
 */
public class Movie {

    @CsvBindByPosition(position = 0)
    private int id;

    @CsvBindByPosition(position = 1)
    private int clusterId;

    @CsvBindByPosition(position = 2)
    private String title;

    @CsvBindByPosition(position = 3)
    private String genres;

    @CsvBindByPosition(position = 4)
    private float rating;

    @CsvBindByPosition(position = 5)
    private int votes;

    public Movie(int id, int clusterId, String title, String genres, float rating, int votes) {
        this.id = id;
        this.clusterId = clusterId;
        this.title = title;
        this.genres = genres;
        this.rating = rating;
        this.votes = votes;
    }

    public Movie() {
        // needed for deserialization
    }

    public int getId() {
        return id;
    }

    public int getClusterId() {
        return clusterId;
    }

    public String getTitle() {
        return title;
    }

    public String getGenres() {
        return genres;
    }

    public float getRating() {
        return rating;
    }

    public int getVotes() {
        return votes;
    }

    @Override
    public String toString() {
        return title + ", movieId: " + id + ", clusterId: " + clusterId;
    }
}
