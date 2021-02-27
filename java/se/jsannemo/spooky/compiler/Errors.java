package se.jsannemo.spooky.compiler;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import se.jsannemo.spooky.compiler.ast.Ast;

public final class Errors {

  private final ArrayList<Error> errors = new ArrayList<>();

  public void error(Ast.Pos pos, String message) {
    errors.add(new Error(pos, message));
  }

  public ImmutableList<Error> errors() {
    return ImmutableList.copyOf(errors);
  }

  public static class Error {
    final Ast.Pos position;
    final String msg;

    Error(Ast.Pos position, String msg) {
      this.position = position;
      this.msg = msg;
    }

    @Override
    public String toString() {
      return position.getOffset() + "-" + position.getEndOffset() + ": " + msg;
    }
  }
}
