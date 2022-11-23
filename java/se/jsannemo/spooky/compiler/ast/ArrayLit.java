package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class ArrayLit {
  public abstract ImmutableList<Expression> prefix();

  public abstract SourceRange pos();

  public static ArrayLit create(ImmutableList<Expression> prefix, SourceRange pos) {
    return new AutoValue_ArrayLit(prefix, pos);
  }
}
