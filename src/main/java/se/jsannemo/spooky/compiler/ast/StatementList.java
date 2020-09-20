package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class StatementList {
  StatementList() {}

  public abstract ImmutableList<Statement> statements();

  public static Builder builder() {
    return new AutoValue_StatementList.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableList.Builder<Statement> statementsBuilder();

    public Builder addStatement(Statement statement) {
      statementsBuilder().add(statement);
      return this;
    }

    public abstract StatementList build();
  }
}
