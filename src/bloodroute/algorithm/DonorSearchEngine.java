package bloodroute.algorithm;

import bloodroute.ds.CustomLinkedList;
import bloodroute.model.BloodType;
import bloodroute.model.Donor;

/**
 * [ALGORITHM 2: Linear Search & Compatibility Filter Engine]
 * Performs sequential search and biological filtering across registered donor records.
 * Time Complexity: O(N) where N is the number of registered donors.
 * Space Complexity: O(K) where K is the number of compatible matches.
 */
public class DonorSearchEngine {

    /**
     * Filters all registered donors who are biologically compatible with the recipient
     * and currently marked as available for emergency donation.
     *
     * @param allDonors List of registered donors
     * @param requestedType Blood type required by the emergency patient
     * @return CustomLinkedList of compatible, available donors
     */
    public static CustomLinkedList<Donor> filterCompatibleDonors(CustomLinkedList<Donor> allDonors, BloodType requestedType) {
        CustomLinkedList<Donor> compatibleList = new CustomLinkedList<>();
        if (allDonors == null || requestedType == null) {
            return compatibleList;
        }

        for (Donor donor : allDonors) {
            if (donor.isAvailable() && BloodType.isCompatible(donor.getBloodType(), requestedType)) {
                compatibleList.addLast(donor);
            }
        }
        return compatibleList;
    }

    /**
     * Searches for a donor by unique ID using Linear Search.
     * Time Complexity: O(N)
     *
     * @param allDonors List of donors
     * @param donorId Target ID
     * @return Matching Donor object, or null if not found
     */
    public static Donor searchById(CustomLinkedList<Donor> allDonors, String donorId) {
        if (allDonors == null || donorId == null) {
            return null;
        }

        String target = donorId.trim();
        for (Donor donor : allDonors) {
            if (donor.getDonorId().equalsIgnoreCase(target)) {
                return donor;
            }
        }
        return null;
    }

    /**
     * Searches for donors whose name contains the query string (case-insensitive).
     * Time Complexity: O(N)
     */
    public static CustomLinkedList<Donor> searchByName(CustomLinkedList<Donor> allDonors, String nameQuery) {
        CustomLinkedList<Donor> matches = new CustomLinkedList<>();
        if (allDonors == null || nameQuery == null) {
            return matches;
        }

        String query = nameQuery.trim().toLowerCase();
        for (Donor donor : allDonors) {
            if (donor.getName().toLowerCase().contains(query)) {
                matches.addLast(donor);
            }
        }
        return matches;
    }
}
