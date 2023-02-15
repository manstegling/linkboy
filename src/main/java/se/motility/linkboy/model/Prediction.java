package se.motility.linkboy.model;

import java.util.Arrays;

public class Prediction {

    private final Movie movie;
    private final float predictedRating;
    private final Component[] components;

    public Prediction(Movie movie, float predictedRating, Component[] components) {
        this.movie = movie;
        this.predictedRating = predictedRating;
        this.components = components;
    }

    public Movie getMovie() {
        return movie;
    }

    public float getPredictedRating() {
        return predictedRating;
    }

    public Component[] getComponents() {
        return components;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Prediction{");
        sb.append("movie=").append(movie);
        sb.append(", predictedRating=").append(predictedRating);
        sb.append(", stats=").append(Arrays.toString(components));
        sb.append('}');
        return sb.toString();
    }

    public static class Component {
        private final int movieId;
        private final String title;
        private final float userRating;
        private final double distance;
        private final double proportion;

        public Component(int movieId, String title, float userRating, double distance, double proportion) {
            this.movieId = movieId;
            this.title = title;
            this.userRating = userRating;
            this.distance = distance;
            this.proportion = proportion;
        }

        public int getMovieId() {
            return movieId;
        }

        public String getTitle() {
            return title;
        }

        public float getUserRating() {
            return userRating;
        }

        public double getDistance() {
            return distance;
        }

        public double getProportion() {
            return proportion;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Stat{");
            sb.append("mId=").append(movieId);
            sb.append(", name='").append(title).append('\'');
            sb.append(", rating=").append(userRating);
            sb.append(", distance=").append(distance);
            sb.append(", proportion=").append(proportion);
            sb.append('}');
            return sb.toString();
        }
    }
}
