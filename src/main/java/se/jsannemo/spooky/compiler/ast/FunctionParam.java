package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class FunctionParam {
  FunctionParam() {}

  public abstract Identifier name();

  public abstract TypeName type();

  public static FunctionParam ofNameAndType(Identifier name, TypeName type) {
    return new AutoValue_FunctionParam(name, type);
  }
}
