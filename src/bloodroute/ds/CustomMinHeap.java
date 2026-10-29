package bloodroute.ds;

import java.util.NoSuchElementException;

/**
 * Custom array-backed Binary Min-Heap implemented from scratch.
 * Satisfies BIC2214 requirements for manual data structure implementation.
 * Used as the Priority Queue for Dijkstra's shortest path algorithm.
 */
public class CustomMinHeap {

    public static class HeapNode {
        public final int vertexId;
        public final double distance;

        public HeapNode(int vertexId, double distance) {
            this.vertexId = vertexId;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return String.format("(Vertex: %d, Distance: %.2f)", vertexId, distance);
        }
    }

    private HeapNode[] heap;
    private int size;
    private static final int DEFAULT_CAPACITY = 32;

    public CustomMinHeap() {
        this(DEFAULT_CAPACITY);
    }

    public CustomMinHeap(int initialCapacity) {
        if (initialCapacity <= 0) {
            initialCapacity = DEFAULT_CAPACITY;
        }
        this.heap = new HeapNode[initialCapacity];
        this.size = 0;
    }

    /**
     * Inserts a new node into the min-heap.
     * Time Complexity: O(log V)
     */
    public void insert(int vertexId, double distance) {
        ensureCapacity();
        HeapNode newNode = new HeapNode(vertexId, distance);
        heap[size] = newNode;
        siftUp(size);
        size++;
    }

    /**
     * Removes and returns the minimum element (root).
     * Time Complexity: O(log V)
     */
    public HeapNode extractMin() {
        if (isEmpty()) {
            throw new NoSuchElementException("Min-Heap is empty.");
        }
        HeapNode minNode = heap[0];
        HeapNode lastNode = heap[size - 1];
        heap[0] = lastNode;
        heap[size - 1] = null;
        size--;

        if (size > 0) {
            siftDown(0);
        }
        return minNode;
    }

    /**
     * Returns the minimum element without removing it.
     * Time Complexity: O(1)
     */
    public HeapNode peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Min-Heap is empty.");
        }
        return heap[0];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parentIndex = (index - 1) / 2;
            if (heap[index].distance < heap[parentIndex].distance) {
                swap(index, parentIndex);
                index = parentIndex;
            } else {
                break;
            }
        }
    }

    private void siftDown(int index) {
        while (index < size) {
            int leftChild = 2 * index + 1;
            int rightChild = 2 * index + 2;
            int smallest = index;

            if (leftChild < size && heap[leftChild].distance < heap[smallest].distance) {
                smallest = leftChild;
            }
            if (rightChild < size && heap[rightChild].distance < heap[smallest].distance) {
                smallest = rightChild;
            }

            if (smallest != index) {
                swap(index, smallest);
                index = smallest;
            } else {
                break;
            }
        }
    }

    private void swap(int i, int j) {
        HeapNode temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    private void ensureCapacity() {
        if (size >= heap.length) {
            HeapNode[] newHeap = new HeapNode[heap.length * 2];
            System.arraycopy(heap, 0, newHeap, 0, heap.length);
            heap = newHeap;
        }
    }
}
