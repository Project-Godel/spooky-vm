package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class Assignment {
  public abstract Expression reference();

  public abstract Expression value();

  public abstract Optional<BinaryOp> compound();

  public abstract SourceRange pos();

  public static Assignment create(
      Expression reference, Expression value, Optional<BinaryOp> compound, SourceRange pos) {
    return new AutoValue_Assignment(reference, value, compound, pos);
  }
}
