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
        private final int mId;
        private final String name;
        private final float rating;
        private final double distance;
        private final double proportion;

        public Component(int mId, String name, float rating, double distance, double proportion) {
            this.mId = mId;
            this.name = name;
            this.rating = rating;
            this.distance = distance;
            this.proportion = proportion;
        }

        public float getRating() {
            return rating;
        }

        public double getProportion() {
            return proportion;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Stat{");
            sb.append("mId=").append(mId);
            sb.append(", name='").append(name).append('\'');
            sb.append(", rating=").append(rating);
            sb.append(", distance=").append(distance);
            sb.append(", proportion=").append(proportion);
            sb.append('}');
            return sb.toString();
        }
    }
}
