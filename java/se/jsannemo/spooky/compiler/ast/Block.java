package se.jsannemo.spooky.compiler.ast;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

@AutoValue
public abstract class Block {

  public abstract ImmutableList<Statement> statements();

  public abstract SourceRange pos();

  public static Block create(ImmutableList<Statement> statements, SourceRange pos) {
    return new AutoValue_Block(statements, pos);
  }

  public static Block empty(SourceRange pos) {
    return new AutoValue_Block(ImmutableList.of(), pos);
  }
}
