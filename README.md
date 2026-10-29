# BloodRoute 🩸
> **Emergency Blood Donor Matching & Proximity Routing System**  
> *Data Structures & Algorithms Real-World Application*  
> *Anchor City: Dhaka Metropolitan Road Network & Healthcare Facilities*

---

## 🌟 Key Features

1. **Biological Blood Compatibility Engine**:
   - Encapsulates ABO and Rh blood group transfusion logic ($O^-$ Universal Donor, $AB^+$ Universal Recipient).
2. **Real-World Geographic Map (Dhaka, Bangladesh)**:
   - Interactive OpenStreetMap & Leaflet web interface showing real hospitals (DMCH, BSMMU, Square, Central, Suhrawardy) and road networks.
   - Dynamic animated polyline tracing the exact shortest route on city streets from the hospital to the nearest donor.
3. **Pure Java Embedded Architecture**:
   - Uses Java standard library `com.sun.net.httpserver.HttpServer`.
   - **Zero external frameworks/dependencies** (no Maven/Gradle, Node.js, or Spring required). Compiles cleanly with standard `javac`.
4. **Dual-Mode User Interface**:
   - **Interactive Web UI**: Available at `http://localhost:8080`.
   - **Interactive Terminal CLI**: Accessible directly in the console for automated testing, quick evaluation, and terminal grading.
5. **Core DSA Architecture**:
   - Modular Data Structures: `Graph`, `CustomMinHeap`, `CustomLinkedList`.
   - Core Algorithms: `Dijkstra's Algorithm`, `Linear Search`.
   - Custom implementations written **completely from scratch**.

---

## 🏗️ Data Structures & Algorithms Summary

| # | Component | Type | Big-O Time Complexity | Role in System |
|---|---|---|---|---|
| **DS 1** | **Graph** (Adjacency List) | Custom OOP | Space: $\mathcal{O}(V + E)$ | Models intersections (nodes) and road connections (weighted edges in km). |
| **DS 2** | **CustomMinHeap** | **Implemented from scratch** | Insert: $\mathcal{O}(\log V)$, ExtractMin: $\mathcal{O}(\log V)$ | Min-Priority Queue for Dijkstra's algorithm to extract min-distance vertices. |
| **DS 3** | **CustomLinkedList** | **Implemented from scratch** | Append: $\mathcal{O}(1)$, Traversal: $\mathcal{O}(N)$ | Stores registered donors, match results, and reconstructed route path sequence. |
| **ALGO 1** | **Dijkstra's Algorithm** | Shortest Path | $\mathcal{O}((V + E) \log V)$ | Computes fastest road route and distance from hospital to all candidate donors. |
| **ALGO 2** | **Linear Search** | Search & Filter | $\mathcal{O}(N)$ | Filters biologically compatible donors and searches donor records by ID. |

---

## 🚀 Quick Start Guide

### Prerequisites
- Java Development Kit (JDK 17 or higher, tested with JDK 25 LTS).

### Run with One Click (Windows)
Double-click `run.bat` or run:
```powershell
.\run.bat
```

### Manual Compile & Run
```bash
# 1. Compile all Java classes into bin/
javac -d bin src/bloodroute/model/*.java src/bloodroute/ds/*.java src/bloodroute/algorithm/*.java src/bloodroute/service/*.java src/bloodroute/server/*.java src/bloodroute/*.java test/bloodroute/*.java

# 2. Run the Main Application
java -cp bin bloodroute.Main

# 3. Open Web UI in Browser
# Navigate to: http://localhost:8080
```

### Run Automated Assessment Test Suite
```bash
java -cp bin test.bloodroute.BloodRouteTestSuite
```

---

## 📁 Project Directory Layout

```
d:\Projects\BloodRoute\
├── src\
│   └── bloodroute\
│       ├── Main.java                        // Entry point (CLI & Web launcher)
│       ├── model\
│       │   ├── BloodType.java               // ABO/Rh compatibility rules
│       │   ├── Donor.java                   // Registered donor entity
│       │   ├── LocationNode.java            // Graph vertex (Dhaka coordinates)
│       │   ├── Edge.java                    // Weighted road segment
│       │   └── MatchResult.java             // Match result with distance & route
│       ├── ds\
│       │   ├── CustomLinkedList.java        // [MANUAL DS 1] Generic Singly Linked List
│       │   ├── CustomMinHeap.java           // [MANUAL DS 2] Binary Min-Heap
│       │   └── Graph.java                   // [MANUAL DS 3] Weighted Adjacency List
│       ├── algorithm\
│       │   ├── DijkstraRouter.java          // [ALGORITHM 1] Dijkstra Shortest Path
│       │   └── DonorSearchEngine.java       // [ALGORITHM 2] Linear Search & Compatibility Filter
│       ├── service\
│       │   └── BloodRouteService.java       // Dhaka road network & business logic
│       └── server\
│           └── BloodRouteWebServer.java     // Pure Java embedded HTTP server
├── web\
│   ├── index.html                           // Emergency Dispatcher UI with Leaflet
│   ├── style.css                            // Emergency responsive styling
│   └── app.js                               // Real map controller & route highlighter
├── test\
│   └── bloodroute\
│       └── BloodRouteTestSuite.java         // Automated test suite for edge cases
└── run.bat                                  // One-click compile & run script
```
