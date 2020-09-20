package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import se.jsannemo.spooky.compiler.Token;

@AutoValue
public abstract class FunctionDecl {
  FunctionDecl() {}

  public abstract Identifier name();

  public abstract ImmutableList<FunctionParam> params();

  public abstract TypeName returnType();

  /** Returns the FUNC or EXTERN token. */
  public abstract Token token();

  public static Builder builder(Identifier name) {
    return new AutoValue_FunctionDecl.Builder()
        .name(name)
        .returnType(TypeName.ofName(Identifier.of("Void", name.token())));
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(Identifier name);

    abstract ImmutableList.Builder<FunctionParam> paramsBuilder();

    public Builder addParam(FunctionParam param) {
      paramsBuilder().add(param);
      return this;
    }

    public abstract Builder returnType(TypeName typeName);

    public abstract Builder token(Token token);

    public abstract FunctionDecl build();
  }
}
