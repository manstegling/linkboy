/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * A database containing metadata about all movies in the system.
 * <p>
 * Provides a simple interface for obtaining information about movies. All info is accessible
 * via the 'movieId'. If the identifier is not known, this class provides title search.
 *
 * @author M Tegling
 */
public class MovieLookup {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final Int2ObjectOpenHashMap<Movie> movies;
    private final Int2ObjectOpenHashMap<String> searchIndex;

    public MovieLookup(List<Movie> movies) {
        this.movies = movies.stream()
              .collect(Collectors.toMap(
                      Movie::getId, m -> m, (m1,m2) -> m1, Int2ObjectOpenHashMap::new));
        this.searchIndex = movies.stream()
              .collect(Collectors.toMap(
                      Movie::getId, m -> normalize(m.getTitle()), (m1,m2) -> m1, Int2ObjectOpenHashMap::new));
    }

    /**
     * Returns a list of movies containing the provided string. Search is case-insensitive and
     * disregards diacritics.
     * @param term part of title to search for
     * @return List of movies containing the provided query
     */
    public List<Movie> search(String term) {
        String q = normalize(term);
        Pattern pattern = Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE);

        // Using the .fastIterator() is considerably faster than using Streams
        ObjectIterator<Int2ObjectMap.Entry<String>> iter = searchIndex
                .int2ObjectEntrySet().fastIterator();

        List<Movie> result = new ArrayList<>();
        Int2ObjectMap.Entry<String> entry;
        while (iter.hasNext()) {
            entry = iter.next();
            if (pattern.matcher(entry.getValue()).find()) {
                result.add(movies.get(entry.getIntKey()));
            }
        }

        result.sort(Comparator.comparingInt(Movie::getId));
        return result;
    }

    public Movie getMovie(int movieId) {
        return movies.get(movieId);
    }

    public int getClusterId(int movieId) {
        return movies.get(movieId)
                     .getClusterId();
    }

    public boolean contains(int movieId) {
        return movies.containsKey(movieId);
    }

    /**
     * Returns a List of all movies belonging to the cluster with the provided ID.
     * The movies are returned in decreasing order with respect to rating.
     * @param clusterId to find associated movies for
     * @return List of movies belonging to the cluster
     */
    public List<Movie> getCluster(int clusterId) {
        // Using the .fastIterator() is considerably faster than using Streams
        ObjectIterator<Int2ObjectMap.Entry<Movie>> iter = movies
                .int2ObjectEntrySet().fastIterator();

        List<Movie> result = new ArrayList<>();
        Int2ObjectMap.Entry<Movie> entry;
        while (iter.hasNext()) {
            entry = iter.next();
            if (entry.getValue().getClusterId() == clusterId) {
                result.add(entry.getValue());
            }
        }
        result.sort(MovieLookup::decreasingOrder);
        return result;
    }

    /* Removes diacritics */
    private static String normalize(String str) {
        return DIACRITICS.matcher(Normalizer.normalize(str, Normalizer.Form.NFD))
                         .replaceAll("");
    }

    /* Highest rating first. Unrated last. */
    private static int decreasingOrder(Movie m1, Movie m2) {
        if (Float.isNaN(m1.getRating())) {
            return 1;
        } else if (Float.isNaN(m2.getRating())) {
            return -1;
        } else {
            return Float.compare(m2.getRating(), m1.getRating());
        }
    }

}
