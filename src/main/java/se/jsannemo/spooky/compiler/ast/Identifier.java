package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import se.jsannemo.spooky.compiler.Token;

@AutoValue
public abstract class Identifier {
  Identifier() {}

  public abstract String name();

  public abstract Token token();

  public static Identifier of(String name, Token token) {
    return new AutoValue_Identifier(name, token);
  }
}
