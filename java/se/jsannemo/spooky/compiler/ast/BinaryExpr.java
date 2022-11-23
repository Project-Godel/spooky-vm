package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class BinaryExpr {
  public abstract Expression left();

  public abstract Expression right();

  public abstract BinaryOp op();

  public abstract SourceRange pos();

  public static BinaryExpr create(Expression left, Expression right, BinaryOp op, SourceRange pos) {
    return new AutoValue_BinaryExpr(left, right, op, pos);
  }
}
