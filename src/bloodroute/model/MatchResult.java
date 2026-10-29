package bloodroute.model;

import bloodroute.ds.CustomLinkedList;

/**
 * Encapsulates the result of matching an emergency requester with a compatible donor.
 * Contains donor details, shortest road distance, estimated transit time, and route node IDs.
 */
public class MatchResult implements Comparable<MatchResult> {
    private final Donor donor;
    private final double roadDistanceKm;
    private final double estimatedTimeMinutes;
    private final CustomLinkedList<Integer> routePath;
    private final boolean isReachable;

    public MatchResult(Donor donor, double roadDistanceKm, double estimatedTimeMinutes,
                       CustomLinkedList<Integer> routePath, boolean isReachable) {
        this.donor = donor;
        this.roadDistanceKm = roadDistanceKm;
        this.estimatedTimeMinutes = estimatedTimeMinutes;
        this.routePath = routePath;
        this.isReachable = isReachable;
    }

    public Donor getDonor() {
        return donor;
    }

    public double getRoadDistanceKm() {
        return roadDistanceKm;
    }

    public double getEstimatedTimeMinutes() {
        return estimatedTimeMinutes;
    }

    public CustomLinkedList<Integer> getRoutePath() {
        return routePath;
    }

    public boolean isReachable() {
        return isReachable;
    }

    @Override
    public int compareTo(MatchResult other) {
        return Double.compare(this.roadDistanceKm, other.roadDistanceKm);
    }

    @Override
    public String toString() {
        return String.format("%s | Distance: %.2f km | ETA: %.1f mins | Status: %s",
                donor.getName(), roadDistanceKm, estimatedTimeMinutes, isReachable ? "Reachable" : "Unreachable");
    }
}
