package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;
import com.google.common.collect.ImmutableList;

@AutoOneOf(Expression.ExpressionKind.class)
public abstract class Expression {
  Expression() {}

  public enum ExpressionKind {
    INT_LITERAL,
    STRING_LITERAL,
    BINARY,
    FUNCTION_CALL,
    REFERENCE,
  }

  public abstract ExpressionKind kind();

  public abstract int intLiteral();

  public abstract String stringLiteral();

  public abstract BinaryExpression binary();

  public abstract FunctionCall functionCall();

  public abstract Identifier reference();

  public static Expression intLiteral(int literal) {
    return AutoOneOf_Expression.intLiteral(literal);
  }

  public static Expression stringLiteral(String literal) {
    return AutoOneOf_Expression.stringLiteral(literal);
  }

  public static Expression boolLiteral(boolean b) {
    return AutoOneOf_Expression.intLiteral(b ? 1 : 0);
  }

  public static Expression reference(Identifier reference) {
    return AutoOneOf_Expression.reference(reference);
  }

  public static Expression binary(Expression left, Expression right, BinaryOperator op) {
    return AutoOneOf_Expression.binary(BinaryExpression.of(left, right, op));
  }

  public static Expression functionCall(Expression left, ImmutableList<Expression> params) {
    return AutoOneOf_Expression.functionCall(FunctionCall.of(left, params));
  }
}
