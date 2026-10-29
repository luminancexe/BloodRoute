package bloodroute.service;

import bloodroute.algorithm.DijkstraRouter;
import bloodroute.algorithm.DonorSearchEngine;
import bloodroute.ds.CustomLinkedList;
import bloodroute.ds.CustomMinHeap;
import bloodroute.ds.Graph;
import bloodroute.model.*;

import java.util.Arrays;

/**
 * Core business logic service for BloodRoute.
 * Coordinates Dhaka road graph, donor registry, Dijkstra pathfinding, and proximity ranking.
 */
public class BloodRouteService {

    private final Graph cityGraph;
    private final CustomLinkedList<Donor> donorRegistry;

    public BloodRouteService() {
        // Initialize Dhaka road network graph with capacity for 25 nodes
        this.cityGraph = new Graph(25);
        this.donorRegistry = new CustomLinkedList<>();

        initializeDhakaRoadNetwork();
        initializeDefaultDonors();
    }

    /**
     * Finds and ranks all compatible donors from nearest to farthest
     * starting from the emergency requester's location node.
     *
     * @param requesterNodeId Starting hospital or intersection node
     * @param neededBloodType Patient's required blood group
     * @param simulationTrace Optional list to record Dijkstra exploration events
     * @return Ranked CustomLinkedList of MatchResults
     */
    public CustomLinkedList<MatchResult> findNearestCompatibleDonors(int requesterNodeId, BloodType neededBloodType,
                                                                    CustomLinkedList<DijkstraRouter.SimulationStep> simulationTrace) {
        if (requesterNodeId < 0 || requesterNodeId >= cityGraph.getNumVertices()) {
            throw new IndexOutOfBoundsException("Invalid requester node ID: " + requesterNodeId);
        }
        if (cityGraph.getNode(requesterNodeId) == null) {
            throw new IllegalArgumentException("Node ID " + requesterNodeId + " does not exist in the graph.");
        }

        // Step 1: Filter compatible, available donors using Algorithm 2 (Linear Search)
        CustomLinkedList<Donor> compatibleDonors = DonorSearchEngine.filterCompatibleDonors(donorRegistry, neededBloodType);

        // Step 2: Compute shortest road paths to all nodes using Algorithm 1 (Dijkstra) with simulation trace
        int v = cityGraph.getNumVertices();
        double[] distances = new double[v];
        int[] parent = new int[v];
        DijkstraRouter.computeShortestPaths(cityGraph, requesterNodeId, distances, parent, simulationTrace);

        // Step 3: Package matches with exact distance, ETA, and reconstructed route path
        CustomLinkedList<MatchResult> rawResults = new CustomLinkedList<>();
        for (Donor donor : compatibleDonors) {
            int donorNode = donor.getLocationNodeId();
            double dist = distances[donorNode];
            boolean reachable = !Double.isInfinite(dist);

            CustomLinkedList<Integer> path = new CustomLinkedList<>();
            if (reachable) {
                int curr = donorNode;
                while (curr != -1) {
                    path.addFirst(curr);
                    if (curr == requesterNodeId) break;
                    curr = parent[curr];
                }
            }

            double etaMinutes = reachable ? (dist / 25.0) * 60.0 : -1.0;
            rawResults.addLast(new MatchResult(donor, dist, etaMinutes, path, reachable));
        }

        // Step 4: Sort matches by road distance (Nearest to Farthest) using Insertion Sort
        return sortMatchesByProximity(rawResults);
    }

    public CustomLinkedList<MatchResult> findNearestCompatibleDonors(int requesterNodeId, BloodType neededBloodType) {
        return findNearestCompatibleDonors(requesterNodeId, neededBloodType, null);
    }

    /**
     * Registers a new donor into the system with validation.
     */
    public boolean registerDonor(Donor donor) {
        if (donor == null) {
            throw new IllegalArgumentException("Donor cannot be null.");
        }
        // Verify unique donor ID
        if (DonorSearchEngine.searchById(donorRegistry, donor.getDonorId()) != null) {
            throw new IllegalArgumentException("Donor with ID " + donor.getDonorId() + " already exists!");
        }
        // Verify valid location node
        if (donor.getLocationNodeId() < 0 || donor.getLocationNodeId() >= cityGraph.getNumVertices() ||
            cityGraph.getNode(donor.getLocationNodeId()) == null) {
            throw new IllegalArgumentException("Invalid location node ID: " + donor.getLocationNodeId());
        }

        donorRegistry.addLast(donor);
        return true;
    }

    public Graph getCityGraph() {
        return cityGraph;
    }

    public CustomLinkedList<Donor> getDonorRegistry() {
        return donorRegistry;
    }

    /**
     * Sorts the matched donors in ascending order of road distance.
     * Implements in-place / array conversion Insertion Sort.
     */
    private CustomLinkedList<MatchResult> sortMatchesByProximity(CustomLinkedList<MatchResult> list) {
        int n = list.size();
        if (n <= 1) return list;

        MatchResult[] array = new MatchResult[n];
        int idx = 0;
        for (MatchResult m : list) {
            array[idx++] = m;
        }

        // Insertion sort by road distance
        for (int i = 1; i < n; i++) {
            MatchResult key = array[i];
            int j = i - 1;
            while (j >= 0 && array[j].getRoadDistanceKm() > key.getRoadDistanceKm()) {
                array[j + 1] = array[j];
                j--;
            }
            array[j + 1] = key;
        }

        CustomLinkedList<MatchResult> sortedList = new CustomLinkedList<>();
        for (MatchResult m : array) {
            sortedList.addLast(m);
        }
        return sortedList;
    }

    /**
     * Configures real-world Dhaka landmarks, medical hospitals, and arterial roads.
     */
    private void initializeDhakaRoadNetwork() {
        // Adding Vertices (Real Dhaka Coordinates)
        cityGraph.addVertex(new LocationNode(0, "Dhaka Medical College Hospital (DMCH)", 23.7258, 90.3976, true, "Bakshibazar"));
        cityGraph.addVertex(new LocationNode(1, "BSMMU / PG Hospital", 23.7388, 90.3956, true, "Shahbagh"));
        cityGraph.addVertex(new LocationNode(2, "BIRDEM General Hospital", 23.7397, 90.3962, true, "Shahbagh"));
        cityGraph.addVertex(new LocationNode(3, "Science Lab Intersection", 23.7389, 90.3838, false, "Dhanmondi"));
        cityGraph.addVertex(new LocationNode(4, "Central Hospital", 23.7428, 90.3824, true, "Green Road"));
        cityGraph.addVertex(new LocationNode(5, "Square Hospital", 23.7530, 90.3817, true, "Panthapath"));
        cityGraph.addVertex(new LocationNode(6, "Kawran Bazar Junction", 23.7511, 90.3934, false, "Tejgaon"));
        cityGraph.addVertex(new LocationNode(7, "Farmgate Intersection", 23.7570, 90.3895, false, "Farmgate"));
        cityGraph.addVertex(new LocationNode(8, "Shaheed Suhrawardy Medical College Hospital", 23.7699, 90.3705, true, "Sher-e-Bangla Nagar"));
        cityGraph.addVertex(new LocationNode(9, "National Institute of Neurosciences (NINS)", 23.7745, 90.3735, true, "Agargaon"));
        cityGraph.addVertex(new LocationNode(10, "Asad Gate", 23.7594, 90.3664, false, "Mohammadpur"));
        cityGraph.addVertex(new LocationNode(11, "Mohakhali Intersection", 23.7776, 90.4034, false, "Mohakhali"));
        cityGraph.addVertex(new LocationNode(12, "ICDDR,B Hospital", 23.7788, 90.4020, true, "Mohakhali"));
        cityGraph.addVertex(new LocationNode(13, "Gulshan-1 Circle", 23.7782, 90.4170, false, "Gulshan"));
        cityGraph.addVertex(new LocationNode(14, "Banani Road 11", 23.7937, 90.4043, false, "Banani"));
        cityGraph.addVertex(new LocationNode(15, "Mirpur 10 Circle", 23.8069, 90.3687, false, "Mirpur"));
        cityGraph.addVertex(new LocationNode(16, "Kurmitola General Hospital", 23.8223, 90.4125, true, "Cantonment"));
        cityGraph.addVertex(new LocationNode(17, "Uttara Sector 3 / Rajlakshmi", 23.8683, 90.3986, false, "Uttara"));

        // Adding Weighted Edges (Realistic Road Distances in Kilometers)
        cityGraph.addEdge(0, 1, 1.8, "Secretariat Rd / Kazi Nazrul Islam Ave", true);
        cityGraph.addEdge(1, 2, 0.3, "Shahbagh Crossing", true);
        cityGraph.addEdge(1, 3, 1.6, "Elephant Road", true);
        cityGraph.addEdge(3, 4, 0.6, "Green Road", true);
        cityGraph.addEdge(4, 5, 1.4, "Green Road / Panthapath", true);
        cityGraph.addEdge(3, 10, 2.4, "Mirpur Road", true);
        cityGraph.addEdge(1, 6, 1.5, "Kazi Nazrul Islam Ave", true);
        cityGraph.addEdge(6, 7, 0.9, "Kazi Nazrul Islam Ave", true);
        cityGraph.addEdge(6, 5, 1.3, "Panthapath", true);
        cityGraph.addEdge(7, 8, 2.2, "Manik Mia Ave / Mirpur Rd", true);
        cityGraph.addEdge(7, 11, 2.7, "Airport Road / Mohakhali Flyover", true);
        cityGraph.addEdge(10, 8, 1.5, "Mirpur Road", true);
        cityGraph.addEdge(8, 9, 0.8, "Syed Mahbub Morshed Ave", true);
        cityGraph.addEdge(9, 15, 4.1, "Begum Rokeya Sarani", true);
        cityGraph.addEdge(11, 12, 0.4, "Mohakhali TB Gate Road", true);
        cityGraph.addEdge(11, 13, 1.6, "Gulshan-Mohakhali Link Road", true);
        cityGraph.addEdge(11, 14, 2.1, "Airport Road / Kemal Ataturk", true);
        cityGraph.addEdge(13, 14, 2.2, "Gulshan Avenue", true);
        cityGraph.addEdge(14, 16, 3.5, "Dhaka-Mymensingh Highway", true);
        cityGraph.addEdge(16, 17, 5.4, "Dhaka-Mymensingh Highway", true);
        cityGraph.addEdge(15, 17, 8.2, "Mirpur-Uttara Link Road", true);
    }

    /**
     * Pre-populates realistic registered blood donors located across Dhaka.
     */
    private void initializeDefaultDonors() {
        donorRegistry.addLast(new Donor("D-101", "Tanvir Ahmed", BloodType.O_NEG, 4, "+8801711223344", true, "Green Road, Dhanmondi"));
        donorRegistry.addLast(new Donor("D-102", "Sadia Rahman", BloodType.A_POS, 5, "+8801819334455", true, "Panthapath, Dhaka"));
        donorRegistry.addLast(new Donor("D-103", "Kazi Farhan", BloodType.B_POS, 10, "+8801912445566", true, "Asad Gate, Mohammadpur"));
        donorRegistry.addLast(new Donor("D-104", "Nusrat Jahan", BloodType.AB_POS, 3, "+8801613556677", true, "Science Lab, Dhanmondi"));
        donorRegistry.addLast(new Donor("D-105", "Mahmudul Hasan", BloodType.O_POS, 7, "+8801724667788", true, "Farmgate, Tejgaon"));
        donorRegistry.addLast(new Donor("D-106", "Ayesha Siddiqua", BloodType.O_NEG, 13, "+8801835778899", true, "Gulshan-1, Dhaka"));
        donorRegistry.addLast(new Donor("D-107", "Rifat Hossain", BloodType.A_NEG, 15, "+8801946889900", true, "Mirpur 10, Dhaka"));
        donorRegistry.addLast(new Donor("D-108", "Shakil Chowdhury", BloodType.B_NEG, 8, "+8801557990011", true, "Sher-e-Bangla Nagar"));
        donorRegistry.addLast(new Donor("D-109", "Farhana Akter", BloodType.AB_NEG, 14, "+8801768001122", true, "Road 11, Banani"));
        donorRegistry.addLast(new Donor("D-110", "Imtiaz Karim", BloodType.O_POS, 0, "+8801879112233", true, "Old Dhaka / Bakshibazar"));
        donorRegistry.addLast(new Donor("D-111", "Tasnim Sultana", BloodType.A_POS, 9, "+8801980223344", true, "Agargaon, Dhaka"));
        donorRegistry.addLast(new Donor("D-112", "Zubair Al-Mamun", BloodType.B_POS, 17, "+8801691334455", true, "Sector 3, Uttara"));
        donorRegistry.addLast(new Donor("D-113", "Mehedi Hassan", BloodType.O_NEG, 6, "+8801712445566", false, "Kawran Bazar (Unavailable)"));
        donorRegistry.addLast(new Donor("D-114", "Rubina Yasmin", BloodType.A_POS, 1, "+8801823556677", true, "Shahbagh, Dhaka"));
    }
}
