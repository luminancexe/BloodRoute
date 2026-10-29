package bloodroute.algorithm;

import bloodroute.ds.CustomLinkedList;
import bloodroute.ds.CustomMinHeap;
import bloodroute.ds.Graph;
import bloodroute.model.Edge;

import java.util.Arrays;

/**
 * [ALGORITHM 1: Dijkstra's Shortest Path Algorithm with Step-by-Step Simulation Trace]
 * Computes single-source shortest paths on the road network graph using a custom binary min-heap.
 * Records every algorithmic step (extract-min, edge examination, edge relaxation, node settlement)
 * to power interactive visual simulations for live demonstration.
 *
 * Time Complexity: O((V + E) log V)
 * Space Complexity: O(V)
 */
public class DijkstraRouter {

    /**
     * Represents a single atomic algorithmic event during Dijkstra execution.
     */
    public static class SimulationStep {
        public final String action;      // "EXTRACT_MIN", "EXAMINE_EDGE", "RELAX_EDGE", "SETTLE_NODE"
        public final int u;              // Source vertex
        public final int v;              // Target neighbor vertex (-1 if none)
        public final double distance;    // Current or updated distance
        public final String description; // Human-readable explanation

        public SimulationStep(String action, int u, int v, double distance, String description) {
            this.action = action;
            this.u = u;
            this.v = v;
            this.distance = distance;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("[%s] u=%d, v=%d, dist=%.2f: %s", action, u, v, distance, description);
        }
    }

    public static class RoutingResult {
        public final int sourceNodeId;
        public final int destinationNodeId;
        public final double totalDistanceKm;
        public final CustomLinkedList<Integer> pathNodeIds;
        public final double estimatedTimeMinutes;
        public final boolean isReachable;
        public final CustomLinkedList<SimulationStep> simulationSteps;

        public RoutingResult(int sourceNodeId, int destinationNodeId, double totalDistanceKm,
                             CustomLinkedList<Integer> pathNodeIds, double estimatedTimeMinutes,
                             boolean isReachable, CustomLinkedList<SimulationStep> simulationSteps) {
            this.sourceNodeId = sourceNodeId;
            this.destinationNodeId = destinationNodeId;
            this.totalDistanceKm = totalDistanceKm;
            this.pathNodeIds = pathNodeIds;
            this.estimatedTimeMinutes = estimatedTimeMinutes;
            this.isReachable = isReachable;
            this.simulationSteps = simulationSteps;
        }
    }

    /**
     * Executes Dijkstra's algorithm with optional step-by-step simulation tracing.
     *
     * @param graph The road network
     * @param sourceNodeId Starting node index
     * @param distances Output array filled with shortest distances
     * @param parent Output array filled with predecessor nodes for path reconstruction
     * @param traceList Optional list to collect simulation steps (can be null if not needed)
     */
    public static void computeShortestPaths(Graph graph, int sourceNodeId, double[] distances, int[] parent,
                                            CustomLinkedList<SimulationStep> traceList) {
        int v = graph.getNumVertices();
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[v];

        distances[sourceNodeId] = 0.0;

        CustomMinHeap minHeap = new CustomMinHeap(v);
        minHeap.insert(sourceNodeId, 0.0);

        if (traceList != null) {
            String srcName = graph.getNode(sourceNodeId) != null ? graph.getNode(sourceNodeId).getName() : "Node " + sourceNodeId;
            traceList.addLast(new SimulationStep("INIT", sourceNodeId, -1, 0.0,
                    "Initialized source " + srcName + " with distance 0.0 km. Inserted into Min-Heap."));
        }

        while (!minHeap.isEmpty()) {
            CustomMinHeap.HeapNode current = minHeap.extractMin();
            int u = current.vertexId;

            if (visited[u]) {
                continue;
            }
            visited[u] = true;

            if (traceList != null) {
                String uName = graph.getNode(u) != null ? graph.getNode(u).getName() : "Node " + u;
                traceList.addLast(new SimulationStep("EXTRACT_MIN", u, -1, current.distance,
                        "Extracted min vertex from Heap: " + uName + " (Distance: " + String.format("%.2f", current.distance) + " km)"));
            }

            // Relax all outgoing edges from vertex u
            CustomLinkedList<Edge> neighbors = graph.getNeighbors(u);
            for (Edge edge : neighbors) {
                int neighbor = edge.getDestination();
                double weight = edge.getDistanceKm();

                if (!visited[neighbor]) {
                    if (traceList != null) {
                        String vName = graph.getNode(neighbor) != null ? graph.getNode(neighbor).getName() : "Node " + neighbor;
                        traceList.addLast(new SimulationStep("EXAMINE_EDGE", u, neighbor, weight,
                                "Examining road to " + vName + " (" + String.format("%.2f", weight) + " km via " + edge.getRoadName() + ")"));
                    }

                    double newDist = distances[u] + weight;
                    if (newDist < distances[neighbor]) {
                        distances[neighbor] = newDist;
                        parent[neighbor] = u;
                        minHeap.insert(neighbor, newDist);

                        if (traceList != null) {
                            String vName = graph.getNode(neighbor) != null ? graph.getNode(neighbor).getName() : "Node " + neighbor;
                            traceList.addLast(new SimulationStep("RELAX_EDGE", u, neighbor, newDist,
                                    "Edge relaxed! Shorter path to " + vName + " found: " + String.format("%.2f", newDist) + " km"));
                        }
                    }
                }
            }

            if (traceList != null) {
                String uName = graph.getNode(u) != null ? graph.getNode(u).getName() : "Node " + u;
                traceList.addLast(new SimulationStep("SETTLE_NODE", u, -1, distances[u],
                        "Finalized vertex " + uName + " with optimal distance " + String.format("%.2f", distances[u]) + " km"));
            }
        }
    }

    /**
     * Overload for standard execution without tracing.
     */
    public static void computeShortestPaths(Graph graph, int sourceNodeId, double[] distances, int[] parent) {
        computeShortestPaths(graph, sourceNodeId, distances, parent, null);
    }

    /**
     * Finds the shortest path and total distance from source to a specific destination.
     */
    public static RoutingResult findRoute(Graph graph, int source, int destination) {
        int v = graph.getNumVertices();
        double[] distances = new double[v];
        int[] parent = new int[v];
        CustomLinkedList<SimulationStep> steps = new CustomLinkedList<>();

        computeShortestPaths(graph, source, distances, parent, steps);

        double totalDist = distances[destination];
        boolean reachable = !Double.isInfinite(totalDist);

        CustomLinkedList<Integer> path = new CustomLinkedList<>();
        if (reachable) {
            int curr = destination;
            while (curr != -1) {
                path.addFirst(curr);
                if (curr == source) break;
                curr = parent[curr];
            }
        }

        double etaMinutes = reachable ? (totalDist / 25.0) * 60.0 : -1.0;

        return new RoutingResult(source, destination, totalDist, path, etaMinutes, reachable, steps);
    }
}
