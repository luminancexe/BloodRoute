package bloodroute.model;

/**
 * Represents a weighted edge in the road network graph.
 * Connects the source vertex to a destination vertex with distance in kilometers.
 */
public class Edge {
    private final int destination;
    private final double distanceKm;
    private final String roadName;

    public Edge(int destination, double distanceKm, String roadName) {
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Road distance cannot be negative: " + distanceKm);
        }
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.roadName = roadName;
    }

    public int getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public String getRoadName() {
        return roadName;
    }

    @Override
    public String toString() {
        return String.format("-> Node %d via %s (%.2f km)", destination, roadName, distanceKm);
    }
}
