package se.jsannemo.spooky.compiler;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import jsinterop.annotations.JsConstructor;
import jsinterop.annotations.JsMethod;
import se.jsannemo.spooky.compiler.ast.SourceRange;

public final class Errors {

  private final ArrayList<Error> errors = new ArrayList<>();

  @JsConstructor
  public Errors() {}

  public void error(SourceRange pos, String message) {
    errors.add(new Error(pos, message));
  }

  @JsMethod
  public ImmutableList<Error> errors() {
    return ImmutableList.copyOf(errors);
  }

  public static class Error {
    final SourceRange position;
    final String msg;

    Error(SourceRange position, String msg) {
      this.position = position;
      this.msg = msg;
    }

    @Override
    public String toString() {
      return position.from().line() + ":" + position.from().col() + ": " + msg;
    }
  }
}
