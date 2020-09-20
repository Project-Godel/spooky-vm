package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Function {
  Function() {}

  public abstract FunctionDecl declaration();

  public abstract StatementList body();

  public static Function of(FunctionDecl declaration, StatementList body) {
    return new AutoValue_Function(declaration, body);
  }
}
