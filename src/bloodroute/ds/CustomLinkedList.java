package bloodroute.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Custom generic singly linked list implemented from scratch.
 * Satisfies BIC2214 requirements for manual data structure implementation.
 *
 * @param <E> Element type
 */
public class CustomLinkedList<E> implements Iterable<E> {

    public static class Node<E> {
        public E data;
        public Node<E> next;

        public Node(E data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node<E> head;
    private Node<E> tail;
    private int size;

    public CustomLinkedList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Appends an element to the end of the list.
     * Time Complexity: O(1) using tail pointer.
     */
    public void addLast(E data) {
        Node<E> newNode = new Node<>(data);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
        size++;
    }

    /**
     * Inserts an element at the beginning of the list.
     * Time Complexity: O(1).
     */
    public void addFirst(E data) {
        Node<E> newNode = new Node<>(data);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            newNode.next = head;
            head = newNode;
        }
        size++;
    }

    /**
     * Retrieves element at specified index.
     * Time Complexity: O(N) worst case.
     */
    public E get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Node<E> current = head;
        for (int i = 0; i < index; i++) {
            current = current.next;
        }
        return current.data;
    }

    /**
     * Removes the first occurrence of the specified element.
     * Time Complexity: O(N).
     * @return true if found and removed, false otherwise
     */
    public boolean remove(E data) {
        if (head == null) return false;

        if ((head.data == null && data == null) || (head.data != null && head.data.equals(data))) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
            size--;
            return true;
        }

        Node<E> current = head;
        while (current.next != null) {
            if ((current.next.data == null && data == null) ||
                (current.next.data != null && current.next.data.equals(data))) {
                if (current.next == tail) {
                    tail = current;
                }
                current.next = current.next.next;
                size--;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Clears all elements.
     */
    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public Node<E> getHead() {
        return head;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private Node<E> current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                E val = current.data;
                current = current.next;
                return val;
            }
        };
    }
}
