package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Func {
  public abstract FuncDecl decl();

  public abstract Statement body();

  public abstract SourceRange pos();

  public static Func create(FuncDecl decl, Statement body, SourceRange pos) {
    return new AutoValue_Func(decl, body, pos);
  }
}
