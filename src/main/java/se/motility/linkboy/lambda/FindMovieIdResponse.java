package se.motility.linkboy.lambda;

public class FindMovieIdResponse {

    private final String[] movies;

    public FindMovieIdResponse(String[] movies) {
        this.movies = movies;
    }

    public String[] getMovies() {
        return movies;
    }
}
