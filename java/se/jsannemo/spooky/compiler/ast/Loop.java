package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import java.util.Optional;

@AutoValue
public abstract class Loop {
  public abstract Optional<Statement> init();

  public abstract Optional<Expression> condition();

  public abstract Optional<Statement> increment();

  public abstract Statement body();

  public abstract SourceRange pos();

  public static Builder builder() {
    return new AutoValue_Loop.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setInit(Statement init);

    public abstract Builder setCondition(Expression condition);

    public abstract Builder setIncrement(Statement increment);

    public abstract Builder setBody(Statement body);

    public abstract Builder setPos(SourceRange pos);

    public abstract Loop build();
  }
}
