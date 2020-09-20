package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class TypeName {
  TypeName() {}

  public abstract Identifier name();

  public abstract int dimension();

  public static TypeName ofName(Identifier name) {
    return new AutoValue_TypeName(name, 0);
  }

  public static TypeName array(Identifier name, int dimension) {
    return new AutoValue_TypeName(name, dimension);
  }
}
