/*
 * Copyright (c) 2021-2023 Måns Tegling
 *
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */
package se.motility.linkboy;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.util.MotUncaughtExceptionHandler;

/**
 * The entry-point to the app using the Linkboy CLI. This class is responsible for parsing
 * input arguments and running the desired parts of the program.
 *
 * @author M Tegling
 */
@Command(name = "Linkboy", mixinStandardHelpOptions = true, version = "1.0-SNAPSHOT",
         description = "A friendly recommender guiding you to new territory")
public class App implements Callable<Integer> {

    static {
        System.setProperty("log4j.configurationFile", "config/log4j2.xml");
        Thread.setDefaultUncaughtExceptionHandler(new MotUncaughtExceptionHandler());
        Locale.setDefault(Locale.ENGLISH);
    }

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    @ArgGroup(exclusive = true, multiplicity = "1")
    Arguments arguments;


    @Override
    public Integer call() {
        Server server = new Server();
        if (arguments.movieSearchArgs != null) {
            String term = arguments.movieSearchArgs.searchString;
            List<String> result = server.searchMovie(term);
            if (result != null) {
                if (result.isEmpty()) {
                    LOG.info("No movies containing '{}' found. Please try something else.", term);
                } else {
                    String output = result
                            .stream()
                            .map(m -> m + "\n")
                            .collect(Collectors.joining());
                    LOG.info("Movies found containing '{}':\n{}", term, output);
                }
                return 0;
            }
            return 1;
        } else if (arguments.pathFinderArgs != null) {
            long start = System.currentTimeMillis();
            MoviePath path = server.find(
                    arguments.pathFinderArgs.startMovieId,
                    arguments.pathFinderArgs.targetMovieId,
                    arguments.pathFinderArgs.userFile);
            if (path != null) {
                LOG.info("A good path was found between {} (C{}) and {} (C{}). Took {} ms.",
                        path.getMov1().getTitle(), path.getMov1().getClusterId(), path.getMov2().getTitle(),
                        path.getMov2().getClusterId(), System.currentTimeMillis() - start);
                LOG.info("Path details: {}", path);
            } else {
                LOG.warn("No path was found. Took {} ms", System.currentTimeMillis() - start);
            }
            return 0;
        } else if (arguments.willHeLoveItArgs != null) {
            long start = System.currentTimeMillis();
            Prediction prediction = server.predict(arguments.willHeLoveItArgs.movieId);
            LOG.info("Result: {}. Took {} ms", prediction, System.currentTimeMillis() - start);
            return 0;
        } else {
            LOG.error("Incorrect arguments");
            return 1; // should never happen
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }

    private static class Arguments {
        @ArgGroup(exclusive = false, multiplicity = "1", heading = "Path Finder Options%n")
        PathFinderArgs pathFinderArgs;

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "Movie Search Options%n")
        MovieSearchArgs movieSearchArgs;

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "Will He Love It Options%n")
        WillHeLoveItArgs willHeLoveItArgs;
    }

    private static class WillHeLoveItArgs {

        @Option(names = {"-q", "--q-movie-id"}, required = true, //TODO fix option
                description = "Movie ID")
        private int movieId;

    }

    private static class PathFinderArgs {

        @Option(names = {"-m", "--movie-id"}, required = true,
                description = "Target Movie ID")
        private int targetMovieId;

        @Option(names = {"-s", "--start-id"},
                description = "[Optional] Start Movie ID. If provided, will find the path between this movie and target movie")
        private int startMovieId;

        @Option(names = {"-u", "--user-file"},
                description = "[Optional] Provide your own ratings for personalized results. " +
                              "Otherwise, using the default profile.")
        private String userFile;
    }

    private static class MovieSearchArgs {
        @Option(names = {"-f", "--find"}, required = true,
                description = "Enter a string to search for Movie ID based on title")
        private String searchString;
    }
}