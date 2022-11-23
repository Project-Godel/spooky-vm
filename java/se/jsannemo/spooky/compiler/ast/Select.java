package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Select {

  public abstract Expression operand();

  public abstract Identifier identifier();

  public abstract SourceRange pos();

  public static Select create(Expression operand, Identifier identifier, SourceRange pos) {
    return new AutoValue_Select(operand, identifier, pos);
  }
}
