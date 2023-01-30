package se.motility.linkboy;

import java.util.List;

public class MoviePath {

    private final Movie mov1;
    private final Movie mov2;
    private final List<List<Movie>> path;
    private final List<Integer> clusterIds;
    private final double distance;

    public MoviePath(Movie mov1, Movie mov2, List<List<Movie>> path, List<Integer> clusterIds, double distance) {
        this.mov1 = mov1;
        this.mov2 = mov2;
        this.path = path;
        this.clusterIds = clusterIds;
        this.distance = distance;
    }

    public Movie getMov1() {
        return mov1;
    }

    public Movie getMov2() {
        return mov2;
    }

    public List<List<Movie>> getPath() {
        return path;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder()
                .append("Clusters: ")
                .append(clusterIds)
                .append(", Distance: ")
                .append(String.format("%.3f",  distance))
                .append("\n");
        for (List<Movie> cluster : path) {
            sb.append("[\n  ");
            for (int i = 0; i < cluster.size(); i++) {
                if (i > 0) {
                    sb.append("  ");
                }
                sb.append(cluster.get(i).getTitle())
                  .append("\n");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

}
