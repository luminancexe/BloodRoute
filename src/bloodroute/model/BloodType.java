package bloodroute.model;

/**
 * Enumeration representing human ABO and Rh blood types.
 * Encapsulates biological compatibility rules for blood transfusions.
 */
public enum BloodType {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-"),
    O_POS("O+"),
    O_NEG("O-");

    private final String label;

    BloodType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Determines whether a donor's blood type is biologically compatible with the recipient.
     * 
     * Biological Rules:
     * - O- is the Universal Donor (can donate to any blood type).
     * - AB+ is the Universal Recipient (can receive from any blood type).
     * - Rh-positive patients can receive from Rh-positive and Rh-negative compatible types.
     * - Rh-negative patients can only receive from Rh-negative compatible types.
     *
     * @param donor Blood type of the potential donor
     * @param recipient Blood type needed by the recipient
     * @return true if transfusion is safe, false otherwise
     */
    public static boolean isCompatible(BloodType donor, BloodType recipient) {
        if (donor == null || recipient == null) {
            return false;
        }

        // O- is universal donor
        if (donor == O_NEG) {
            return true;
        }

        // AB+ is universal recipient
        if (recipient == AB_POS) {
            return true;
        }

        switch (recipient) {
            case O_POS:
                return donor == O_POS || donor == O_NEG;

            case O_NEG:
                return donor == O_NEG;

            case A_POS:
                return donor == A_POS || donor == A_NEG || donor == O_POS || donor == O_NEG;

            case A_NEG:
                return donor == A_NEG || donor == O_NEG;

            case B_POS:
                return donor == B_POS || donor == B_NEG || donor == O_POS || donor == O_NEG;

            case B_NEG:
                return donor == B_NEG || donor == O_NEG;

            case AB_NEG:
                return donor == AB_NEG || donor == A_NEG || donor == B_NEG || donor == O_NEG;

            default:
                return false;
        }
    }

    /**
     * Parses a string representation (e.g., "O+", "a-", "AB_POS") into a BloodType.
     *
     * @param input String representation of blood type
     * @return BloodType enum constant, or null if invalid
     */
    public static BloodType fromString(String input) {
        if (input == null) return null;
        String clean = input.trim().toUpperCase().replace(" ", "");
        for (BloodType type : values()) {
            if (type.label.equalsIgnoreCase(clean) || type.name().equalsIgnoreCase(clean)) {
                return type;
            }
        }
        return null;
    }
}
