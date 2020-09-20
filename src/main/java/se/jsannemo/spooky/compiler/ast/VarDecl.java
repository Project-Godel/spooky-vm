package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class VarDecl {
  VarDecl() {}

  public abstract Identifier name();

  public abstract TypeName type();

  public abstract Expression value();

  public static VarDecl of(Identifier name, TypeName type, Expression value) {
    return new AutoValue_VarDecl(name, type, value);
  }
}
