package bloodroute.model;

/**
 * Represents a registered blood donor.
 * Stores personal, clinical, location, and contact information.
 */
public class Donor {
    private final String donorId;
    private final String name;
    private final BloodType bloodType;
    private final int locationNodeId;
    private final String phoneNumber;
    private boolean isAvailable;
    private final String address;

    public Donor(String donorId, String name, BloodType bloodType, int locationNodeId, String phoneNumber, boolean isAvailable, String address) {
        if (donorId == null || donorId.trim().isEmpty()) {
            throw new IllegalArgumentException("Donor ID cannot be null or empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Donor name cannot be null or empty.");
        }
        if (bloodType == null) {
            throw new IllegalArgumentException("Blood type cannot be null.");
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty.");
        }

        this.donorId = donorId.trim();
        this.name = name.trim();
        this.bloodType = bloodType;
        this.locationNodeId = locationNodeId;
        this.phoneNumber = phoneNumber.trim();
        this.isAvailable = isAvailable;
        this.address = address != null ? address.trim() : "";
    }

    public String getDonorId() {
        return donorId;
    }

    public String getName() {
        return name;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public int getLocationNodeId() {
        return locationNodeId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return String.format("Donor[%s | %s | %s | Node %d | %s | %s]",
                donorId, name, bloodType.getLabel(), locationNodeId, phoneNumber,
                isAvailable ? "Available" : "Unavailable");
    }
}
