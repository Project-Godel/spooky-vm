package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import se.jsannemo.spooky.compiler.Token;

@AutoOneOf(Expression.ExpressionKind.class)
public abstract class Expression {
  Expression() {}

  public enum ExpressionKind {
    INT_LITERAL,
    BOOL_LITERAL,
    STRING_LITERAL,
    BINARY,
    FUNCTION_CALL,
    REFERENCE,
  }

  public abstract ExpressionKind kind();

  public abstract IntLit intLiteral();

  public abstract BoolLit boolLiteral();

  public abstract StringLit stringLiteral();

  public abstract BinaryExpression binary();

  public abstract FunctionCall functionCall();

  public abstract Identifier reference();

  public static Expression intLiteral(int literal, Token token) {
    return AutoOneOf_Expression.intLiteral(new AutoValue_Expression_IntLit(literal, token));
  }

  public static Expression stringLiteral(String literal, Token token) {
    return AutoOneOf_Expression.stringLiteral(new AutoValue_Expression_StringLit(literal, token));
  }

  public static Expression boolLiteral(boolean b, Token token) {
    return AutoOneOf_Expression.boolLiteral(new AutoValue_Expression_BoolLit(b, token));
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

  @AutoValue
  public abstract static class IntLit {
    public abstract int value();
    public abstract Token token();
  }

  @AutoValue
  public abstract static class BoolLit {
    public abstract boolean value();
    public abstract Token token();
  }

  @AutoValue
  public abstract static class StringLit {
    public abstract String value();
    public abstract Token token();
  }

  public Token firstToken() {
    return switch (kind()) {
      case BINARY -> binary().left().firstToken();
      case REFERENCE -> reference().token();
      case STRING_LITERAL -> stringLiteral().token();
      case BOOL_LITERAL -> boolLiteral().token();
      case FUNCTION_CALL -> functionCall().function().firstToken();
      case INT_LITERAL -> intLiteral().token();
    };
  }

}
