package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Ternary {

  public abstract Expression cond();

  public abstract Expression left();

  public abstract Expression right();

  public abstract SourceRange pos();

  public static Ternary create(
      Expression cond, Expression left, Expression right, SourceRange pos) {
    return new AutoValue_Ternary(cond, left, right, pos);
  }
}
