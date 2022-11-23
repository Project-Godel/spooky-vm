package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

/**
 * A character position in a source file. Lines and columns are 1-indexed, while the offset is a
 * zero-indexed byte offset in the entire file. Since Spooky only supports ASCII, this is the same
 * as the zero-indexed character offset.
 */
@AutoValue
public abstract class SourcePos {
  public abstract int line();

  public abstract int col();

  public abstract int offset();

  public static SourcePos of(int line, int col, int offset) {
    return new AutoValue_SourcePos(line, col, offset);
  }
}
