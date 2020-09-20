package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class BinaryExpression {
  BinaryExpression() {}

  public abstract Expression left();

  public abstract Expression right();

  public abstract BinaryOperator operator();

  public static BinaryExpression of(Expression left, Expression right, BinaryOperator operator) {
    return new AutoValue_BinaryExpression(left, right, operator);
  }
}
