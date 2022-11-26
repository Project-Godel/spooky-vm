package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class FuncDecl {

  public abstract Identifier name();

  public abstract ImmutableList<FuncParam> params();

  public abstract Type returnType();

  public abstract SourceRange pos();

  public static FuncDecl create(
      Identifier name, ImmutableList<FuncParam> params, Type returnType, SourceRange pos) {
    return new AutoValue_FuncDecl(name, params, returnType, pos);
  }

  @AutoValue
  public abstract static class FuncParam {
    public abstract Identifier name();

    public abstract Type type();

    public abstract SourceRange pos();

    public static FuncParam create(Identifier name, Type type, SourceRange pos) {
      return new AutoValue_FuncDecl_FuncParam(name, type, pos);
    }
  }
}
