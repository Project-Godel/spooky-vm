package se.jsannemo.spooky.compiler;

import java.util.Objects;

public final class Position implements Comparable<Position> {
  public final int line;
  public final int column;
  public final int offset;

  public Position(int line, int column, int offset) {
    this.line = line;
    this.column = column;
    this.offset = offset;
  }

  @Override
  public int compareTo(Position o) {
    return Integer.compare(offset, o.offset);
  }

  @Override
  public String toString() {
    return line + ":" + offset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Position position = (Position) o;
    return line == position.line && column == position.column && offset == position.offset;
  }

  @Override
  public int hashCode() {
    return Objects.hash(line, column, offset);
  }
}
