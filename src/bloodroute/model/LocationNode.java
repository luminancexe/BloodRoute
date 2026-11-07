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
    private final String contact;
    private final String address;
    private final String email;

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area, String division, String contact, String address, String email) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isHospital = isHospital;
        this.area = area;
        this.division = division != null ? division : "General";
        this.contact = contact != null ? contact : "";
        this.address = address != null ? address : "";
        this.email = email != null ? email : "";
    }

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area, String division) {
        this(id, name, latitude, longitude, isHospital, area, division, "", "", "");
    }

    public LocationNode(int id, String name, double latitude, double longitude, boolean isHospital, String area) {
        this(id, name, latitude, longitude, isHospital, area, "General", "", "", "");
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

    public String getContact() {
        return contact;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        String contactInfo = !contact.isEmpty() ? " | 📞 " + contact : "";
        return String.format("[%d] %s (%s, %s)%s%s", id, name, area, division, isHospital ? " 🏥[Hospital]" : "", contactInfo);
    }
}
