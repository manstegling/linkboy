/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.model.Movie;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.model.TasteSpace;
import se.motility.linkboy.model.UserData;
import se.motility.linkboy.util.IOExceptionThrowingSupplier;

/**
 * @author M Tegling
 */
public class Server {

    public static final String MOVIEMAP_PATH = "moviemap.dat.gz";
    public static final String TASTESPACE_PATH = "tastespace.dat.gz";
    private static final String DEFAULT_USER_FILE = "uXXX.csv.gz"; //or u86031.csv.gz

    private static final int USER_DIMENSIONS = 7; //make this configurable?
    private static final int MAX_RESULTS = 10;
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private MovieLookup movieLookup;
    private PathFinder finder;

    public MoviePath find(int startMovieId, int targetMovieId, String userFile) {
        initPathFinder();
        IOExceptionThrowingSupplier<InputStream> streamSupplier = userFile == null ? null : () -> open(userFile);
        return finder.find(startMovieId, targetMovieId, streamSupplier);
    }

    public MoviePath find(int startMovieId, int targetMovieId, IOExceptionThrowingSupplier<InputStream> userFileSupplier) {
        initPathFinder();
        return finder.find(startMovieId, targetMovieId, userFileSupplier);
    }

    public List<String> searchMovie(String term) {
        initSearch();
        long start = System.currentTimeMillis();
        List<Movie> result = movieLookup.search(term);
        LOG.debug("Took {} ms", System.currentTimeMillis() - start);
        if (result.isEmpty()) {
            return List.of();
        }
        List<String> output = result
                .stream()
                .limit(10)
                .map(m -> m.getTitle() + ": ID=" + m.getId())
                .collect(Collectors.toList());
        if (result.size() > MAX_RESULTS) {
            output.add("[...] truncated");
        }
        return output;
    }

    public Prediction predict(int movieId) {
        initPathFinder();
        return finder.predict(movieId, PathFinder.PredictionKernel.INVERSE_PROPORTIONAL);
    }

    /**
     * Initializes and preloads resources needed for performing searches. Can be used with e.g. Snapstart.
     */
    public void initSearch() {
        if (movieLookup == null) {
            movieLookup = read(MOVIEMAP_PATH, DataLoader::readMovieMap);
            if (movieLookup == null) {
                LOG.error("Could not read movie map at path '{}'", MOVIEMAP_PATH);
                throw new IllegalStateException("Could not read movie map at " + MOVIEMAP_PATH);
            }
        }
    }

    /**
     * Initializes and preloads resources needed for finding paths. Can be used with e.g. Snapstart.
     */
    public void initPathFinder() {
        if (finder == null) {
            initSearch();
            TasteSpace tasteSpace = read(TASTESPACE_PATH, DataLoader::readTasteSpace);
            if (tasteSpace == null) {
                LOG.error("Could not read taste-space at '{}'", TASTESPACE_PATH);
                throw new IllegalStateException("Could not read taste-space at " + TASTESPACE_PATH);
            }
            UserData defaultUserData = read(DEFAULT_USER_FILE,
                    in -> DataLoader.readUserDataFull(in, movieLookup, tasteSpace));
            if (defaultUserData == null) {
                LOG.error("Could not read default user ratings at '{}'", DEFAULT_USER_FILE);
                throw new IllegalStateException("Could not read default user ratings at " + DEFAULT_USER_FILE);
            }
            finder = new PathFinder(movieLookup, tasteSpace, defaultUserData, USER_DIMENSIONS, DimensionAnalyser.MIDPOINT_FUNCTION);
        }
    }

    private <T> T read(String path, ExceptionThrowingFunction<IOExceptionThrowingSupplier<InputStream>, T> fn) {
        try {
            long start = System.currentTimeMillis();
            IOExceptionThrowingSupplier<InputStream> supp = () -> open(path);
            T t = fn.apply(supp);
            LOG.info("Read '{}' in {} ms", path, System.currentTimeMillis() - start);
            return t;
        } catch (Exception e) {
            LOG.error("Exception encountered while reading '{}'. Message: {}",
                    path, e.getMessage());
            return null;
        }
    }

    private static InputStream open(String path) throws IOException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Could not open resource at " + path);
        }
        return new GZIPInputStream(in, 4096);
    }

    private interface ExceptionThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

}
