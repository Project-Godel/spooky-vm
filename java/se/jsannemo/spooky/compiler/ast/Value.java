package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;

@AutoOneOf(Value.Kind.class)
public abstract class Value {
  public enum Kind {
    BOOL_LIT,
    INT_LIT,
    STRING_LIT,
    CHAR_LIT
  }

  public abstract Kind kind();

  public abstract BoolLit boolLit();

  public abstract IntLit intLit();

  public abstract StringLit stringLit();

  public abstract CharLit charLit();

  public static Value ofBoolLit(boolean value, SourceRange pos) {
    return AutoOneOf_Value.boolLit(new AutoValue_Value_BoolLit(value, pos));
  }

  public static Value ofIntLit(int value, SourceRange pos) {
    return AutoOneOf_Value.intLit(new AutoValue_Value_IntLit(value, pos));
  }

  public static Value ofStringLit(String value, SourceRange pos) {
    return AutoOneOf_Value.stringLit(new AutoValue_Value_StringLit(value, pos));
  }

  public static Value ofCharLit(char value, SourceRange pos) {
    return AutoOneOf_Value.charLit(new AutoValue_Value_CharLit(value, pos));
  }

  public SourceRange pos() {
    switch (kind()) {
      case BOOL_LIT:
        return boolLit().pos();
      case CHAR_LIT:
        return charLit().pos();
      case INT_LIT:
        return intLit().pos();
      case STRING_LIT:
        return stringLit().pos();
    }
    throw new IllegalStateException("Unknown kind");
  }

  @AutoValue
  public abstract static class BoolLit {
    public abstract boolean value();

    public abstract SourceRange pos();
  }

  @AutoValue
  public abstract static class IntLit {
    public abstract int value();

    public abstract SourceRange pos();
  }

  @AutoValue
  public abstract static class StringLit {
    public abstract String value();

    public abstract SourceRange pos();
  }

  @AutoValue
  public abstract static class CharLit {
    public abstract char value();

    public abstract SourceRange pos();
  }
}
