package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Type {
  public abstract String name();

  public abstract ImmutableList<ArrayDimension> dimensions();

  public abstract SourceRange pos();

  public static Type normal(String name, SourceRange pos) {
    return new AutoValue_Type(name, ImmutableList.of(), pos);
  }

  public static Type array(String name, ImmutableList<ArrayDimension> dims, SourceRange pos) {
    return new AutoValue_Type(name, dims, pos);
  }

  @AutoValue
  public abstract static class ArrayDimension {
    public abstract int dimension();

    public abstract SourceRange pos();

    public static ArrayDimension fixed(int dim, SourceRange pos) {
      return new AutoValue_Type_ArrayDimension(dim, pos);
    }
  }
}
