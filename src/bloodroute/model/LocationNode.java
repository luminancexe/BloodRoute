package bloodroute.model;

/**
 * Represents a vertex/node in the road network graph.
 * Corresponds to a real geographic intersection or medical facility in Dhaka.
 */
public class LocationNode {
    private final int id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final boolean isHospital;
    private final String area;

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isHospital = isHospital;
        this.area = area;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isHospital() {
        return isHospital;
    }

    public String getArea() {
        return area;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s)%s", id, name, area, isHospital ? " 🏥[Hospital]" : "");
    }
}
