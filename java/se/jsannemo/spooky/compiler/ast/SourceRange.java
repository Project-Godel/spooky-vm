package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SourceRange {

  /** Start of the range, inclusive. */
  public abstract SourcePos from();
  /** End of the range, inclusive. */
  public abstract SourcePos to();

  public SourceRange extend(SourcePos to) {
    return between(from(), to);
  }

  public SourceRange extend(SourceRange to) {
    return between(from(), to.to());
  }

  public SourceRange extend(Token token) {
    if (token != null) {
      return extend(token.pos());
    }
    return this;
  }

  public static SourceRange between(SourcePos from, SourcePos to) {
    return new AutoValue_SourceRange(from, to);
  }
}
