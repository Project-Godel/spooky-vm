package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;

@AutoOneOf(Expression.Kind.class)
public abstract class Expression {

  public enum Kind {
    BINARY,
    VALUE,
    CALL,
    REFERENCE,
    CONDITIONAL,
    ASSIGNMENT,
    UNARY,
    SELECT,
    ARRAY
  }

  public abstract Kind kind();

  public abstract BinaryExpr binary();

  public abstract Value value();

  public abstract FuncCall call();

  public abstract Identifier reference();

  public abstract Ternary conditional();

  public abstract Assignment assignment();

  public abstract UnaryExpr unary();

  public abstract Select select();

  public abstract ArrayLit array();

  public SourceRange pos() {
    switch (kind()) {
      case BINARY:
        return binary().pos();
      case VALUE:
        return value().pos();
      case CALL:
        return call().pos();
      case REFERENCE:
        return reference().pos();
      case CONDITIONAL:
        return conditional().pos();
      case ASSIGNMENT:
        return assignment().pos();
      case UNARY:
        return unary().pos();
      case SELECT:
        return select().pos();
      case ARRAY:
        return array().pos();
    }
    throw new IllegalStateException("Unknown kind");
  }

  public static Expression ofValue(Value value) {
    return AutoOneOf_Expression.value(value);
  }

  public static Expression ofReference(Identifier identifier) {
    return AutoOneOf_Expression.reference(identifier);
  }

  public static Expression ofSelect(Select select) {
    return AutoOneOf_Expression.select(select);
  }

  public static Expression ofCall(FuncCall funcCall) {
    return AutoOneOf_Expression.call(funcCall);
  }

  public static Expression ofBinary(BinaryExpr binaryExpr) {
    return AutoOneOf_Expression.binary(binaryExpr);
  }

  public static Expression ofUnary(UnaryExpr unaryExpr) {
    return AutoOneOf_Expression.unary(unaryExpr);
  }

  public static Expression ofConditional(Ternary ternary) {
    return AutoOneOf_Expression.conditional(ternary);
  }

  public static Expression ofAssignment(Assignment assignment) {
    return AutoOneOf_Expression.assignment(assignment);
  }

  public static Expression ofArray(ArrayLit arrayLit) {
    return AutoOneOf_Expression.array(arrayLit);
  }

  // TODO Pos position = 8;
}
