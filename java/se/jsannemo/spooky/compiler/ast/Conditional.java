package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class Conditional {

  public abstract Expression condition();

  public abstract Statement body();

  public abstract Optional<Statement> elseBody();

  public abstract SourceRange pos();

  public static Conditional create(
      Expression cond, Statement body, Optional<Statement> elseBody, SourceRange pos) {
    return new AutoValue_Conditional(cond, body, elseBody, pos);
  }
}
