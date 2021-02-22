package se.jsannemo.spooky.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/** An array-backed, fixed capacity circular queue that supports random access. */
public final class CircularQueue<T> {
  // The index of the first element in the queue.
  private int head = 0;
  // The current number of elements in the queue.
  private int size = 0;
  private final Object[] elements;

  private CircularQueue(int capacity) {
    this.elements = new Object[capacity];
  }

  /**
   * Adds a value to queue.
   *
   * @throws IllegalStateException if the queue exceeds is size.
   */
  public void add(T val) {
    if (size == elements.length) {
      throw new IllegalStateException("Capacity exceeded");
    }
    int nx = (head + size) % elements.length;
    elements[nx] = val;
    size++;
  }

  /**
   * Returns the {@code idx}'th value from the queue, zero-indexed.
   *
   * @throws IllegalArgumentException if {@code idx} is out of range.
   */
  @SuppressWarnings("unchecked")
  public T get(int idx) {
    checkArgument(0 <= idx && idx < size, "Index out of bounds");
    return (T) elements[(head + idx) % elements.length];
  }

  /**
   * Returns and removes the first value of the queue.
   *
   * @throws IllegalStateException if the queue is empty.
   */
  @SuppressWarnings("unchecked")
  public T poll() {
    checkState(size > 0, "Queue is empty");
    T ret = (T) elements[head];
    head = (head + 1) % elements.length;
    size--;
    return ret;
  }

  public boolean empty() {
    return size == 0;
  }

  public int size() {
    return size;
  }

  public static <T> CircularQueue<T> withCapacity(int cap) {
    return new CircularQueue<>(cap);
  }
}
