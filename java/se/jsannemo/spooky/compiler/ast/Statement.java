package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;

@AutoOneOf(Statement.Kind.class)
public abstract class Statement {
  public enum Kind {
    BLOCK,
    CONDITIONAL,
    LOOP,
    VAR_DECL,
    EXPRESSION,
    RETURN_VALUE
  }

  public abstract Kind kind();

  public abstract Block block();

  public abstract Conditional conditional();

  public abstract Loop loop();

  public abstract VarDecl varDecl();

  public abstract Expression expression();

  public abstract ReturnValue returnValue();

  public SourceRange pos() {
    switch (kind()) {
      case BLOCK:
        return block().pos();
      case CONDITIONAL:
        return conditional().pos();
      case LOOP:
        return loop().pos();
      case VAR_DECL:
        return varDecl().pos();
      case EXPRESSION:
        return expression().pos();
      case RETURN_VALUE:
        return returnValue().pos();
    }
    throw new IllegalStateException("Invalid switch");
  }

  public static Statement ofBlock(Block block) {
    return AutoOneOf_Statement.block(block);
  }

  public static Statement ofConditional(Conditional conditional) {
    return AutoOneOf_Statement.conditional(conditional);
  }

  public static Statement ofLoop(Loop loop) {
    return AutoOneOf_Statement.loop(loop);
  }

  public static Statement ofVarDecl(VarDecl varDecl) {
    return AutoOneOf_Statement.varDecl(varDecl);
  }

  public static Statement ofExpression(Expression expression) {
    return AutoOneOf_Statement.expression(expression);
  }

  public static Statement ofReturnValue(ReturnValue returnValue) {
    return AutoOneOf_Statement.returnValue(returnValue);
  }
}
