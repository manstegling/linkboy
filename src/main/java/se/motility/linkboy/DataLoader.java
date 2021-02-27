/*
 * Copyright (c) 2021 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.opencsv.CSVReader;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class providing methods to read relevant app data
 *
 * @author M Tegling
 */
public class DataLoader {

    private static final String NAN = "NA";
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final String COMMA = ",";
    private static final Logger LOG = LoggerFactory.getLogger(DataLoader.class);


    public static TasteSpace readTasteSpace(File file) {
        if (file.exists() && file.isFile()) {
            long start = System.currentTimeMillis();

            int rows = countRows(file);
            if (rows < 0) {
                return null;
            }

            // Read data line-by-line and convert to primitive types
            try (InputStream fis = new FileInputStream(file);
                 InputStream gis = file.getName().endsWith(".gz") ? new GZIPInputStream(fis, 4096) : fis;
                 InputStreamReader r = new InputStreamReader(gis, StandardCharsets.UTF_8);
                 BufferedReader buf = new BufferedReader(r)) {
                // Find number of columns from header
                String row = buf.readLine(); // header
                int cols = row.split(COMMA).length - 1; // don't count 'clusterId' column

                // Pre-allocate data structures for data
                int[] clusterIds = new int[rows];
                float[][] coordinates = new float[rows][cols];

                // Loop over input data and populate data structures
                String[] values;
                int i = 0;
                while ((row = buf.readLine()) != null) {
                    row = WHITESPACE.matcher(row).replaceAll("");
                    values = row.split(COMMA);
                    float[] coordinate = coordinates[i];
                    clusterIds[i] = Integer.parseInt(values[0]);
                    for (int j = 1; j < values.length; j++) {
                        coordinate[j-1] = Float.parseFloat(values[j]);
                    }
                    i++;
                }
                LOG.info("Read '{}' in {} ms", file.getAbsolutePath(), System.currentTimeMillis() - start);
                return new TasteSpace(clusterIds, coordinates);
            } catch (Exception e) {
                LOG.error("Exception encountered while reading '{}'. Message: {}",
                        file.getAbsolutePath(), e.getMessage());
            }
        } else {
            LOG.error("Could not find '{}'", file.getAbsolutePath());
        }
        return null;
    }

    public static MovieLookup readMovieMap(File file) {
        if (file.exists() && file.isFile()) {
            long start = System.currentTimeMillis();
            try (InputStream fis = new FileInputStream(file);
                 InputStream gis = file.getName().endsWith(".gz") ? new GZIPInputStream(fis, 4096) : fis;
                 InputStreamReader r = new InputStreamReader(gis, StandardCharsets.UTF_8);
                 BufferedReader buf = new BufferedReader(r);
                 CSVReader reader = new CSVReader(buf)) {

                reader.skip(1); // skip header

                List<Movie> movies = new ArrayList<>();
                String[] row;
                while ((row = reader.readNext()) != null) {
                    movies.add(new Movie(
                            Integer.parseInt(row[0]),
                            Integer.parseInt(row[1]),
                            row[2],
                            row[3],
                            NAN.equals(row[4]) ? Float.NaN : Float.parseFloat(row[4]),
                            NAN.equals(row[5]) ? -1 : Integer.parseInt(row[5])));

                }
                LOG.info("Read '{}' in {} ms", file.getAbsolutePath(), System.currentTimeMillis() - start);
                return new MovieLookup(movies);
            } catch (Exception e) {
                LOG.error("Exception encountered while reading '{}'. Message: {}",
                        file.getAbsolutePath(), e.getMessage());
            }
        } else {
            LOG.error("Could not find '{}'", file.getAbsolutePath());
        }
        return null;
    }

    // Returns a Map containing a user's movieId-rating pairs
    public static Int2DoubleOpenHashMap readUserRatings(File file) {
        if (file.exists() && file.isFile()) {
            long start = System.currentTimeMillis();
            try (InputStream fis = new FileInputStream(file);
                 InputStream gis = file.getName().endsWith(".gz") ? new GZIPInputStream(fis, 4096) : fis;
                 InputStreamReader r = new InputStreamReader(gis, StandardCharsets.UTF_8);
                 BufferedReader buf = new BufferedReader(r);
                 CSVReader reader = new CSVReader(buf)) {

                reader.skip(1); // skip header

                Int2DoubleOpenHashMap map = new Int2DoubleOpenHashMap();
                String[] row;
                while ((row = reader.readNext()) != null) {
                    map.put(Integer.parseInt(row[0]), Float.parseFloat(row[3]));
                }
                LOG.info("Read '{}' in {} ms", file.getAbsolutePath(), System.currentTimeMillis() - start);
                return map;
            } catch (Exception e) {
                LOG.error("Exception encountered while reading '{}'. Message: {}",
                        file.getAbsolutePath(), e.getMessage());
            }
        } else {
            LOG.error("Could not find '{}'", file.getAbsolutePath());
        }
        return null;
    }

    public static UserData readUserDataFull(File file, MovieLookup movieLookup, TasteSpace globalSpace) {
        Int2DoubleOpenHashMap userRatings = readUserRatings(file);
        if (userRatings == null) {
            return null;
        }
        int n = userRatings.size();
        int[] movieIds = new int[n];
        int[] clusterIds = new int[n];
        float[] ratings = new float[n];

        int k = 0;
        ObjectIterator<Int2DoubleMap.Entry> iter = userRatings.int2DoubleEntrySet().fastIterator();
        Int2DoubleMap.Entry entry;
        int movieId;
        while (iter.hasNext()) {
            entry = iter.next();
            movieId = entry.getIntKey();
            if (movieLookup.contains(movieId)) {
                movieIds[k] = movieId;
                ratings[k] = (float) entry.getDoubleValue();
                clusterIds[k] = movieLookup.getClusterId(movieId);
                k++;
            }
        }

        // Truncate the arrays to size k (instead of n)
        int[] mIds = new int[k];
        int[] cIds = new int[k];
        float[] rats = new float[k];
        System.arraycopy(movieIds, 0, mIds, 0, k);
        System.arraycopy(clusterIds, 0, cIds, 0, k);
        System.arraycopy(ratings, 0, rats, 0, k);

        float[][] userspace = new float[k][globalSpace.getDimensions()];
        for (int j = 0; j < k; j++) {
            userspace[j] = globalSpace.getCoordinate(globalSpace.getClusterIndex(cIds[j]));
        }

        return new UserData(mIds, cIds, rats, userspace);
    }

    // Efficient counting of lines in file taken from https://stackoverflow.com/a/5342096
    private static int countRows(File file) {
        try (InputStream fis = new FileInputStream(file);
             InputStream gis = file.getName().endsWith(".gz") ? new GZIPInputStream(fis, 4096) : fis;
             InputStreamReader r = new InputStreamReader(gis, StandardCharsets.UTF_8);
             BufferedReader buf = new BufferedReader(r);
             LineNumberReader count = new LineNumberReader(buf)) {
            while (count.skip(Long.MAX_VALUE) > 0) {/* Handles edge cases */}
            return count.getLineNumber() - 1; // don't count header
        } catch (IOException e) {
            LOG.error("Unable to count number of rows in '{}'. Message: {}",
                    file.getAbsolutePath(), e.getMessage());
            return -1;
        }
    }

    private DataLoader() {
        throw new UnsupportedOperationException("Do not instantiate utility class");
    }


}
