package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;

import java.util.Optional;

@AutoOneOf(Statement.StatementKind.class)
public abstract class Statement {
  Statement() {}

  public enum StatementKind {
    VAR_DECL,
    EXPRESSION,
    CONDITIONAL,
    LOOP,
    RETURNS,
    NOOP,
    HALT,
  }

  public static Statement varDecl(VarDecl varDecl) {
    return AutoOneOf_Statement.varDecl(varDecl);
  }

  public static Statement expression(Expression expression) {
    return AutoOneOf_Statement.expression(expression);
  }

  public static Statement conditional(Conditional cond) {
    return AutoOneOf_Statement.conditional(cond);
  }

  public static Statement loop(Loop loop) {
    return AutoOneOf_Statement.loop(loop);
  }

  public static Statement returns(Optional<Expression> expression) {
    return AutoOneOf_Statement.returns(expression);
  }

  public static Statement ofNoop() {
    return AutoOneOf_Statement.noop();
  }

  public static Statement ofHalt() {
    return AutoOneOf_Statement.halt();
  }

  public abstract StatementKind kind();

  public abstract VarDecl varDecl();

  public abstract Expression expression();

  public abstract Conditional conditional();

  public abstract Loop loop();

  public abstract Optional<Expression> returns();

  public abstract void noop();

  public abstract void halt();
}
