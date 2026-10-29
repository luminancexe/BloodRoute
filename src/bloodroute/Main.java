package bloodroute;

import bloodroute.algorithm.DonorSearchEngine;
import bloodroute.ds.CustomLinkedList;
import bloodroute.model.*;
import bloodroute.server.BloodRouteWebServer;
import bloodroute.service.BloodRouteService;

import java.awt.Desktop;
import java.net.URI;
import java.util.Scanner;

/**
 * Main application entry point for BloodRoute.
 * Provides a dual-mode interface:
 * 1. Interactive Console CLI (for terminal grading, testing, and exploration).
 * 2. Embedded HTTP Server hosting the interactive Leaflet OpenStreetMap Web UI on port 8080.
 */
public class Main {

    private static final int WEB_PORT = 8080;

    public static void main(String[] args) {
        BloodRouteService service = new BloodRouteService();
        BloodRouteWebServer webServer = new BloodRouteWebServer(WEB_PORT, service);

        // Start Embedded Web Server
        try {
            webServer.start();
        } catch (Exception e) {
            System.err.println("Warning: Could not start web server on port " + WEB_PORT + ": " + e.getMessage());
        }

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        printBanner();

        while (running) {
            printMenu();
            System.out.print("👉 Select an option (1-8): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleDisplayNetwork(service);
                    break;
                case "2":
                    handleDisplayDonors(service);
                    break;
                case "3":
                    handleSearchDonor(service, scanner);
                    break;
                case "4":
                    handleEmergencyRequest(service, scanner);
                    break;
                case "5":
                    handleRegisterDonor(service, scanner);
                    break;
                case "6":
                    openBrowser("http://localhost:" + WEB_PORT);
                    break;
                case "7":
                    runTestSuite();
                    break;
                case "8":
                    System.out.println("\n👋 Shutting down BloodRoute. Stay safe!");
                    webServer.stop();
                    running = false;
                    break;
                default:
                    System.out.println("❌ Invalid choice. Please enter a number between 1 and 8.");
            }

            if (running) {
                System.out.println("\nPress [Enter] to return to menu...");
                scanner.nextLine();
            }
        }

        scanner.close();
    }

    private static void printBanner() {
        System.out.println("=================================================================================");
        System.out.println("  🩸 BLOODROUTE: EMERGENCY BLOOD DONOR MATCHING & ROUTING SYSTEM");
        System.out.println("  Data Structures & Algorithms | Real-Life Emergency System");
        System.out.println("  Location Anchor: Dhaka City Road Network & Medical Facilities");
        System.out.println("  Web Interface:   http://localhost:" + WEB_PORT);
        System.out.println("=================================================================================");
    }

    private static void printMenu() {
        System.out.println("\n------------------------------ MAIN MENU ------------------------------");
        System.out.println(" [1] View Dhaka Road Network Graph (Adjacency List)");
        System.out.println(" [2] Display All Registered Donors");
        System.out.println(" [3] Search Donor by ID (Algorithm 2: Linear Search)");
        System.out.println(" [4] Emergency Blood Request (Algorithm 1: Dijkstra Shortest Path)");
        System.out.println(" [5] Register New Blood Donor");
        System.out.println(" [6] Open Real Map Web UI in Browser (http://localhost:" + WEB_PORT + ")");
        System.out.println(" [7] Run Automated Assessment Rubric Test Suite");
        System.out.println(" [8] Exit");
        System.out.println("------------------------------------------------------------------------");
    }

    private static void handleDisplayNetwork(BloodRouteService service) {
        System.out.println("\n--- DHAKA ROAD NETWORK (GRAPH ADJACENCY LIST) ---");
        LocationNode[] nodes = service.getCityGraph().getAllNodes();
        for (int i = 0; i < service.getCityGraph().getNumVertices(); i++) {
            LocationNode node = nodes[i];
            if (node == null) continue;
            System.out.printf("[%2d] %-42s (%s)%s\n",
                    node.getId(), node.getName(), node.getArea(), node.isHospital() ? " 🏥[HOSPITAL]" : "");
            CustomLinkedList<Edge> edges = service.getCityGraph().getNeighbors(i);
            for (Edge e : edges) {
                LocationNode dest = service.getCityGraph().getNode(e.getDestination());
                String destName = dest != null ? dest.getName() : "Node " + e.getDestination();
                System.out.printf("       └──> to [%2d] %-30s | %.2f km via %s\n",
                        e.getDestination(), destName, e.getDistanceKm(), e.getRoadName());
            }
        }
    }

    private static void handleDisplayDonors(BloodRouteService service) {
        System.out.println("\n--- REGISTERED BLOOD DONORS IN DHAKA ---");
        System.out.printf("%-8s | %-18s | %-6s | %-24s | %-15s | %-10s\n",
                "ID", "Name", "Blood", "Area / Landmark", "Phone", "Status");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (Donor d : service.getDonorRegistry()) {
            LocationNode node = service.getCityGraph().getNode(d.getLocationNodeId());
            String area = node != null ? node.getArea() : "Unknown";
            System.out.printf("%-8s | %-18s | %-6s | %-24s | %-15s | %-10s\n",
                    d.getDonorId(), d.getName(), d.getBloodType().getLabel(), area, d.getPhoneNumber(),
                    d.isAvailable() ? "AVAILABLE" : "BUSY");
        }
    }

    private static void handleSearchDonor(BloodRouteService service, Scanner scanner) {
        System.out.print("\nEnter Donor ID to search (e.g., D-101): ");
        String id = scanner.nextLine().trim();

        // Algorithm 2: Linear Search
        long startTime = System.nanoTime();
        Donor donor = DonorSearchEngine.searchById(service.getDonorRegistry(), id);
        long elapsed = System.nanoTime() - startTime;

        if (donor != null) {
            LocationNode node = service.getCityGraph().getNode(donor.getLocationNodeId());
            System.out.println("\n✅ Donor Found (Search Time: " + elapsed + " ns):");
            System.out.println("   ID:           " + donor.getDonorId());
            System.out.println("   Name:         " + donor.getName());
            System.out.println("   Blood Group:  " + donor.getBloodType().getLabel());
            System.out.println("   Location:     " + (node != null ? node.getName() + " (" + node.getArea() + ")" : "Node " + donor.getLocationNodeId()));
            System.out.println("   Phone:        " + donor.getPhoneNumber());
            System.out.println("   Availability: " + (donor.isAvailable() ? "AVAILABLE" : "UNAVAILABLE"));
            System.out.println("   Address:      " + donor.getAddress());
        } else {
            System.out.println("❌ No donor found with ID: " + id);
        }
    }

    private static void handleEmergencyRequest(BloodRouteService service, Scanner scanner) {
        System.out.println("\n--- EMERGENCY BLOOD REQUEST DISPATCH ---");
        System.out.println("Available Hospitals / Locations:");
        LocationNode[] nodes = service.getCityGraph().getAllNodes();
        for (LocationNode n : nodes) {
            if (n != null && n.isHospital()) {
                System.out.printf("   [%2d] %s (%s)\n", n.getId(), n.getName(), n.getArea());
            }
        }

        System.out.print("\nEnter Requester Hospital Node ID (e.g., 0 for DMCH, 5 for Square): ");
        int reqNode;
        try {
            reqNode = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid node number.");
            return;
        }

        System.out.print("Enter Required Blood Group (e.g., A+, O-, B+, AB+): ");
        String bloodStr = scanner.nextLine().trim();
        BloodType bloodType = BloodType.fromString(bloodStr);

        if (bloodType == null) {
            System.out.println("❌ Invalid blood group format.");
            return;
        }

        System.out.print("Show step-by-step Dijkstra Heap simulation trace? (y/n): ");
        String simChoice = scanner.nextLine().trim().toLowerCase();
        boolean showTrace = simChoice.equals("y") || simChoice.equals("yes");

        System.out.println("\n⚡ Executing Dijkstra Shortest Path & Biological Filtering...");
        long start = System.nanoTime();
        bloodroute.ds.CustomLinkedList<bloodroute.algorithm.DijkstraRouter.SimulationStep> trace = new bloodroute.ds.CustomLinkedList<>();
        CustomLinkedList<MatchResult> matches = service.findNearestCompatibleDonors(reqNode, bloodType, trace);
        long elapsed = System.nanoTime() - start;

        if (showTrace) {
            System.out.println("\n--- [SIMULATION TRACE: DIJKSTRA MIN-HEAP EXECUTION] ---");
            int stepNum = 1;
            for (bloodroute.algorithm.DijkstraRouter.SimulationStep step : trace) {
                switch (step.action) {
                    case "INIT":
                        System.out.printf("[%2d] 🏁 %s\n", stepNum++, step.description);
                        break;
                    case "EXTRACT_MIN":
                        System.out.printf("[%2d] 📥 [HEAP EXTRACT] %s\n", stepNum++, step.description);
                        break;
                    case "EXAMINE_EDGE":
                        System.out.printf("     ├── 🔍 [EXAMINE ROAD] %s\n", step.description);
                        break;
                    case "RELAX_EDGE":
                        System.out.printf("     └── ⚡ [EDGE RELAXED] %s\n", step.description);
                        break;
                    case "SETTLE_NODE":
                        System.out.printf("[%2d] 🎯 [FINALIZED] %s\n\n", stepNum++, step.description);
                        break;
                }
            }
            System.out.println("--- [END OF SIMULATION TRACE] ---\n");
        }

        if (matches.isEmpty()) {
            System.out.println("⚠️ No available compatible donors found in the network.");
            return;
        }

        System.out.printf("✅ Found %d compatible donors (Execution Time: %.3f ms)\n", matches.size(), elapsed / 1_000_000.0);
        System.out.println("\nRANKED COMPATIBLE DONORS (NEAREST TO FARTHEST):");
        System.out.printf("%-4s | %-18s | %-6s | %-10s | %-10s | %-15s | %-20s\n",
                "Rank", "Donor Name", "Blood", "Distance", "ETA", "Phone", "Route Summary");
        System.out.println("----------------------------------------------------------------------------------------------------");

        int rank = 1;
        for (MatchResult m : matches) {
            Donor d = m.getDonor();
            StringBuilder pathSummary = new StringBuilder();
            boolean first = true;
            for (int p : m.getRoutePath()) {
                if (!first) pathSummary.append("->");
                LocationNode ln = service.getCityGraph().getNode(p);
                pathSummary.append(ln != null ? ln.getName().split(" ")[0] : p);
                first = false;
            }

            System.out.printf("#%-3d | %-18s | %-6s | %7.2f km | %6.1f min | %-15s | %s\n",
                    rank++, d.getName(), d.getBloodType().getLabel(),
                    m.getRoadDistanceKm(), m.getEstimatedTimeMinutes(), d.getPhoneNumber(), pathSummary);
        }

        // Highlight nearest donor
        MatchResult top = matches.get(0);
        System.out.println("\n🚨 DISPATCH RECOMMENDED DONOR 🚨");
        System.out.println("   Name:     " + top.getDonor().getName() + " (" + top.getDonor().getBloodType().getLabel() + ")");
        System.out.println("   Distance: " + String.format("%.2f km", top.getRoadDistanceKm()) + " (ETA: " + String.format("%.1f mins", top.getEstimatedTimeMinutes()) + ")");
        System.out.println("   Contact:  " + top.getDonor().getPhoneNumber());
    }

    private static void handleRegisterDonor(BloodRouteService service, Scanner scanner) {
        System.out.println("\n--- REGISTER NEW BLOOD DONOR ---");
        try {
            System.out.print("Enter Donor ID (e.g., D-120): ");
            String id = scanner.nextLine().trim();

            System.out.print("Enter Full Name: ");
            String name = scanner.nextLine().trim();

            System.out.print("Enter Blood Group (O+, O-, A+, A-, B+, B-, AB+, AB-): ");
            String bloodStr = scanner.nextLine().trim();
            BloodType bType = BloodType.fromString(bloodStr);
            if (bType == null) {
                System.out.println("❌ Invalid blood group.");
                return;
            }

            System.out.print("Enter Location Node ID (0-17): ");
            int nodeId = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("Enter Phone Number: ");
            String phone = scanner.nextLine().trim();

            System.out.print("Enter Address / Landmark: ");
            String address = scanner.nextLine().trim();

            Donor newDonor = new Donor(id, name, bType, nodeId, phone, true, address);
            service.registerDonor(newDonor);
            System.out.println("✅ Donor " + name + " (" + bType.getLabel() + ") registered successfully!");
        } catch (Exception e) {
            System.out.println("❌ Registration failed: " + e.getMessage());
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                System.out.println("🌐 Opened " + url + " in your default browser.");
            } else {
                System.out.println("🌐 Please open your browser and navigate to: " + url);
            }
        } catch (Exception e) {
            System.out.println("🌐 Please open your browser and navigate to: " + url);
        }
    }

    private static void runTestSuite() {
        System.out.println("\nRunning Automated Test Suite...");
        try {
            test.bloodroute.BloodRouteTestSuite.main(new String[0]);
        } catch (Exception e) {
            System.err.println("Test suite execution failed: " + e.getMessage());
        }
    }
}
