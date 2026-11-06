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
 * Coordinates Bangladesh nationwide road graph, donor registry, Dijkstra pathfinding, and proximity ranking.
 */
public class BloodRouteService {

    private final Graph cityGraph;
    private final CustomLinkedList<Donor> donorRegistry;

    public BloodRouteService() {
        // Initialize Bangladesh nationwide road network graph with capacity for 50 nodes
        this.cityGraph = new Graph(50);
        this.donorRegistry = new CustomLinkedList<>();

        initializeBangladeshRoadNetwork();
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

            // Transit speed: intra-district (<30km) avg 35 km/h, inter-district highway (>=30km) avg 65 km/h
            double avgSpeedKmH = dist < 30.0 ? 35.0 : 65.0;
            double etaMinutes = reachable ? (dist / avgSpeedKmH) * 60.0 : -1.0;
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
     * Configures real-world Bangladesh medical college hospitals, district healthcare hubs,
     * and national highway arterial networks covering all 8 administrative divisions.
     */
    private void initializeBangladeshRoadNetwork() {
        // --- DIVISION 1: DHAKA DIVISION ---
        cityGraph.addVertex(new LocationNode(0, "Dhaka Medical College Hospital (DMCH)", 23.7258, 90.3976, true, "Bakshibazar / Old Dhaka", "Dhaka"));
        cityGraph.addVertex(new LocationNode(1, "BSMMU / PG Hospital", 23.7388, 90.3956, true, "Shahbagh", "Dhaka"));
        cityGraph.addVertex(new LocationNode(2, "Square Hospital", 23.7530, 90.3817, true, "Panthapath", "Dhaka"));
        cityGraph.addVertex(new LocationNode(3, "Kurmitola General Hospital", 23.8223, 90.4125, true, "Airport / Cantonment", "Dhaka"));
        cityGraph.addVertex(new LocationNode(4, "Gazipur Shaheed Tajuddin Ahmad MCH", 23.9989, 90.3802, true, "Gazipur", "Dhaka"));
        cityGraph.addVertex(new LocationNode(5, "Narsingdi 100 Bed District Hospital", 23.9224, 90.7177, true, "Narsingdi", "Dhaka"));
        cityGraph.addVertex(new LocationNode(6, "Narayanganj 300 Bed Hospital", 23.6238, 90.5000, true, "Narayanganj", "Dhaka"));
        cityGraph.addVertex(new LocationNode(7, "Bhairab Junction / Meghna Bridge", 24.0514, 90.9856, false, "Bhairab / Kishoreganj", "Dhaka"));
        cityGraph.addVertex(new LocationNode(8, "Tangail 250 Bed General Hospital", 24.2513, 89.9167, true, "Tangail", "Dhaka"));
        cityGraph.addVertex(new LocationNode(9, "Faridpur Bangabandhu Sheikh Mujib MCH", 23.6070, 89.8429, true, "Faridpur", "Dhaka"));

        // --- DIVISION 2: CHATTOGRAM DIVISION ---
        cityGraph.addVertex(new LocationNode(10, "Cumilla Medical College Hospital (CuMCH)", 23.4607, 91.1809, true, "Cumilla", "Chattogram"));
        cityGraph.addVertex(new LocationNode(11, "Feni 250 Bed District Sadar Hospital", 23.0159, 91.3976, true, "Feni", "Chattogram"));
        cityGraph.addVertex(new LocationNode(12, "Chittagong Medical College Hospital (CMCH)", 22.3592, 91.8288, true, "Chattogram City", "Chattogram"));
        cityGraph.addVertex(new LocationNode(13, "Cox's Bazar 250 Bed Sadar Hospital", 21.4272, 91.9782, true, "Cox's Bazar", "Chattogram"));
        cityGraph.addVertex(new LocationNode(14, "Noakhali Abdul Malek Ukil MCH", 22.8710, 91.0973, true, "Maijdee / Noakhali", "Chattogram"));
        cityGraph.addVertex(new LocationNode(15, "Brahmanbaria 250 Bed District Hospital", 23.9608, 91.1115, true, "Brahmanbaria", "Chattogram"));

        // --- DIVISION 3: SYLHET DIVISION ---
        cityGraph.addVertex(new LocationNode(16, "Sylhet MAG Osmani MCH (SOMCH)", 24.8998, 91.8576, true, "Sylhet City", "Sylhet"));
        cityGraph.addVertex(new LocationNode(17, "Moulvibazar 250 Bed District Hospital", 24.4843, 91.7685, true, "Moulvibazar", "Sylhet"));
        cityGraph.addVertex(new LocationNode(18, "Habiganj 250 Bed District Hospital", 24.3749, 91.4155, true, "Habiganj", "Sylhet"));

        // --- DIVISION 4: MYMENSINGH DIVISION ---
        cityGraph.addVertex(new LocationNode(19, "Mymensingh Medical College Hospital (MMCH)", 24.7471, 90.4103, true, "Mymensingh City", "Mymensingh"));
        cityGraph.addVertex(new LocationNode(20, "Jamalpur 250 Bed General Hospital", 24.9375, 89.9378, true, "Jamalpur", "Mymensingh"));
        cityGraph.addVertex(new LocationNode(21, "Netrokona Modern Sadar Hospital", 24.8833, 90.7333, true, "Netrokona", "Mymensingh"));

        // --- DIVISION 5: RAJSHAHI DIVISION ---
        cityGraph.addVertex(new LocationNode(22, "Sirajganj 250 Bed Hospital (Jamuna West)", 24.4534, 89.7006, true, "Sirajganj", "Rajshahi"));
        cityGraph.addVertex(new LocationNode(23, "Bogura Shaheed Ziaur Rahman MCH (SZRMCH)", 24.8465, 89.3777, true, "Bogura", "Rajshahi"));
        cityGraph.addVertex(new LocationNode(24, "Rajshahi Medical College Hospital (RMCH)", 24.3707, 88.5833, true, "Rajshahi City", "Rajshahi"));
        cityGraph.addVertex(new LocationNode(25, "Pabna 250 Bed General Hospital", 24.0064, 89.2372, true, "Pabna", "Rajshahi"));
        cityGraph.addVertex(new LocationNode(26, "Natore Modern Sadar Hospital", 24.4102, 88.9795, true, "Natore", "Rajshahi"));

        // --- DIVISION 6: RANGPUR DIVISION ---
        cityGraph.addVertex(new LocationNode(27, "Rangpur Medical College Hospital (RpMCH)", 25.7500, 89.2500, true, "Rangpur City", "Rangpur"));
        cityGraph.addVertex(new LocationNode(28, "Dinajpur M Abdur Rahim MCH", 25.6217, 88.6355, true, "Dinajpur", "Rangpur"));
        cityGraph.addVertex(new LocationNode(29, "Saidpur 100 Bed Hospital", 25.7777, 88.8917, true, "Saidpur / Nilphamari", "Rangpur"));

        // --- DIVISION 7: KHULNA DIVISION ---
        cityGraph.addVertex(new LocationNode(30, "Kushtia 250 Bed General Hospital", 23.9013, 89.1205, true, "Kushtia", "Khulna"));
        cityGraph.addVertex(new LocationNode(31, "Jashore 250 Bed General Hospital", 23.1664, 89.2138, true, "Jashore", "Khulna"));
        cityGraph.addVertex(new LocationNode(32, "Khulna Medical College Hospital (KMCH)", 22.8157, 89.5429, true, "Khulna City", "Khulna"));
        cityGraph.addVertex(new LocationNode(33, "Satkhira Medical College Hospital", 22.7185, 89.0705, true, "Satkhira", "Khulna"));

        // --- DIVISION 8: BARISHAL DIVISION ---
        cityGraph.addVertex(new LocationNode(34, "Sher-e-Bangla Medical College Hospital (SBMCH)", 22.6896, 90.3585, true, "Barishal City", "Barishal"));
        cityGraph.addVertex(new LocationNode(35, "Patuakhali Medical College Hospital", 22.3596, 90.3298, true, "Patuakhali", "Barishal"));
        cityGraph.addVertex(new LocationNode(36, "Bhola 250 Bed General Hospital", 22.6859, 90.6481, true, "Bhola", "Barishal"));

        // =========================================================================
        // NATIONAL HIGHWAY ROAD CONNECTIONS (WEIGHTED EDGES IN KILOMETERS)
        // =========================================================================

        // --- Dhaka Metro Arteries & Outlets ---
        cityGraph.addEdge(0, 1, 2.5, "Shahbagh / Kazi Nazrul Islam Ave", true);
        cityGraph.addEdge(1, 2, 2.0, "Panthapath Arterial Rd", true);
        cityGraph.addEdge(2, 3, 9.5, "Airport Expressway", true);
        cityGraph.addEdge(3, 4, 18.0, "Dhaka-Mymensingh Highway (N3)", true);
        cityGraph.addEdge(0, 6, 16.0, "Dhaka-Narayanganj Link Rd", true);
        cityGraph.addEdge(3, 5, 46.0, "Dhaka-Sylhet Highway (N2)", true);
        cityGraph.addEdge(0, 9, 74.0, "Padma Bridge Expressway (N8)", true);

        // --- Dhaka to Chattogram Corridor (N1) ---
        cityGraph.addEdge(6, 10, 82.0, "Dhaka-Chattogram Highway (N1)", true);
        cityGraph.addEdge(10, 11, 58.0, "Cumilla-Feni Highway (N1)", true);
        cityGraph.addEdge(11, 12, 72.0, "Feni-Chattogram Highway (N1)", true);
        cityGraph.addEdge(12, 13, 142.0, "Chattogram-Cox's Bazar Highway (N1)", true);
        cityGraph.addEdge(11, 14, 42.0, "Feni-Noakhali Regional Highway", true);
        cityGraph.addEdge(10, 15, 54.0, "Cumilla-Brahmanbaria Highway (N102)", true);

        // --- Dhaka to Sylhet Corridor (N2) ---
        cityGraph.addEdge(5, 7, 38.0, "Narsingdi-Bhairab Highway (N2)", true);
        cityGraph.addEdge(7, 15, 26.0, "Bhairab-Brahmanbaria Link (Meghna Bridge)", true);
        cityGraph.addEdge(15, 18, 68.0, "Brahmanbaria-Habiganj Highway (N2)", true);
        cityGraph.addEdge(18, 17, 42.0, "Habiganj-Moulvibazar Road", true);
        cityGraph.addEdge(17, 16, 56.0, "Moulvibazar-Sylhet Highway (N208)", true);
        cityGraph.addEdge(18, 16, 74.0, "Dhaka-Sylhet Direct Highway (N2)", true);

        // --- Dhaka to Mymensingh Corridor (N3) ---
        cityGraph.addEdge(4, 19, 95.0, "Dhaka-Mymensingh Highway (N3)", true);
        cityGraph.addEdge(19, 20, 54.0, "Mymensingh-Jamalpur Highway", true);
        cityGraph.addEdge(19, 21, 38.0, "Mymensingh-Netrokona Road", true);
        cityGraph.addEdge(19, 8, 86.0, "Mymensingh-Tangail Highway", true);

        // --- North Bengal Corridor (N4/N5 via Jamuna Bridge) ---
        cityGraph.addEdge(4, 8, 68.0, "Dhaka-Tangail Expressway (N4)", true);
        cityGraph.addEdge(8, 22, 45.0, "Bangabandhu Jamuna Bridge Expressway (N405)", true);
        cityGraph.addEdge(22, 23, 72.0, "Dhaka-Rangpur Highway (N5)", true);
        cityGraph.addEdge(23, 27, 102.0, "Bogura-Rangpur Highway (N5)", true);
        cityGraph.addEdge(27, 29, 44.0, "Rangpur-Saidpur Highway (N5)", true);
        cityGraph.addEdge(29, 28, 36.0, "Saidpur-Dinajpur Highway", true);
        cityGraph.addEdge(22, 26, 62.0, "Sirajganj-Natore Highway (N6)", true);
        cityGraph.addEdge(26, 24, 46.0, "Natore-Rajshahi Highway (N6)", true);
        cityGraph.addEdge(26, 23, 66.0, "Natore-Bogura Highway", true);
        cityGraph.addEdge(24, 25, 88.0, "Rajshahi-Pabna Highway (N6)", true);
        cityGraph.addEdge(22, 25, 78.0, "Sirajganj-Pabna Road", true);

        // --- South-West Corridor (Khulna & Barishal via Padma Bridge & Lalon Shah Bridge) ---
        cityGraph.addEdge(25, 30, 42.0, "Lalon Shah Bridge / Paksey (N704)", true);
        cityGraph.addEdge(30, 31, 92.0, "Kushtia-Jashore Highway (N7)", true);
        cityGraph.addEdge(31, 32, 58.0, "Jashore-Khulna Highway (N7)", true);
        cityGraph.addEdge(32, 33, 64.0, "Khulna-Satkhira Highway", true);
        cityGraph.addEdge(9, 34, 98.0, "Padma South-Barishal Highway (N8)", true);
        cityGraph.addEdge(34, 35, 38.0, "Barishal-Patuakhali Highway (N8)", true);
        cityGraph.addEdge(34, 36, 32.0, "Barishal-Bhola Link Highway", true);
        cityGraph.addEdge(9, 31, 95.0, "Faridpur-Magura-Jashore Highway (N702)", true);
        cityGraph.addEdge(9, 30, 98.0, "Faridpur-Rajbari-Kushtia Highway", true);
        cityGraph.addEdge(32, 34, 118.0, "Khulna-Barishal Highway (N805)", true);
    }

    /**
     * Pre-populates registered blood donors distributed across all 8 administrative divisions of Bangladesh.
     */
    private void initializeDefaultDonors() {
        // Dhaka Division
        donorRegistry.addLast(new Donor("D-101", "Tanvir Ahmed", BloodType.O_NEG, 0, "+8801711223344", true, "DMCH, Old Dhaka"));
        donorRegistry.addLast(new Donor("D-102", "Sadia Rahman", BloodType.A_POS, 2, "+8801819334455", true, "Square Hospital, Panthapath"));
        donorRegistry.addLast(new Donor("D-103", "Mahmudul Hasan", BloodType.O_POS, 4, "+8801724667788", true, "Chowrasta, Gazipur"));
        donorRegistry.addLast(new Donor("D-104", "Kazi Farhan", BloodType.B_POS, 1, "+8801912445566", true, "Shahbagh, Dhaka"));
        donorRegistry.addLast(new Donor("D-105", "Nusrat Jahan", BloodType.AB_POS, 3, "+8801613556677", true, "Kurmitola / Cantonment"));

        // Chattogram Division
        donorRegistry.addLast(new Donor("D-106", "Saifullah Mansur", BloodType.O_NEG, 12, "+8801835778899", true, "Panchlaish, Chattogram"));
        donorRegistry.addLast(new Donor("D-107", "Mehnaz Parveen", BloodType.B_POS, 10, "+8801736889900", true, "Kandirpar, Cumilla"));
        donorRegistry.addLast(new Donor("D-108", "Rashedul Karim", BloodType.A_POS, 11, "+8801937990011", true, "Trunk Road, Feni"));
        donorRegistry.addLast(new Donor("D-109", "Tarequl Islam", BloodType.O_POS, 13, "+8801638001122", true, "Kolatoli, Cox's Bazar"));
        donorRegistry.addLast(new Donor("D-110", "Jannatul Ferdous", BloodType.AB_NEG, 14, "+8801839112233", true, "Maijdee Court, Noakhali"));
        donorRegistry.addLast(new Donor("D-111", "Ashfaqur Rahman", BloodType.B_NEG, 15, "+8801530223344", true, "Medda, Brahmanbaria"));

        // Sylhet Division
        donorRegistry.addLast(new Donor("D-112", "Shahriar Kabir", BloodType.O_NEG, 16, "+8801741334455", true, "Amberkhana, Sylhet"));
        donorRegistry.addLast(new Donor("D-113", "Nazia Chowdhury", BloodType.A_POS, 17, "+8801842445566", true, "Sreemangal Rd, Moulvibazar"));
        donorRegistry.addLast(new Donor("D-114", "Mahir Faisal", BloodType.B_POS, 18, "+8801943556677", true, "New Bus Stand, Habiganj"));

        // Mymensingh Division
        donorRegistry.addLast(new Donor("D-115", "Kamrul Ahsan", BloodType.O_POS, 19, "+8801644667788", true, "Charpara, Mymensingh"));
        donorRegistry.addLast(new Donor("D-116", "Shanjida Akter", BloodType.A_NEG, 20, "+8801745778899", true, "Station Rd, Jamalpur"));
        donorRegistry.addLast(new Donor("D-117", "Moniruzzaman", BloodType.B_POS, 21, "+8801846889900", true, "Choto Bazar, Netrokona"));

        // Rajshahi Division
        donorRegistry.addLast(new Donor("D-118", "Asadul Islam", BloodType.O_NEG, 24, "+8801947990011", true, "Laxmipur, Rajshahi"));
        donorRegistry.addLast(new Donor("D-119", "Farzana Haque", BloodType.A_POS, 23, "+8801548001122", true, "Thanthania, Bogura"));
        donorRegistry.addLast(new Donor("D-120", "Tariqul Anam", BloodType.B_POS, 22, "+8801749112233", true, "Mujib Sarak, Sirajganj"));
        donorRegistry.addLast(new Donor("D-121", "Shamim Reza", BloodType.O_POS, 25, "+8801850223344", true, "Abdul Hamid Rd, Pabna"));
        donorRegistry.addLast(new Donor("D-122", "Mustafizur Rahman", BloodType.AB_POS, 26, "+8801951334455", true, "Kanaikhali, Natore"));

        // Rangpur Division
        donorRegistry.addLast(new Donor("D-123", "Rafiqul Islam", BloodType.O_POS, 27, "+8801652445566", true, "Dhap, Rangpur"));
        donorRegistry.addLast(new Donor("D-124", "Sabrina Yasmin", BloodType.B_POS, 28, "+8801753556677", true, "Goneshtola, Dinajpur"));
        donorRegistry.addLast(new Donor("D-125", "Atikur Rahman", BloodType.A_POS, 29, "+8801854667788", true, "Railway Colony, Saidpur"));

        // Khulna Division
        donorRegistry.addLast(new Donor("D-126", "Anisur Rahman", BloodType.O_NEG, 32, "+8801955778899", true, "Boyra, Khulna"));
        donorRegistry.addLast(new Donor("D-127", "Bilkis Ara", BloodType.A_POS, 31, "+8801556889900", true, "Chowrasta, Jashore"));
        donorRegistry.addLast(new Donor("D-128", "Zahid Hasan", BloodType.B_POS, 30, "+8801757990011", true, "NS Road, Kushtia"));
        donorRegistry.addLast(new Donor("D-129", "Faruk Hossain", BloodType.O_POS, 33, "+8801858001122", true, "Palashpole, Satkhira"));

        // Barishal Division
        donorRegistry.addLast(new Donor("D-130", "Moinul Islam", BloodType.O_NEG, 34, "+8801959112233", true, "Band Road, Barishal"));
        donorRegistry.addLast(new Donor("D-131", "Sharmin Sultana", BloodType.B_POS, 35, "+8801660223344", true, "Sadar Rd, Patuakhali"));
        donorRegistry.addLast(new Donor("D-132", "Jahangir Alam", BloodType.A_POS, 36, "+8801761334455", true, "Ukheel Para, Bhola"));
        donorRegistry.addLast(new Donor("D-133", "Mehedi Hassan", BloodType.O_NEG, 1, "+8801862445566", false, "Shahbagh, Dhaka (Busy)"));
        donorRegistry.addLast(new Donor("D-134", "Rubina Yasmin", BloodType.A_POS, 9, "+8801963556677", true, "Goalchamot, Faridpur"));
    }
}
