package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Token {

  public abstract TokenKind kind();

  public abstract String text();

  public abstract SourceRange pos();

  public static Token create(TokenKind kind, String text, SourceRange pos) {
    return new AutoValue_Token(kind, text, pos);
  }
}
