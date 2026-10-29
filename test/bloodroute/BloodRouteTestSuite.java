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
        boolean heapExceptionThrown = false;
        try {
            emptyHeap.extractMin();
        } catch (NoSuchElementException e) {
            heapExceptionThrown = true;
        }
        assertCondition(heapExceptionThrown, "CustomMinHeap.extractMin() throws NoSuchElementException on empty heap");
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
