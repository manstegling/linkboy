package se.motility.linkboy.lambda;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindMovieIdRequestHandler implements RequestHandler<Map<String, String>, String> {

    private static final Logger LOG = LoggerFactory.getLogger(FindMovieIdRequestHandler.class);

    // Initialize last
    private static final ServerResource SERVER_RESOURCE = new ServerResource(true, false);

    @Override
    public String handleRequest(Map<String, String> request, Context context) {
        String term = request.get("term");
        LOG.info("Incoming request for term '{}'", term);
        List<String> result = SERVER_RESOURCE.server().searchMovie(term);
        FindMovieIdResponse response = new FindMovieIdResponse(result.toArray(new String[0]));
        return SERVER_RESOURCE.serializeResponse(response);
    }

}