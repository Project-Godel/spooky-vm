package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Loop {
  Loop() {}

  public abstract Statement initialize();

  public abstract Expression condition();

  public abstract Statement increment();

  public abstract StatementList body();

  public static Loop of(
      Statement initialize, Expression condition, Statement increment, StatementList body) {
    return new AutoValue_Loop(initialize, condition, increment, body);
  }
}
