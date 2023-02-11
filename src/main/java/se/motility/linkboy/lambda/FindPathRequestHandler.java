package se.motility.linkboy.lambda;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.model.Movie;
import se.motility.linkboy.model.MoviePath;
import se.motility.linkboy.util.Util;

public class FindPathRequestHandler implements RequestHandler<Map<String, String>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(FindPathRequestHandler.class);

    private static final String START_ID = "startId";
    private static final String TARGET_ID = "targetId";

    // Initialize last
    private static final ServerResource SERVER_RESOURCE = new ServerResource(true, true);

    @Override
    public String handleRequest(Map<String, String> request, Context context) {

        String startId = request.get(START_ID);
        int startMovieId = Util.parseMovieId(startId, START_ID);

        String targetId = request.get(TARGET_ID);
        int targetMovieId = Util.parseMovieId(targetId, TARGET_ID);

        LOG.info("Trying to find path between {} and {}", startMovieId, targetMovieId);
        MoviePath result = SERVER_RESOURCE.server().find(startMovieId, targetMovieId, (String) null);
        if (result == null) {
            return "{\"message\": \"Could not find a path. Please check your input or try a different movie.\"}";
        }

        List<List<Movie>> path = result.getPath();
        StringBuilder response = new StringBuilder();
        response.append("{")
                .append("\"distance\": \"")
                .append(Math.round(result.getDistance() * 1e4) / 1e4) //trick to round double to 4 decimals
                .append("\", \"targetMovie\": \"")
                .append(result.getMov2().getTitle())
                .append(": ID=")
                .append(result.getMov2().getId())
                .append("\", \"path\": ")
                .append("[[");

        List<Movie> cluster; Movie m;
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                response.append("], [");
            }
            cluster = path.get(i);
            for (int j = 0; j < cluster.size(); j++) {
                if (j > 0) {
                    response.append(", ");
                }
                m = cluster.get(j);
                response.append("\"")
                        .append(m.getTitle())
                        .append(": ID=")
                        .append(m.getId())
                        .append("\"");
            }
        }
        response.append("]]}");
        return response.toString();
    }

}
