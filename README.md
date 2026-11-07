# BloodRoute 🩸
> **Emergency Blood Logistics & Proximity Routing System**  
> *Data Structures & Algorithms (BIC2214) Production Application*  
> *Coverage: Bangladesh Nationwide Medical Network & National Highways (All 8 Administrative Divisions)*

---

## 📋 Executive Overview

**BloodRoute** is an emergency healthcare logistics platform designed to locate and dispatch the nearest biologically compatible blood donors to hospitals in Bangladesh. The application pairs a pure, zero-dependency Java 17+ backend with an interactive Leaflet and OpenStreetMap web console.

The routing engine employs a **hybrid proximity architecture**:
1. **Biological Filtering (Linear Search)** filters registered donors based on clinical ABO/Rh transfusion rules ($O^-$ universal donor, $AB^+$ universal recipient).
2. **Custom Dijkstra's Algorithm** with an array-backed Binary Min-Heap ranks all eligible donors strictly by graph road distance across 88 medical hubs.
3. **OpenStreetMap & OSRM Engine** dynamically calculates and renders real street-following geometry, road distances, driving transit times, and turn-by-turn guidance for the selected primary responder.

---

## 🌟 Key Features

- **Pure Java Embedded Architecture**:
  - Built directly on Java's native `com.sun.net.httpserver.HttpServer` and `java.net.http.HttpClient`.
  - **Zero external frameworks or build dependencies** — no Maven, Gradle, Spring Boot, or Node.js required to run the core server.
- **Manual Data Structures & Algorithms from Scratch**:
  - Weighted Adjacency List Graph (`Graph`).
  - Array-backed Binary Min-Heap Priority Queue (`CustomMinHeap`).
  - Singly Linked List with `Iterable` support (`CustomLinkedList`).
  - Single-Source Shortest Path (`DijkstraRouter`).
  - Sequential Search & Biological Filter (`DonorSearchEngine`).
- **Real-Time Step-by-Step Algorithm Simulation**:
  - Live visual demonstration of Dijkstra min-heap operations (node extraction, road examination, edge relaxation, and node finalization) with progress telemetry.
- **Real Road-Network Routing (OSRM & OpenStreetMap)**:
  - Queries OpenStreetMap via OSRM to generate actual street navigation geometry (`LineString`), driving durations, and turn-by-turn maneuver steps.
  - Distinct emerald road visualization with dark casing for high contrast.
  - Automatic fallback to Dijkstra graph topology if external routing is unreachable or for on-site facility matches.
- **Authentic Bangladesh Healthcare Network (88 Facilities)**:
  - 88 real medical institutions mapped across all 8 divisions (Dhaka, Chattogram, Rajshahi, Khulna, Barishal, Sylhet, Rangpur, Mymensingh), including national institutes from the Directorate General of Health Services (DGHS) facility registry.
- **Dual Operating Modes**:
  - **Web Dashboard**: High-density healthcare logistics interface at `http://localhost:8080`.
  - **Console CLI**: Interactive terminal interface with color banners and test runner for grading.

---

## 🏗️ Data Structures & Algorithms Specifications

| Component | Class | Manual Implementation Details | Time Complexity | Role in System |
|---|---|---|---|---|
| **DS 1** | `Graph` | Custom Adjacency List with `LocationNode` array and `CustomLinkedList<Edge>[]` | Space: $\mathcal{O}(V + E)$ | Models intersections, medical centers, and bidirectional highway segments. |
| **DS 2** | `CustomMinHeap` | Binary Min-Heap backed by dynamic array with `siftUp()` and `siftDown()` | Insert: $\mathcal{O}(\log V)$<br>ExtractMin: $\mathcal{O}(\log V)$ | Serves as the priority queue for Dijkstra's algorithm. |
| **DS 3** | `CustomLinkedList<E>` | Generic singly linked list with head and tail pointers and custom `Iterator` | Append: $\mathcal{O}(1)$<br>Prepend: $\mathcal{O}(1)$<br>Traversal: $\mathcal{O}(N)$ | Stores donor registries, graph edge lists, and reconstructed path vertices. |
| **ALGO 1** | `DijkstraRouter` | Single-source shortest path using `CustomMinHeap` and predecessor tracking | $\mathcal{O}((V + E) \log V)$ | Calculates minimum network distance and produces the shortest path vertex chain. |
| **ALGO 2** | `DonorSearchEngine` | Sequential search and biological compatibility filter | $\mathcal{O}(N)$ | Filters available donors by blood compatibility and searches records by ID or name. |

---

## 🌐 Hybrid Routing Architecture

```
                                  [ Emergency Blood Request ]
                               (Hospital Node ID + Blood Type)
                                              │
                                              ▼
                                 [ Algorithm 2: Linear Search ]
                           Biologically filters eligible available donors
                                              │
                                              ▼
                             [ Algorithm 1: Custom Dijkstra ]
                         Uses CustomMinHeap to rank candidate donors
                                strictly by graph road distance
                                              │
                                              ▼
                                   [ Ranked Candidates ]
                           Top candidate selected as Primary Responder
                                              │
                                              ▼
                              [ OpenStreetMap OSRM Service ]
                         Calculates actual street-following geometry,
                          precise road distance, and driving duration
                                              │
                                              ▼
                                  [ Leaflet Map Visualization ]
                         • Emerald green GeoJSON route casing
                         • Turn-by-turn navigation guidance drawer
                         • Fallback to Dijkstra graph if offline
```

---

## 🔌 REST API Endpoints

The embedded HTTP server on port 8080 exposes the following REST endpoints:

| Endpoint | Method | Description | Sample Query / Body |
|---|---|---|---|
| `/api/network` | `GET` | Returns graph nodes (hospitals/intersections) and highway edges. | N/A |
| `/api/donors` | `GET` | Returns all registered donors in the system. | N/A |
| `/api/match` | `POST` | Executes Dijkstra proximity matching for a hospital and blood group. | `{"requesterNodeId": 0, "bloodType": "A+"}` |
| `/api/route` | `GET` | Proxies OSRM to calculate real street geometry, distance, and duration. | `?startLat=23.7258&startLon=90.3976&endLat=23.7388&endLon=90.3956` |
| `/api/donors` | `POST` | Registers a new blood donor into the live registry. | `{"name":"...","bloodType":"O-","phone":"...","locationNodeId":83}` |

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java Development Kit (JDK 17 or higher)**.
- Any modern web browser (Chrome, Edge, Firefox, Safari).

### Launch with One Click (Windows)
Double-click `run.bat` or run in PowerShell:
```powershell
.\run.bat
```

### Manual Compilation & Execution
```bash
# 1. Compile all source and test files into bin/
javac -d bin src/bloodroute/model/*.java src/bloodroute/ds/*.java src/bloodroute/algorithm/*.java src/bloodroute/service/*.java src/bloodroute/server/*.java src/bloodroute/*.java test/bloodroute/*.java

# 2. Start BloodRoute
java -cp bin bloodroute.Main

# 3. Open Web UI
# Navigate to: http://localhost:8080
```

### Run Automated Assessment Test Suite
Double-click `test.bat` or run:
```bash
# Windows batch script:
.\test.bat

# Or direct Java command:
java -cp bin test.bloodroute.BloodRouteTestSuite
```

---

## 🧪 Automated Test Suite Coverage

The test suite (`test.bloodroute.BloodRouteTestSuite`) runs 28 automated tests across 5 categories:

1. **Category 1: Normal Data Testing**:
   - Matching returns ordered compatible donors.
   - Linear search by ID and substring name lookup.
   - Facility contact metadata integrity.
2. **Category 2: Empty Structure Testing**:
   - `CustomLinkedList` empty state, size checks, and graceful search handling.
   - `CustomMinHeap` empty state, `extractMin()` and `peek()` `NoSuchElementException` verification.
3. **Category 3: Invalid Input Testing**:
   - Rejection of invalid blood group strings.
   - Negative edge weight rejection in `Graph`.
   - Out-of-bounds node index validation.
   - Null and empty parameter rejection in `Donor` constructor.
4. **Category 4: Duplicate Value Testing**:
   - Prevention of duplicate donor ID registration.
5. **Category 5: Boundary Cases Testing**:
   - Self-routing distance equals $0.0\text{ km}$ and reachable.
   - Disconnected components detected as unreachable ($\infty$ distance).
   - Universal donor ($O^-$) and universal recipient ($AB^+$) boundaries.
   - **Full 64-combination ABO/Rh matrix test** enforcing Rh-negative recipient safety.
   - `CustomMinHeap` priority order verification across arbitrary numeric inputs.
   - `CustomLinkedList` node removal (head, middle, and tail nodes).
   - Bangladesh geographical coordinate boundaries validation for all 88 facilities.

---

## 📁 Repository Directory Layout

```
BloodRoute/
├── src/
│   └── bloodroute/
│       ├── Main.java                        // Dual-mode entry point (CLI & Web server)
│       ├── model/
│       │   ├── BloodType.java               // ABO/Rh biological compatibility rules
│       │   ├── Donor.java                   // Registered blood donor entity
│       │   ├── LocationNode.java            // Graph vertex (facility & coordinates)
│       │   ├── Edge.java                    // Weighted road segment (distance in km)
│       │   └── MatchResult.java             // Proximity match result & path
│       ├── ds/
│       │   ├── CustomLinkedList.java        // Generic Singly Linked List from scratch
│       │   ├── CustomMinHeap.java           // Binary Min-Heap Priority Queue from scratch
│       │   └── Graph.java                   // Weighted Adjacency List Graph from scratch
│       ├── algorithm/
│       │   ├── DijkstraRouter.java          // Dijkstra shortest path with simulation trace
│       │   └── DonorSearchEngine.java       // Linear search & biological filter engine
│       ├── service/
│       │   └── BloodRouteService.java       // Bangladesh road network & business logic
│       └── server/
│           └── BloodRouteWebServer.java     // Pure Java embedded HTTP server & REST API
├── web/
│   ├── index.html                           // Emergency dispatch dashboard
│   ├── style.css                            // Healthcare logistics design system
│   └── app.js                               // Leaflet controller, OSRM routing & simulation
├── test/
│   └── bloodroute/
│       └── BloodRouteTestSuite.java         // 28-test automated verification suite
├── .gitignore                               // Clean git ignore configuration
├── run.bat                                  // One-click compilation and launcher script
└── README.md                                // Production documentation
```

---

## ⚠️ Known Limitations & Design Notes

1. **OSRM Public Endpoint**:
   - The application queries the public demonstration instance of OSRM (`https://router.project-osrm.org/`).
   - If internet access is interrupted or the public OSRM server experiences high latency, BloodRoute automatically falls back to rendering the custom Dijkstra graph path with full warning telemetry.
2. **Speed Calculation**:
   - Estimated driving durations use real-world traffic benchmarks: intra-city transit in Bangladesh averages $35\text{ km/h}$, while national highway transit averages $65\text{ km/h}$.
