package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Program {
  Program() {}

  public abstract ImmutableList<Function> functions();

  /** Returns the externally linked functions of the program. */
  public abstract ImmutableList<FunctionDecl> externs();

  public static Builder builder() {
    return new AutoValue_Program.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    abstract ImmutableList.Builder<Function> functionsBuilder();

    public Builder addFunction(Function function) {
      this.functionsBuilder().add(function);
      return this;
    }

    abstract ImmutableList.Builder<FunctionDecl> externsBuilder();

    public Builder addExtern(FunctionDecl functionDecl) {
      this.externsBuilder().add(functionDecl);
      return this;
    }

    public abstract Program build();
  }
}
