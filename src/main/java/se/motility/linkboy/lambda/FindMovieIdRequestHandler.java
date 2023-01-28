package se.motility.linkboy.lambda;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindMovieIdRequestHandler implements RequestHandler<Map<String, String>, String> {

    private static final ServerResource SERVER_RESOURCE = new ServerResource();
    private static final Logger LOG = LoggerFactory.getLogger(FindMovieIdRequestHandler.class);

    @Override
    public String handleRequest(Map<String, String> request, Context context) {
        String term = request.get("term");
        LOG.info("Incoming request for term '{}'", term);
        List<String> result = SERVER_RESOURCE.server().searchMovie(term);
        StringBuilder response = new StringBuilder();

        response.append("{ ")
                .append("\"movies\": ")
                .append("[");
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                response.append(", ");
            }
            response.append("\"")
                    .append(result.get(i))
                    .append("\"");
        }
        response.append("]}");
        return response.toString();
    }

}