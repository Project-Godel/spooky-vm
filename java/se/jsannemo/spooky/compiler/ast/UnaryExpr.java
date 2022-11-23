package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class UnaryExpr {
  public abstract Expression operand();

  public abstract UnaryOp op();

  public abstract SourceRange pos();

  public static UnaryExpr create(Expression operand, UnaryOp op, SourceRange pos) {
    return new AutoValue_UnaryExpr(operand, op, pos);
  }
}
