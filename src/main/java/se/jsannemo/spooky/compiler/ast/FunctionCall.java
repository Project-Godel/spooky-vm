package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class FunctionCall {
  FunctionCall() {}

  public abstract Expression function();

  public abstract ImmutableList<Expression> params();

  public static FunctionCall of(Expression function, ImmutableList<Expression> params) {
    return new AutoValue_FunctionCall(function, params);
  }
}
