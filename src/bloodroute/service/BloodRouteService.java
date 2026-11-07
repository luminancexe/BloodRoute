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
        // Initialize Bangladesh nationwide road network graph with capacity for 130 nodes
        this.cityGraph = new Graph(130);
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
        // =========================================================================
        // DIVISION 1: DHAKA DIVISION - TERTIARY & SPECIALIZED HOSPITALS
        // =========================================================================
        cityGraph.addVertex(new LocationNode(0, "Dhaka Medical College Hospital (DMCH)", 23.7258, 90.3976, true, "Bakshibazar / Old Dhaka", "Dhaka", "+880255165656", "Secretariat Rd, Bakshibazar, Dhaka 1000", "principal@dmc.gov.bd"));
        cityGraph.addVertex(new LocationNode(1, "BSMMU / PG Hospital", 23.7388, 90.3956, true, "Shahbagh", "Dhaka", "02-9661551", "Shahbagh, Dhaka 1000", "info@bsmmu.edu.bd"));
        cityGraph.addVertex(new LocationNode(2, "Square Hospital", 23.7530, 90.3817, true, "Panthapath", "Dhaka", "10616", "18/F Bir Uttam Qazi Nuruzzaman Sarak, West Panthapath, Dhaka 1205", "nuralam@squarehospital.com"));
        cityGraph.addVertex(new LocationNode(3, "Kurmitola General Hospital", 23.8223, 90.4125, true, "Airport / Cantonment", "Dhaka", "+88028711222", "Dhaka-Mymensingh Highway, Cantonment, Dhaka", "kgh@hos.gov.bd"));
        cityGraph.addVertex(new LocationNode(4, "Gazipur Shaheed Tajuddin Ahmad MCH", 23.9989, 90.3802, true, "Gazipur", "Dhaka", "02-9261234", "Hospital Road, Gazipur 1700", "stamc@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(5, "Narsingdi 100 Bed District Hospital", 23.9224, 90.7177, true, "Narsingdi", "Dhaka", "02-9462222", "Velanagar, Narsingdi", "narsingdihosp@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(6, "Narayanganj 300 Bed Hospital", 23.6238, 90.5000, true, "Narayanganj", "Dhaka", "02-7634567", "Khanpur, Narayanganj", "n300h@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(7, "Bhairab Junction / Meghna Bridge", 24.0514, 90.9856, false, "Bhairab / Kishoreganj", "Dhaka", "N/A", "Bhairab Bazar, Kishoreganj", ""));
        cityGraph.addVertex(new LocationNode(8, "Tangail 250 Bed General Hospital", 24.2513, 89.9167, true, "Tangail", "Dhaka", "0921-63456", "Sabalia, Tangail 1900", "tangailgh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(9, "Faridpur Bangabandhu Sheikh Mujib MCH", 23.6070, 89.8429, true, "Faridpur", "Dhaka", "0631-62456", "West Khabashpur, Faridpur", "bsmmch.faridpur@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 2: CHATTOGRAM DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(10, "Cumilla Medical College Hospital (CuMCH)", 23.4607, 91.1809, true, "Cumilla", "Chattogram", "081-65432", "Kuchaitoli, Cumilla", "cumch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(11, "Feni 250 Bed District Sadar Hospital", 23.0159, 91.3976, true, "Feni", "Chattogram", "0331-74123", "Hospital Road, Feni", "fenisadar@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(12, "Chittagong Medical College Hospital (CMCH)", 22.3592, 91.8288, true, "Chattogram City", "Chattogram", "031-619400", "57 K.B. Fazlul Kader Road, Panchlaish, Chattogram", "cmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(13, "Cox's Bazar 250 Bed Sadar Hospital", 21.4272, 91.9782, true, "Cox's Bazar", "Chattogram", "0341-63456", "Hospital Road, Cox's Bazar", "coxsadar@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(14, "Noakhali Abdul Malek Ukil MCH", 22.8710, 91.0973, true, "Maijdee / Noakhali", "Chattogram", "0321-61234", "Maijdee Court, Noakhali", "amumch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(15, "Brahmanbaria 250 Bed District Hospital", 23.9608, 91.1115, true, "Brahmanbaria", "Chattogram", "0851-58234", "Hospital Road, Brahmanbaria", "bbariagh@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 3: SYLHET DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(16, "Sylhet MAG Osmani MCH (SOMCH)", 24.8998, 91.8576, true, "Sylhet City", "Sylhet", "0821-713456", "Kajolshah, Sylhet 3100", "somch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(17, "Moulvibazar 250 Bed District Hospital", 24.4843, 91.7685, true, "Moulvibazar", "Sylhet", "0861-52345", "Sreemangal Road, Moulvibazar", "mbazardh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(18, "Habiganj 250 Bed District Hospital", 24.3749, 91.4155, true, "Habiganj", "Sylhet", "0831-62345", "Main Road, Habiganj", "habiganjdh@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 4: MYMENSINGH DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(19, "Mymensingh Medical College Hospital (MMCH)", 24.7471, 90.4103, true, "Mymensingh City", "Mymensingh", "091-66033", "Charpara, Mymensingh 2200", "mmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(20, "Jamalpur 250 Bed General Hospital", 24.9375, 89.9378, true, "Jamalpur", "Mymensingh", "0981-63456", "Station Road, Jamalpur", "jamalpurgh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(21, "Netrokona Modern Sadar Hospital", 24.8833, 90.7333, true, "Netrokona", "Mymensingh", "0951-61234", "Hospital Road, Netrokona", "netrokonadh@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 5: RAJSHAHI DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(22, "Sirajganj 250 Bed Hospital (Jamuna West)", 24.4534, 89.7006, true, "Sirajganj", "Rajshahi", "0751-62345", "Hospital Road, Sirajganj", "sirajganjgh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(23, "Bogura Shaheed Ziaur Rahman MCH (SZRMCH)", 24.8465, 89.3777, true, "Bogura", "Rajshahi", "051-65432", "Silimpur, Bogura 5800", "szrmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(24, "Rajshahi Medical College Hospital (RMCH)", 24.3707, 88.5833, true, "Rajshahi City", "Rajshahi", "0721-772150", "Laxmipur, Rajshahi 6000", "rmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(25, "Pabna 250 Bed General Hospital", 24.0064, 89.2372, true, "Pabna", "Rajshahi", "0731-65432", "Hospital Road, Shalgaria, Pabna", "pabnagh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(26, "Natore Modern Sadar Hospital", 24.4102, 88.9795, true, "Natore", "Rajshahi", "0771-62345", "Kanaikhali, Natore", "natoredh@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 6: RANGPUR DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(27, "Rangpur Medical College Hospital (RpMCH)", 25.7500, 89.2500, true, "Rangpur City", "Rangpur", "0521-62150", "Dhap, Jail Road, Rangpur", "rpmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(28, "Dinajpur M Abdur Rahim MCH", 25.6217, 88.6355, true, "Dinajpur", "Rangpur", "0531-65432", "Munshipara, Dinajpur", "dinajpurmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(29, "Saidpur 100 Bed Hospital", 25.7777, 88.8917, true, "Saidpur / Nilphamari", "Rangpur", "05526-72345", "Hospital Road, Saidpur", "saidpurhosp@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 7: KHULNA DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(30, "Kushtia 250 Bed General Hospital", 23.9013, 89.1205, true, "Kushtia", "Khulna", "071-62345", "Courtpara, Kushtia", "kushtiagh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(31, "Jashore 250 Bed General Hospital", 23.1664, 89.2138, true, "Jashore", "Khulna", "0421-68234", "Jail Road, Ghope, Jashore", "jashoregh@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(32, "Khulna Medical College Hospital (KMCH)", 22.8157, 89.5429, true, "Khulna City", "Khulna", "041-760350", "Boyra Main Road, Khulna", "kmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(33, "Satkhira Medical College Hospital", 22.7185, 89.0705, true, "Satkhira", "Khulna", "0471-63456", "Bakchara, Satkhira", "satkhiramch@dghs.gov.bd"));

        // =========================================================================
        // DIVISION 8: BARISHAL DIVISION
        // =========================================================================
        cityGraph.addVertex(new LocationNode(34, "Sher-e-Bangla Medical College Hospital (SBMCH)", 22.6896, 90.3585, true, "Barishal City", "Barishal", "0431-217354", "Band Road, Chandmari, Barishal 8200", "sbmch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(35, "Patuakhali Medical College Hospital", 22.3596, 90.3298, true, "Patuakhali", "Barishal", "0441-62345", "College Road, Patuakhali", "patuakhalimch@dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(36, "Bhola 250 Bed General Hospital", 22.6859, 90.6481, true, "Bhola", "Barishal", "0491-61234", "Muslim Para Road, Bhola Sadar", "bholagh@dghs.gov.bd"));

        // =========================================================================
        // NEW PRIVATE & SPECIALIZED HOSPITALS (IPDI & ASTHA LIFE LISTS)
        // =========================================================================
        cityGraph.addVertex(new LocationNode(37, "Labaid Cardiac Hospital", 23.7420, 90.3828, true, "Dhanmondi", "Dhaka", "10606, 01713-333337", "House-06, Road-04, Dhanmondi, Dhaka 1205", "corporateservices@labaidgroup.com"));
        cityGraph.addVertex(new LocationNode(38, "Ibrahim Cardiac Hospital & Research Institute", 23.7395, 90.3962, true, "Shahbagh", "Dhaka", "01714-006706, 02-41060451", "122 Kazi Nazrul Islam Avenue, Shahbagh, Dhaka 1000", "info@ibrahimcardiac.org.bd"));
        cityGraph.addVertex(new LocationNode(39, "Bangladesh Specialized Hospital (BSHL)", 23.7718, 90.3662, true, "Shyamoli", "Dhaka", "+8809666700100, 01313-777888", "21 Shyamoli, Mirpur Road, Dhaka 1207", "samsul@bshl.com.bd"));
        cityGraph.addVertex(new LocationNode(40, "United Hospital", 23.7978, 90.4158, true, "Gulshan", "Dhaka", "+88028836444, 10666", "Plot 15, Road 71, Gulshan-2, Dhaka 1212", "info@uhlbd.com"));
        cityGraph.addVertex(new LocationNode(41, "Asgar Ali Hospital", 23.7086, 90.4285, true, "Gandaria / Dhupkhola", "Dhaka", "10602, 01787-683333", "111/1/A Distillery Road, Gandaria, Dhaka 1204", "marufaah@asgaralihospital.com"));
        cityGraph.addVertex(new LocationNode(42, "Ibn Sina Specialized Hospital Dhanmondi", 23.7505, 90.3725, true, "Dhanmondi", "Dhaka", "10615, 01782-453046", "House#68, Road#15/A, Dhanmondi, Dhaka 1209", "corporate.ish2019@gmail.com"));
        cityGraph.addVertex(new LocationNode(43, "Evercare Hospital Dhaka", 23.8099, 90.4312, true, "Bashundhara R/A", "Dhaka", "10678, 02-8401661", "Plot 81, Block E, Bashundhara R/A, Vatara, Dhaka 1229", "shafayetul.islam@evercarebd.com"));
        cityGraph.addVertex(new LocationNode(44, "National Heart Foundation Hospital", 23.8042, 90.3601, true, "Mirpur", "Dhaka", "09666-750075", "Plot 4, Road 2, Section 2, Mirpur, Dhaka 1216", "info@nhf.org.bd"));
        cityGraph.addVertex(new LocationNode(45, "BRB Hospitals Limited", 23.7538, 90.3842, true, "Panthapath", "Dhaka", "09610-010647, 01704-117499", "77/A East Razabazar, West Panthapath, Dhaka 1215", "kaziahasanh@gmail.com"));
        cityGraph.addVertex(new LocationNode(46, "Green Life Hospital Ltd", 23.7472, 90.3855, true, "Green Road", "Dhaka", "02-9612345, 01711-540246", "31 Bir Uttam Shafiullah Sarak, Green Rd, Dhaka 1205", "info@greenlifehospital.com.bd"));
        cityGraph.addVertex(new LocationNode(47, "Central Hospital Ltd", 23.7441, 90.3862, true, "Green Road", "Dhaka", "02-41060806, 01731-213511", "House No. 02, Road No. 05, Green Road, Dhanmondi, Dhaka 1205", "mamunchl81@gmail.com"));
        cityGraph.addVertex(new LocationNode(48, "Dhaka Shishu (Children's) Hospital", 23.7712, 90.3745, true, "Sher-e-Bangla Nagar", "Dhaka", "02-55059051", "Sher-e-Bangla Nagar, Dhaka 1207", "dsh@bangla.net"));
        cityGraph.addVertex(new LocationNode(49, "Holy Family Red Crescent Medical College Hospital", 23.7431, 90.4028, true, "Eskaton", "Dhaka", "+880 1754-294042", "1, Eskaton Garden Road, Dhaka 1000", "hfrcmchdhaka@yahoo.com"));
        cityGraph.addVertex(new LocationNode(50, "Impulse Hospital", 23.7681, 90.4068, true, "Tejgaon", "Dhaka", "01718-501774, 01701-332110", "304/E, Bir Uttam Mir Shawkat Sarak, Tejgaon, Dhaka 1208", "ranjantbiswas@gmail.com"));
        cityGraph.addVertex(new LocationNode(51, "Universal Medical College & Hospital (Aysha Memorial)", 23.7772, 90.4038, true, "Mohakhali", "Dhaka", "01841-480000, 01841-490015", "74G/75, New Airport Road, Beside Mohakhali Flyover, Dhaka 1215", "corporate@umchltd.com"));
        cityGraph.addVertex(new LocationNode(52, "Uttara Crescent Hospital", 23.8688, 90.3985, true, "Uttara", "Dhaka", "01926-993211, 028932430", "House # 21, Road # 15, Sector # 3, Uttara, Dhaka 1230", "corporateuch15@gmail.com"));
        cityGraph.addVertex(new LocationNode(53, "Aichi Hospital Ltd", 23.8821, 90.4012, true, "Uttara", "Dhaka", "01711-612103, 01552-584764", "35 & 37, Sector-08, Abdullahpur Mahasarak, Uttara, Dhaka", "aichi_hospital@yahoo.com"));
        cityGraph.addVertex(new LocationNode(54, "Anwer Khan Modern Medical College Hospital", 23.7478, 90.3789, true, "Dhanmondi", "Dhaka", "10652, 01711-927861", "House-17, Road-8, Dhanmondi, Dhaka 1205", "mukul85bam@gmail.com"));
        cityGraph.addVertex(new LocationNode(55, "Popular Medical College Hospital", 23.7402, 90.3814, true, "Dhanmondi", "Dhaka", "09613 787800, 01717-439218", "08, Road: 02, Dhanmondi, Dhaka 1205", "info@popular-hospital.com"));
        cityGraph.addVertex(new LocationNode(56, "City Hospital Limited", 23.7548, 90.3672, true, "Lalmatia / Mohammadpur", "Dhaka", "01712-577101, 01551-244159", "1/8, Block-E, Lalmatia, Satmosjid Road, Mohammadpur, Dhaka 1207", "cityhosp.emarketing@gmail.com"));
        cityGraph.addVertex(new LocationNode(57, "Samorita Hospital Limited", 23.7525, 90.3831, true, "Panthapath", "Dhaka", "10674, 01711-835011", "89/1 Panthapath, Dhaka 1215", "info@samoritahospital.org"));
        cityGraph.addVertex(new LocationNode(58, "Islami Bank Central Hospital Kakrail", 23.7365, 90.4112, true, "Kakrail", "Dhaka", "01716-606231, 01810-000116", "30, Anjuman Mofidul Islam Road, Kakrail, Dhaka 1000", "ibhdit@yahoo.com"));
        cityGraph.addVertex(new LocationNode(59, "Islami Bank Hospital Motijheel", 23.7341, 90.4195, true, "Motijheel", "Dhaka", "01735-269569, 01727-666741", "24/B, Outer Circular Road, Shahjahanpur, Motijheel, Dhaka", "ibhdhaka@gmail.com"));
        cityGraph.addVertex(new LocationNode(60, "Enam Medical College Hospital", 23.8445, 90.2600, true, "Savar", "Dhaka", "01708-153956, 01858-715243", "9/3 Parboti Nagar, Thana Rd, Savar, Dhaka 1340", "hbjewelkhan@gmail.com"));
        cityGraph.addVertex(new LocationNode(61, "Farazy Hospital Ltd", 23.7628, 90.4352, true, "Banasree / Rampura", "Dhaka", "09606-990000, 01882-084319", "15-19, Block: E, Main Road, Banasree, Rampura, Dhaka 1219", "fhl.mhcorporate@gmail.com"));
        cityGraph.addVertex(new LocationNode(62, "Al Helal Specialized Hospital", 23.8072, 90.3685, true, "Mirpur-10", "Dhaka", "01711-156001, 02-9008181", "150, Begum Rokeya Sarani, Senpara Parbata, Mirpur-10, Dhaka", "ahshcorp@gmail.com"));
        cityGraph.addVertex(new LocationNode(63, "Delta Hospital Limited Mirpur", 23.7998, 90.3532, true, "Mirpur-1", "Dhaka", "02-41002132, 01897-672034", "26/2, Principal Abul Kashem Road, Mirpur-1, Dhaka 1216", "delta@delta-hospital.com"));
        cityGraph.addVertex(new LocationNode(64, "York Hospital Limited", 23.7925, 90.4072, true, "Banani", "Dhaka", "01711-035050, 01992-222777", "House No.: 12 & 13, Road No- 22, Block # K, Banani, Dhaka 1213", "sarifhassan888@gmail.com"));
        cityGraph.addVertex(new LocationNode(65, "Sheikh Fazilatunnessa Mujib KPJ Memorial Hospital", 24.0042, 90.3015, true, "Kashimpur / Gazipur", "Dhaka", "01787-772000", "C/12, Tetubari, Kashimpur, Gazipur, Bangladesh", "amihasan786@gmail.com"));
        cityGraph.addVertex(new LocationNode(66, "Pro-Active Medical College Hospital", 23.6705, 90.4952, true, "Siddirganj / Narayanganj", "Dhaka", "01902-556152, 01852-839686", "E-197/7, Mizmizi, Shahebpara, (Signboard), Siddirganj, Narayanganj", "pmchl@gmail.com"));
        cityGraph.addVertex(new LocationNode(67, "Chattogram Metropolitan Hospital", 22.3615, 91.8220, true, "Chattogram City", "Chattogram", "01844-010546, 01814-651077", "698/752, O.R. Nizam Road, Chattogram, Bangladesh", "nfo@ctgmhpl.com"));
        cityGraph.addVertex(new LocationNode(68, "Max Hospital Ltd", 22.3551, 91.8265, true, "Mehedibag", "Chattogram", "01951-818180, 01713-998166", "35/36, Mehedibag, Chattogram 4000", "billing@maxhospitalbd.com"));
        cityGraph.addVertex(new LocationNode(69, "Mount Adora Hospital", 24.9015, 91.8685, true, "Nayasarak", "Sylhet", "01761-982673", "Akhalia, Sunamganj-Sylhet Road, Nayasarak, Sylhet 3100", "mohsinadora@gmail.com"));
        cityGraph.addVertex(new LocationNode(70, "Khulna City Medical College Hospital", 22.8115, 89.5562, true, "Moylapota", "Khulna", "01989-995011, 01999-099099", "25, 26 KDA Avenue, Moylapota More, Khulna", "kcmchcorporate2526kda@gmail.com"));
        cityGraph.addVertex(new LocationNode(71, "Sodesh Hospital Ltd", 24.7395, 90.4010, true, "Maskanda", "Mymensingh", "01703-505421, 09666-777990", "298/2, Maskanda, Mymensingh", "sodeshhospital@yahoo.com"));
        cityGraph.addVertex(new LocationNode(72, "Marks Medical College & Hospital", 23.8155, 90.3820, true, "Mirpur-14", "Dhaka", "09611772233, 01816-672280", "A/3 Main Road, Section - 14, Mirpur, Dhaka 1206", "grbadal@live.com"));
        cityGraph.addVertex(new LocationNode(73, "Dr. Sirajul Islam Medical College & Hospital", 23.7482, 90.4155, true, "Malibagh", "Dhaka", "01614-561414, 01716-49277", "12/3, New Circular Road, Malibagh, Dhaka 1217", "admin@drsimc.com"));
        cityGraph.addVertex(new LocationNode(74, "Japan Bangladesh Friendship Hospital", 23.7385, 90.3752, true, "Zigatola / Dhanmondi", "Dhaka", "01713-443325, 01711-647877", "55 Satmasjid Road, Zigatola Bus Stand, Dhanmondi, Dhaka 1209", "jbfhbdd2021@gmail.com"));
        cityGraph.addVertex(new LocationNode(75, "Insaf Barakah Kidney & General Hospital", 23.7515, 90.4045, true, "Mogbazar", "Dhaka", "01926-604637, 01979-098073", "11, Shahid Tazuddin Ahmed Shoroni, Mogbazar, Dhaka 1217", "insafbarakahhospitalbd@gmail.com"));
        cityGraph.addVertex(new LocationNode(76, "SIBL Foundation Hospital & Diagnostic Center", 23.7518, 90.3848, true, "Panthapath", "Dhaka", "01757-293434, 01991-150900", "Fattah Plaza, 70 Green Road, Panthapath Moor, Dhaka 1205", "mkt.siblh@gmail.com"));
        cityGraph.addVertex(new LocationNode(77, "Salauddin Specialized Hospital", 23.7162, 90.4208, true, "Wari / Old Dhaka", "Dhaka", "09643252525, 01716-338598", "44/A, Hatkhola Road, Wari, Dhaka", "info@doctorspedia.com"));
        cityGraph.addVertex(new LocationNode(78, "BIRDEM General Hospital", 23.7382, 90.3965, true, "Shahbagh", "Dhaka", "02-41060483, 02-9661551", "122, Kazi Nazrul Islam Avenue, Shahbagh, Dhaka 1000", "info@birdembd.org"));
        cityGraph.addVertex(new LocationNode(79, "Bangladesh Medical College Hospital", 23.7512, 90.3705, true, "Dhanmondi", "Dhaka", "02-44812101", "House # 35, Road # 14/A, Dhanmondi, Dhaka", "bmch@bmch.edu.bd"));
        cityGraph.addVertex(new LocationNode(80, "Labaid Cancer Hospital and Super Specialty Center", 23.7488, 90.3858, true, "Green Road", "Dhaka", "01766-661058, 09666710001", "26 Green Road, Dhaka 1205", "hasib@labaidcancer.com"));
        cityGraph.addVertex(new LocationNode(81, "Dhaka Central International Medical College Hospital (DCIMCH)", 23.7705, 90.3625, true, "Shyamoli / Mohammadpur", "Dhaka", "01712-359933, 01409-967351", "2/1 Ring Road, Shyamoli, Mohammadpur, Dhaka 1207", "mohammadarahman1522@gmail.com"));
        cityGraph.addVertex(new LocationNode(82, "IPDI Foundation Medical Center", 23.7519, 90.3850, true, "Panthapath", "Dhaka", "+880 1834 61 00 28, +880 1614157704", "Fattah Plaza, 7th Floor, 70 Green Road, Panthapath, Dhaka 1205", "ipdi.helo@gmail.com"));
        cityGraph.addVertex(new LocationNode(83, "National Asthma Center (DGHS)", 23.7780, 90.4025, true, "Mohakhali", "Dhaka", "02-9844243", "National Institute of Diseases of the Chest & Hospital Compound, Mohakhali, Dhaka", "nac@hospi.dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(84, "Dhaka Dental College Hospital (DGHS)", 23.8055, 90.3850, true, "Mirpur-14 / Bhasan Tek", "Dhaka", "02-9011938", "Mirpur-14, Dhaka 1206", "ddch@hospi.dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(85, "Sarkari Karmachari Hospital (DGHS)", 23.7258, 90.4082, true, "Fulbaria / Ramna", "Dhaka", "02-9556020", "Fulbaria, Ramna, Dhaka-1000", "geh@hospi.dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(86, "Bangladesh Secretariat Clinic (DGHS)", 23.7295, 90.4098, true, "Paltan / Secretariat", "Dhaka", "02-9571000", "Cabinet Division, Bangladesh Secretariat, Paltan, Dhaka", "sc@cs.dghs.gov.bd"));
        cityGraph.addVertex(new LocationNode(87, "Dhaka Institute of Health Technology (DGHS)", 23.7765, 90.4050, true, "Mohakhali / Banani", "Dhaka", "02-9898000", "Mohakhali Health Complex, Dhaka 1212", "ihtd@ac.dghs.gov.bd"));

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

        // =========================================================================
        // ROAD CONNECTIONS FOR NEW PRIVATE & SPECIALIZED HOSPITALS
        // =========================================================================

        // Dhanmondi / Panthapath / Green Road Cluster
        cityGraph.addEdge(37, 1, 1.5, "Science Lab / Elephant Road", true);
        cityGraph.addEdge(37, 2, 1.6, "Green Road Arterial", true);
        cityGraph.addEdge(37, 55, 0.4, "Dhanmondi Road 2", true);
        cityGraph.addEdge(37, 47, 0.6, "Green Road West", true);
        cityGraph.addEdge(37, 54, 0.8, "Dhanmondi Road 8", true);
        cityGraph.addEdge(37, 74, 1.1, "Satmosjid Road", true);

        // Shahbagh Cluster (Ibrahim Cardiac, BIRDEM, BSMMU)
        cityGraph.addEdge(38, 1, 0.2, "Shahbagh Crossing", true);
        cityGraph.addEdge(38, 78, 0.15, "Kazi Nazrul Islam Avenue", true);
        cityGraph.addEdge(78, 1, 0.2, "Shahbagh Intersection", true);
        cityGraph.addEdge(38, 0, 2.4, "Secretariat Road", true);

        // Shyamoli / Mohammadpur Cluster (BSHL, DCIMCH, City Hospital)
        cityGraph.addEdge(39, 2, 3.4, "Mirpur Road / Asad Gate", true);
        cityGraph.addEdge(39, 48, 1.2, "Shyamoli-Agargaon Link Rd", true);
        cityGraph.addEdge(39, 81, 0.5, "Ring Road Shyamoli", true);
        cityGraph.addEdge(39, 63, 3.3, "Darus Salam Road / Mirpur-1", true);
        cityGraph.addEdge(56, 39, 2.0, "Mirpur Road", true);
        cityGraph.addEdge(56, 54, 1.3, "Satmosjid Road", true);
        cityGraph.addEdge(81, 56, 1.6, "Mohammadpur Ring Road", true);

        // Gulshan / Banani / Bashundhara Cluster (United, York, Evercare)
        cityGraph.addEdge(40, 3, 3.8, "Kemal Ataturk / Airport Road", true);
        cityGraph.addEdge(40, 64, 1.2, "Road 11 Banani", true);
        cityGraph.addEdge(40, 43, 3.2, "Madani Avenue", true);
        cityGraph.addEdge(43, 3, 4.2, "Bashundhara Main Gate / Kuril", true);
        cityGraph.addEdge(64, 51, 2.1, "Mohakhali-Banani Link", true);

        // Old Dhaka / Wari / Gandaria Cluster (Asgar Ali, Salauddin)
        cityGraph.addEdge(41, 0, 3.5, "English Road / Dayaganj", true);
        cityGraph.addEdge(41, 6, 13.5, "Postogola Highway", true);
        cityGraph.addEdge(41, 77, 1.8, "Hatkhola Road", true);
        cityGraph.addEdge(77, 0, 2.8, "Nazira Bazar / Secretariat", true);

        // Panthapath Medical Hub (Square, BRB, Samorita, SIBL, IPDI, Green Life)
        cityGraph.addEdge(45, 2, 0.35, "West Panthapath", true);
        cityGraph.addEdge(45, 57, 0.25, "Panthapath Main Road", true);
        cityGraph.addEdge(45, 76, 0.3, "Green Road Crossing", true);
        cityGraph.addEdge(57, 2, 0.2, "Panthapath", true);
        cityGraph.addEdge(76, 2, 0.4, "Panthapath Moor", true);
        cityGraph.addEdge(76, 82, 0.05, "Fattah Plaza Complex", true);
        cityGraph.addEdge(82, 2, 0.45, "Panthapath", true);
        cityGraph.addEdge(46, 47, 0.5, "Green Road", true);
        cityGraph.addEdge(46, 80, 0.3, "Bir Uttam Shafiullah Sarak", true);
        cityGraph.addEdge(46, 2, 0.8, "Panthapath Crossing", true);
        cityGraph.addEdge(47, 1, 1.2, "Green Road / Elephant Rd", true);
        cityGraph.addEdge(80, 47, 0.6, "Green Road", true);

        // Sher-e-Bangla Nagar / Agargaon
        cityGraph.addEdge(48, 2, 2.8, "Manik Mia Ave / Mirpur Rd", true);

        // Eskaton / Kakrail / Motijheel / Malibagh
        cityGraph.addEdge(49, 1, 1.1, "Minto Road / Eskaton", true);
        cityGraph.addEdge(49, 75, 1.0, "Eskaton Garden Road", true);
        cityGraph.addEdge(49, 58, 1.2, "Kakrail VIP Road", true);
        cityGraph.addEdge(58, 0, 2.0, "Bijoy Nagar / Secretariat", true);
        cityGraph.addEdge(58, 59, 1.4, "Outer Circular Road", true);
        cityGraph.addEdge(59, 73, 1.3, "DIT Road Malibagh", true);
        cityGraph.addEdge(73, 58, 1.5, "Mouchak Flyover", true);
        cityGraph.addEdge(75, 73, 1.2, "Moghbazar Flyover", true);

        // Tejgaon / Mohakhali / Banasree
        cityGraph.addEdge(50, 2, 3.2, "Tejgaon Link Road", true);
        cityGraph.addEdge(50, 51, 1.4, "Shaheed Tajuddin Ahmad Sharani", true);
        cityGraph.addEdge(50, 61, 3.4, "Hatirjheel Expressway", true);
        cityGraph.addEdge(51, 3, 5.0, "New Airport Road", true);
        cityGraph.addEdge(61, 73, 2.6, "Rampura Bridge / DIT Rd", true);

        // Mirpur Cluster (National Heart Foundation, Al Helal, Delta, Marks)
        cityGraph.addEdge(44, 62, 0.9, "Mirpur Section 2 Road", true);
        cityGraph.addEdge(44, 63, 1.4, "Principal Abul Kashem Rd", true);
        cityGraph.addEdge(44, 72, 2.2, "Mirpur 14 Road", true);
        cityGraph.addEdge(62, 72, 1.8, "Begum Rokeya Sarani", true);
        cityGraph.addEdge(62, 3, 5.8, "Mirpur-Cantonment Flyover", true);
        cityGraph.addEdge(72, 3, 3.8, "Mirpur-Airport Link Road", true);

        // Uttara Cluster (Uttara Crescent, Aichi)
        cityGraph.addEdge(52, 3, 5.5, "Dhaka-Mymensingh Highway", true);
        cityGraph.addEdge(52, 53, 1.6, "Sector 8 Abdullahpur Road", true);
        cityGraph.addEdge(53, 4, 11.5, "Dhaka-Mymensingh Highway", true);

        // Savar & Outer Dhaka (Enam Medical, KPJ Gazipur, Pro-Active Narayanganj)
        cityGraph.addEdge(60, 39, 16.0, "Dhaka-Aricha Highway (N5)", true);
        cityGraph.addEdge(60, 65, 18.0, "Savar-Ashulia Highway", true);
        cityGraph.addEdge(65, 4, 8.5, "Konabari-Kashimpur Road", true);
        cityGraph.addEdge(66, 6, 5.5, "Dhaka-Chattogram Highway", true);
        cityGraph.addEdge(66, 41, 7.8, "Signboard Link Road", true);

        // Additional Regional Specialities
        cityGraph.addEdge(67, 12, 1.0, "O.R. Nizam Road", true);
        cityGraph.addEdge(67, 68, 1.2, "Mehedibag Road", true);
        cityGraph.addEdge(68, 12, 0.9, "K.B. Fazlul Kader Road", true);
        cityGraph.addEdge(69, 16, 1.8, "Sunamganj Road / Nayasarak", true);
        cityGraph.addEdge(70, 32, 1.6, "KDA Avenue", true);
        cityGraph.addEdge(71, 19, 1.5, "Maskanda Main Road", true);

        // Dhanmondi internal links
        cityGraph.addEdge(42, 37, 1.4, "Satmosjid Road", true);
        cityGraph.addEdge(42, 54, 0.7, "Dhanmondi Road 15/A", true);
        cityGraph.addEdge(42, 79, 0.3, "Dhanmondi Road 14/A", true);
        cityGraph.addEdge(79, 56, 0.9, "Satmosjid Road", true);

        // DGHS Authentic Facilities Connections
        cityGraph.addEdge(83, 51, 0.35, "Mohakhali TB Gate Road", true);
        cityGraph.addEdge(87, 51, 0.4, "Mohakhali Inter-Complex Link", true);
        cityGraph.addEdge(84, 72, 0.45, "Mirpur 14 Road", true);
        cityGraph.addEdge(84, 62, 1.6, "Begum Rokeya Sarani", true);
        cityGraph.addEdge(85, 0, 0.9, "Secretariat / Fulbaria Link Road", true);
        cityGraph.addEdge(85, 58, 1.1, "Kakrail-Fulbaria Connector", true);
        cityGraph.addEdge(86, 0, 0.75, "Abdul Gani Road / Secretariat", true);
        cityGraph.addEdge(86, 85, 0.5, "Topkhana Road", true);
    }

    /**
     * Pre-populates registered blood donors distributed across all 8 administrative divisions of Bangladesh.
     */
    private void initializeDefaultDonors() {
        // Dhaka Division
        donorRegistry.addLast(new Donor("D-101", "Tanvir Ahmed", BloodType.O_NEG, 1, "+8801711223344", true, "Shahbagh / BSMMU, Dhaka"));
        donorRegistry.addLast(new Donor("D-102", "Sadia Rahman", BloodType.A_POS, 2, "+8801819334455", true, "Square Hospital, Panthapath"));
        donorRegistry.addLast(new Donor("D-103", "Mahmudul Hasan", BloodType.O_POS, 4, "+8801724667788", true, "Chowrasta, Gazipur"));
        donorRegistry.addLast(new Donor("D-104", "Kazi Farhan", BloodType.B_POS, 0, "+8801912445566", true, "Bakshibazar / DMCH, Dhaka"));
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

        // Donors at newly integrated specialized hospitals
        donorRegistry.addLast(new Donor("D-135", "Dr. Tanha Islam", BloodType.O_NEG, 37, "+8801834610028", true, "Labaid Cardiac, Dhanmondi"));
        donorRegistry.addLast(new Donor("D-136", "Rezwanul Hoque", BloodType.A_POS, 40, "+8801712334411", true, "United Hospital, Gulshan-2"));
        donorRegistry.addLast(new Donor("D-137", "Sumaiya Akter", BloodType.B_POS, 43, "+8801913445522", true, "Evercare Hospital, Bashundhara"));
        donorRegistry.addLast(new Donor("D-138", "Fahim Shahriar", BloodType.O_POS, 44, "+8801614556633", true, "National Heart Foundation, Mirpur"));
        donorRegistry.addLast(new Donor("D-139", "Tahmina Khatun", BloodType.AB_POS, 41, "+8801815667744", true, "Asgar Ali Hospital, Gandaria"));
        donorRegistry.addLast(new Donor("D-140", "Arifur Rahman", BloodType.O_NEG, 50, "+8801716778855", true, "Impulse Hospital, Tejgaon"));
        donorRegistry.addLast(new Donor("D-141", "Nadia Sultana", BloodType.B_NEG, 51, "+8801917889966", true, "Universal Medical, Mohakhali"));
        donorRegistry.addLast(new Donor("D-142", "Imtiaz Mahmud", BloodType.A_NEG, 52, "+8801618990077", true, "Uttara Crescent Hospital, Sector 3"));
        donorRegistry.addLast(new Donor("D-143", "Shakil Ahmed", BloodType.O_POS, 60, "+8801819001188", true, "Enam Medical College, Savar"));
        donorRegistry.addLast(new Donor("D-144", "Momena Begum", BloodType.B_POS, 61, "+8801720112299", true, "Farazy Hospital, Banasree"));
        donorRegistry.addLast(new Donor("D-145", "Saiful Islam", BloodType.A_POS, 67, "+8801921223300", true, "Chattogram Metropolitan Hospital"));
        donorRegistry.addLast(new Donor("D-146", "Tasnim Zahan", BloodType.O_NEG, 69, "+8801622334411", true, "Mount Adora Hospital, Sylhet"));
        donorRegistry.addLast(new Donor("D-147", "Kamrul Hasan", BloodType.B_POS, 70, "+8801823445522", true, "Khulna City Medical College"));
        donorRegistry.addLast(new Donor("D-148", "Rubel Hossain", BloodType.O_POS, 71, "+8801724556633", true, "Sodesh Hospital, Mymensingh"));
        donorRegistry.addLast(new Donor("D-149", "Farhana Yasmin", BloodType.AB_NEG, 42, "+8801925667744", true, "Ibn Sina Specialized, Dhanmondi"));
        donorRegistry.addLast(new Donor("D-150", "Nazmul Huda", BloodType.A_POS, 82, "+8801834610028", true, "IPDI Foundation Medical Center"));
    }
}
