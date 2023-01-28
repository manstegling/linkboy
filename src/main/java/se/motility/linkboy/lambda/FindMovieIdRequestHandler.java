package se.motility.linkboy.lambda;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.ServerResource;

public class FindMovieIdRequestHandler implements RequestHandler<Map<String, String>, String> {

    private static final ServerResource SERVER_RESOURCE = new ServerResource();
    private static final Logger LOG = LoggerFactory.getLogger(FindMovieIdRequestHandler.class);

    @Override
    public String handleRequest(Map<String, String> request, Context context) {
        String term = request.get("term");
        LOG.info("Incoming request for term '{}'", term);
        List<String> result = SERVER_RESOURCE.server().searchMovie(term);
        return result == null ? "Internal error" :
                result.stream()
                      .map(m -> m + "\n")
                      .collect(Collectors.joining());
    }

}