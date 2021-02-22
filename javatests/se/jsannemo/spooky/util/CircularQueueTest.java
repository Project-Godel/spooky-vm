package se.jsannemo.spooky.util;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

public class CircularQueueTest {

  @Test
  public void testEmpty() {
    CircularQueue<Object> q = CircularQueue.withCapacity(3);
    assertThat(q.empty()).isTrue();
    assertThat(q.size()).isEqualTo(0);
    assertThrows(IllegalStateException.class, q::poll);
    assertThrows(IllegalArgumentException.class, () -> q.get(0));
  }

  @Test
  public void testOperations() {
    CircularQueue<Object> q = CircularQueue.withCapacity(3);
    q.add(1);
    q.add(2);
    q.add(3);
    assertThat(q.size()).isEqualTo(3);
    assertThat(q.empty()).isFalse();
    assertThat(q.get(0)).isEqualTo(1);
    assertThat(q.get(1)).isEqualTo(2);
    assertThat(q.get(2)).isEqualTo(3);
    q.poll();
    assertThat(q.size()).isEqualTo(2);
    assertThat(q.empty()).isFalse();
    assertThat(q.get(0)).isEqualTo(2);
    assertThat(q.get(1)).isEqualTo(3);
    q.add(4);
    assertThat(q.size()).isEqualTo(3);
    assertThat(q.empty()).isFalse();
    assertThat(q.get(0)).isEqualTo(2);
    assertThat(q.get(1)).isEqualTo(3);
    assertThat(q.get(2)).isEqualTo(4);
    q.poll();
    q.poll();
    q.poll();
    assertThat(q.size()).isEqualTo(0);
    assertThat(q.empty()).isTrue();
    q.add(5);
    q.add(6);
    q.add(7);
    assertThat(q.size()).isEqualTo(3);
    assertThat(q.empty()).isFalse();
    assertThat(q.get(0)).isEqualTo(5);
    assertThat(q.get(1)).isEqualTo(6);
    assertThat(q.get(2)).isEqualTo(7);
  }

  @Test
  public void testExceedCapacity() {
    CircularQueue<Object> q = CircularQueue.withCapacity(3);
    q.add(3);
    q.add(3);
    q.add(3);
    assertThrows(IllegalStateException.class, () -> q.add(3));
    q.poll();
    q.add(3);
    assertThrows(IllegalStateException.class, () -> q.add(3));
  }
}
