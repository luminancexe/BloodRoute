package bloodroute.ds;

import bloodroute.model.Edge;
import bloodroute.model.LocationNode;

/**
 * Weighted Graph represented using an Adjacency List.
 * Each vertex has a list of directed/undirected edges connecting to neighboring locations.
 * Uses CustomLinkedList to store edges, fully demonstrating custom DSA.
 */
public class Graph {
    private final int maxVertices;
    private int currentVertices;
    private final LocationNode[] nodes;
    @SuppressWarnings("unchecked")
    private final CustomLinkedList<Edge>[] adjList;

    @SuppressWarnings("unchecked")
    public Graph(int maxVertices) {
        if (maxVertices <= 0) {
            throw new IllegalArgumentException("Max vertices must be positive: " + maxVertices);
        }
        this.maxVertices = maxVertices;
        this.currentVertices = 0;
        this.nodes = new LocationNode[maxVertices];
        this.adjList = (CustomLinkedList<Edge>[]) new CustomLinkedList[maxVertices];

        for (int i = 0; i < maxVertices; i++) {
            this.adjList[i] = new CustomLinkedList<>();
        }
    }

    /**
     * Adds a vertex to the graph.
     */
    public void addVertex(LocationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Location node cannot be null.");
        }
        int id = node.getId();
        if (id < 0 || id >= maxVertices) {
            throw new IndexOutOfBoundsException("Node ID out of graph bounds [0, " + (maxVertices - 1) + "]: " + id);
        }
        if (nodes[id] == null) {
            currentVertices++;
        }
        nodes[id] = node;
    }

    /**
     * Adds a road connection between source and destination.
     *
     * @param src Source node ID
     * @param dest Destination node ID
     * @param distanceKm Distance in kilometers
     * @param roadName Name of the road/avenue
     * @param bidirectional If true, adds road in both directions
     */
    public void addEdge(int src, int dest, double distanceKm, String roadName, boolean bidirectional) {
        validateVertex(src);
        validateVertex(dest);
        if (distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative: " + distanceKm);
        }

        adjList[src].addLast(new Edge(dest, distanceKm, roadName));
        if (bidirectional) {
            adjList[dest].addLast(new Edge(src, distanceKm, roadName));
        }
    }

    public CustomLinkedList<Edge> getNeighbors(int vertexId) {
        validateVertex(vertexId);
        return adjList[vertexId];
    }

    public LocationNode getNode(int vertexId) {
        validateVertex(vertexId);
        return nodes[vertexId];
    }

    public int getNumVertices() {
        return maxVertices;
    }

    public int getActiveVerticesCount() {
        return currentVertices;
    }

    public LocationNode[] getAllNodes() {
        return nodes;
    }

    public CustomLinkedList<Edge>[] getAdjacencyList() {
        return adjList;
    }

    private void validateVertex(int vertexId) {
        if (vertexId < 0 || vertexId >= maxVertices) {
            throw new IndexOutOfBoundsException("Invalid vertex index: " + vertexId);
        }
    }
}
