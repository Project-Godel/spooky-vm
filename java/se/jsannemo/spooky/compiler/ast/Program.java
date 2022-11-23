package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Program {

  /**
   * Whether the program is syntactically valid.
   *
   * <p>This can be used to make downstream tools more permissive in allowing e.g. missing required
   * fields in the AST.
   */
  public abstract boolean valid();

  public abstract ImmutableList<VarDecl> globals();

  public abstract ImmutableList<Func> functions();

  public abstract ImmutableList<FuncDecl> externs();

  public static Builder builder() {
    return new AutoValue_Program.Builder();
  }

  /**
   * repeated VarDecl globals = 1; repeated Func functions = 2; repeated FuncDecl externs = 3;
   * repeated StructDecl structs = 4;
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setValid(boolean valid);

    abstract ImmutableList.Builder<VarDecl> globalsBuilder();

    public Builder addGlobal(VarDecl global) {
      globalsBuilder().add(global);
      return this;
    }

    abstract ImmutableList.Builder<FuncDecl> externsBuilder();

    public Builder addExtern(FuncDecl extern) {
      externsBuilder().add(extern);
      return this;
    }

    abstract ImmutableList.Builder<Func> functionsBuilder();

    public Builder addFunction(Func func) {
      functionsBuilder().add(func);
      return this;
    }

    public abstract Program build();
  }
}
