package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import se.jsannemo.spooky.compiler.Token;

import javax.annotation.Nullable;
import java.util.Optional;

/** Execute the {@code body} if the {@code condition} evaluates to true. */
@AutoValue
public abstract class Conditional {
  Conditional() {}

  public abstract Expression condition();

  public abstract StatementList body();

  public abstract Optional<StatementList> elseBody();

  /** Returns the IF token. */
  public abstract Token token();

  public static Conditional of(Expression condition, StatementList body, @Nullable StatementList elseBody, Token token) {
    return new AutoValue_Conditional(condition, body, Optional.ofNullable(elseBody), token);
  }
}
