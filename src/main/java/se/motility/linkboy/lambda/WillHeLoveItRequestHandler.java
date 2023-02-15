package se.motility.linkboy.lambda;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import se.motility.linkboy.model.Prediction;
import se.motility.linkboy.util.Util;

public class WillHeLoveItRequestHandler implements RequestHandler<Map<String, String>, String> {

    private static final String MOVIE_ID = "movieId";

    private static final ServerResource SERVER_RESOURCE = new ServerResource(true, true);

    @Override
    public String handleRequest(Map<String, String> request, Context context) {
        String movieIdStr = request.get(MOVIE_ID);
        int movieId = Util.parseMovieId(movieIdStr, MOVIE_ID);
        Prediction prediction = SERVER_RESOURCE.server()
                .predict(movieId);
        return SERVER_RESOURCE.serializeResponse(prediction);
    }

}
