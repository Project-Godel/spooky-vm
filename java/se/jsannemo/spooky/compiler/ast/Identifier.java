package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Identifier {
  public abstract String text();

  public abstract SourceRange pos();

  public static Identifier create(String text, SourceRange pos) {
    return new AutoValue_Identifier(text, pos);
  }
}
