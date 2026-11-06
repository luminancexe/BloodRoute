package bloodroute.model;

/**
 * Represents a vertex/node in the nationwide road network graph.
 * Corresponds to a real geographic intersection or medical facility in Bangladesh.
 */
public class LocationNode {
    private final int id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final boolean isHospital;
    private final String area;
    private final String division;

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area, String division) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isHospital = isHospital;
        this.area = area;
        this.division = division != null ? division : "General";
    }

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area) {
        this(id, name, latitude, longitude, isHospital, area, "General");
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

    public String getDivision() {
        return division;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s, %s)%s", id, name, area, division, isHospital ? " 🏥[Hospital]" : "");
    }
}
