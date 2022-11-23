package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VarDecl {
  public abstract Identifier name();

  public abstract Type type();

  public abstract Expression init();

  public abstract SourceRange pos();

  public static VarDecl create(Identifier name, Type type, Expression init, SourceRange pos) {
    return new AutoValue_VarDecl(name, type, init, pos);
  }
}
