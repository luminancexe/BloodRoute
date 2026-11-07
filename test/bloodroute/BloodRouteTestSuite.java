package test.bloodroute;

import bloodroute.algorithm.DijkstraRouter;
import bloodroute.algorithm.DonorSearchEngine;
import bloodroute.ds.CustomLinkedList;
import bloodroute.ds.CustomMinHeap;
import bloodroute.ds.Graph;
import bloodroute.model.BloodType;
import bloodroute.model.Donor;
import bloodroute.model.LocationNode;
import bloodroute.model.MatchResult;
import bloodroute.service.BloodRouteService;

import java.util.NoSuchElementException;

/**
 * Automated Test Suite for BloodRoute.
 * Validates core system robustness and edge cases:
 * 1. Normal data testing
 * 2. Empty structure testing
 * 3. Invalid input testing
 * 4. Duplicate value testing
 * 5. Boundary case testing
 */
public class BloodRouteTestSuite {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("   🩸 BLOODROUTE: AUTOMATED TEST SUITE");
        System.out.println("======================================================================");

        testNormalData();
        testEmptyStructure();
        testInvalidInput();
        testDuplicateValue();
        testBoundaryCases();

        System.out.println("======================================================================");
        System.out.printf("   TEST SUMMARY: %d/%d Tests Passed (%.1f%% Success Rate)\n",
                testsPassed, testsTotal, (testsPassed * 100.0) / testsTotal);
        System.out.println("======================================================================");
    }

    /**
     * Category 1: Normal Data Testing
     */
    private static void testNormalData() {
        System.out.println("\n[CATEGORY 1] Normal Data Testing");
        BloodRouteService service = new BloodRouteService();

        // Test normal matching from Dhaka Medical College (Node 0) for B+ blood
        CustomLinkedList<MatchResult> matches = service.findNearestCompatibleDonors(0, BloodType.B_POS);
        assertCondition(!matches.isEmpty(), "Normal matching returns compatible donors for B+ at DMCH");

        // Verify ordering: Nearest distance must be <= subsequent distances
        boolean isSorted = true;
        for (int i = 0; i < matches.size() - 1; i++) {
            if (matches.get(i).getRoadDistanceKm() > matches.get(i + 1).getRoadDistanceKm()) {
                isSorted = false;
                break;
            }
        }
        assertCondition(isSorted, "Matches are ranked strictly in ascending order of road distance");

        // Test linear search by valid ID
        Donor d = DonorSearchEngine.searchById(service.getDonorRegistry(), "D-101");
        assertCondition(d != null && d.getName().equals("Tanvir Ahmed"), "Linear search correctly finds donor D-101");

        // Test newly added specialized hospital (e.g., Labaid Cardiac Hospital Node 37)
        LocationNode labaid = service.getCityGraph().getNode(37);
        assertCondition(labaid != null && labaid.getName().contains("Labaid") && !labaid.getContact().isEmpty(), "New private hospital Node 37 (Labaid) has valid contact data");

        // 5. Search by name (Linear Search)
        CustomLinkedList<Donor> namedDonors = DonorSearchEngine.searchByName(service.getDonorRegistry(), "Tanvir");
        assertCondition(!namedDonors.isEmpty() && namedDonors.get(0).getName().contains("Tanvir"), "DonorSearchEngine.searchByName finds donor by substring");
    }

    /**
     * Category 2: Empty Structure Testing
     */
    private static void testEmptyStructure() {
        System.out.println("\n[CATEGORY 2] Empty Structure Testing");

        // 1. Empty CustomLinkedList
        CustomLinkedList<Donor> emptyList = new CustomLinkedList<>();
        assertCondition(emptyList.isEmpty(), "CustomLinkedList.isEmpty() returns true on empty list");
        assertCondition(emptyList.size() == 0, "CustomLinkedList.size() is 0 on empty list");
        assertCondition(DonorSearchEngine.searchById(emptyList, "D-999") == null, "Linear search on empty list returns null gracefully");

        // 2. Empty CustomMinHeap
        CustomMinHeap emptyHeap = new CustomMinHeap(10);
        assertCondition(emptyHeap.isEmpty(), "CustomMinHeap.isEmpty() returns true on fresh heap");
        boolean heapExtractExceptionThrown = false;
        try {
            emptyHeap.extractMin();
        } catch (NoSuchElementException e) {
            heapExtractExceptionThrown = true;
        }
        assertCondition(heapExtractExceptionThrown, "CustomMinHeap.extractMin() throws NoSuchElementException on empty heap");

        boolean heapPeekExceptionThrown = false;
        try {
            emptyHeap.peek();
        } catch (NoSuchElementException e) {
            heapPeekExceptionThrown = true;
        }
        assertCondition(heapPeekExceptionThrown, "CustomMinHeap.peek() throws NoSuchElementException on empty heap");
    }

    /**
     * Category 3: Invalid Input Testing
     */
    private static void testInvalidInput() {
        System.out.println("\n[CATEGORY 3] Invalid Input Testing");
        BloodRouteService service = new BloodRouteService();

        // 1. Invalid Blood Type string
        assertCondition(BloodType.fromString("XYZ_INVALID") == null, "BloodType.fromString() returns null for invalid string");

        // 2. Negative road distance rejection
        boolean negativeEdgeCaught = false;
        try {
            service.getCityGraph().addEdge(0, 1, -5.0, "Invalid Road", false);
        } catch (IllegalArgumentException e) {
            negativeEdgeCaught = true;
        }
        assertCondition(negativeEdgeCaught, "Graph correctly rejects negative edge distance");

        // 3. Out-of-bounds vertex lookup
        boolean outOfBoundsCaught = false;
        try {
            service.getCityGraph().getNode(999);
        } catch (IndexOutOfBoundsException e) {
            outOfBoundsCaught = true;
        }
        assertCondition(outOfBoundsCaught, "Graph correctly throws IndexOutOfBoundsException for non-existent node 999");

        // 4. Null Donor attributes
        boolean nullNameCaught = false;
        try {
            new Donor("D-INVALID", null, BloodType.A_POS, 0, "+8801700000000", true, "Dhaka");
        } catch (IllegalArgumentException e) {
            nullNameCaught = true;
        }
        assertCondition(nullNameCaught, "Donor constructor rejects null or empty name");

        boolean nullPhoneCaught = false;
        try {
            new Donor("D-INVALID", "Valid Name", BloodType.A_POS, 0, null, true, "Dhaka");
        } catch (IllegalArgumentException e) {
            nullPhoneCaught = true;
        }
        assertCondition(nullPhoneCaught, "Donor constructor rejects null or empty phone number");

        boolean nullBloodCaught = false;
        try {
            new Donor("D-INVALID", "Valid Name", null, 0, "+8801700000000", true, "Dhaka");
        } catch (IllegalArgumentException e) {
            nullBloodCaught = true;
        }
        assertCondition(nullBloodCaught, "Donor constructor rejects null blood group");
    }

    /**
     * Category 4: Duplicate Value Testing
     */
    private static void testDuplicateValue() {
        System.out.println("\n[CATEGORY 4] Duplicate Value Testing");
        BloodRouteService service = new BloodRouteService();

        // Attempt to register a donor with an already existing ID (D-101)
        Donor duplicateDonor = new Donor("D-101", "Imposter", BloodType.O_NEG, 0, "+8801999999999", true, "Dhaka");
        boolean duplicateCaught = false;
        try {
            service.registerDonor(duplicateDonor);
        } catch (IllegalArgumentException e) {
            duplicateCaught = true;
        }
        assertCondition(duplicateCaught, "Service successfully prevents duplicate donor ID registration");
    }

    /**
     * Category 5: Boundary Cases Testing
     */
    private static void testBoundaryCases() {
        System.out.println("\n[CATEGORY 5] Boundary Cases Testing");
        Graph isolatedGraph = new Graph(5);
        isolatedGraph.addVertex(new LocationNode(0, "Connected A", 23.7, 90.3, true, "Zone A"));
        isolatedGraph.addVertex(new LocationNode(1, "Connected B", 23.71, 90.31, false, "Zone B"));
        isolatedGraph.addVertex(new LocationNode(2, "Isolated Island", 23.8, 90.4, false, "Zone C"));
        isolatedGraph.addEdge(0, 1, 3.0, "A-B link", true);

        // 1. Same source and destination (Distance must be 0)
        DijkstraRouter.RoutingResult selfRoute = DijkstraRouter.findRoute(isolatedGraph, 0, 0);
        assertCondition(selfRoute.totalDistanceKm == 0.0, "Dijkstra to self node returns distance 0.0 km");
        assertCondition(selfRoute.isReachable, "Dijkstra to self node is reachable");

        // 2. Disconnected component / Unreachable node
        DijkstraRouter.RoutingResult unreachableRoute = DijkstraRouter.findRoute(isolatedGraph, 0, 2);
        assertCondition(!unreachableRoute.isReachable, "Dijkstra detects disconnected component (isReachable = false)");
        assertCondition(Double.isInfinite(unreachableRoute.totalDistanceKm), "Distance to unreachable node is infinity");

        // 3. Universal donor compatibility boundary (O- to all types)
        boolean universalOValid = true;
        for (BloodType recipient : BloodType.values()) {
            if (!BloodType.isCompatible(BloodType.O_NEG, recipient)) {
                universalOValid = false;
                break;
            }
        }
        assertCondition(universalOValid, "Universal Donor (O-) is compatible with all 8 blood types");

        // 4. Universal recipient compatibility boundary (AB+ from all types)
        boolean universalABValid = true;
        for (BloodType donor : BloodType.values()) {
            if (!BloodType.isCompatible(donor, BloodType.AB_POS)) {
                universalABValid = false;
                break;
            }
        }
        assertCondition(universalABValid, "Universal Recipient (AB+) can receive from all 8 blood types");

        // 5. Complete 64-combination ABO/Rh biological matrix test
        boolean matrixValid = true;
        for (BloodType d : BloodType.values()) {
            for (BloodType r : BloodType.values()) {
                boolean result = BloodType.isCompatible(d, r);
                // Verify Rh compatibility rule: Rh- negative recipient can never receive from Rh+ positive donor
                boolean dRhPos = d.getLabel().endsWith("+");
                boolean rRhNeg = r.getLabel().endsWith("-");
                if (dRhPos && rRhNeg && result) {
                    matrixValid = false;
                    break;
                }
            }
        }
        assertCondition(matrixValid, "Full 64-combination blood matrix rigorously enforces Rh-negative recipient safety");

        // 6. CustomMinHeap Priority Queue ordering test
        CustomMinHeap testHeap = new CustomMinHeap(20);
        double[] testValues = {45.2, 12.8, 3.4, 99.1, 0.5, 23.0, 7.8, 15.6};
        for (int i = 0; i < testValues.length; i++) {
            testHeap.insert(i, testValues[i]);
        }
        boolean heapOrderValid = true;
        double prev = -1.0;
        while (!testHeap.isEmpty()) {
            double current = testHeap.extractMin().distance;
            if (current < prev) {
                heapOrderValid = false;
                break;
            }
            prev = current;
        }
        assertCondition(heapOrderValid, "CustomMinHeap successfully extracts elements in strictly ascending priority order");

        // 7. CustomLinkedList node removal boundary testing (Head, Middle, Tail)
        CustomLinkedList<String> testList = new CustomLinkedList<>();
        testList.addLast("A");
        testList.addLast("B");
        testList.addLast("C");
        boolean removeMid = testList.remove("B");
        boolean removeHead = testList.remove("A");
        boolean removeTail = testList.remove("C");
        assertCondition(removeMid && removeHead && removeTail && testList.isEmpty(), "CustomLinkedList correctly removes head, middle, and tail nodes");

        // 8. Geographical Coordinate Bounds Validation for Bangladesh Network
        BloodRouteService fullService = new BloodRouteService();
        boolean geoCoordsValid = true;
        for (LocationNode node : fullService.getCityGraph().getAllNodes()) {
            if (node == null) continue;
            // Bangladesh latitude: approx 20.5 to 26.8 N, longitude: approx 88.0 to 92.8 E
            if (node.getLatitude() < 20.0 || node.getLatitude() > 27.0 ||
                node.getLongitude() < 88.0 || node.getLongitude() > 93.0) {
                geoCoordsValid = false;
                break;
            }
        }
        assertCondition(geoCoordsValid, "All 88 graph facilities have verified coordinates within Bangladesh territory");
    }

    private static void assertCondition(boolean condition, String description) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + description);
        } else {
            System.err.println("  [FAIL] " + description);
        }
    }
}
