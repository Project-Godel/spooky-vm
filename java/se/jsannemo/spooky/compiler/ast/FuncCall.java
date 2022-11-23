package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class FuncCall {
  public abstract Identifier function();

  public abstract ImmutableList<Expression> params();

  public abstract SourceRange pos();

  public static FuncCall create(
      Identifier function, ImmutableList<Expression> params, SourceRange pos) {
    return new AutoValue_FuncCall(function, params, pos);
  }
}
