package se.jsannemo.spooky.compiler;

import se.jsannemo.spooky.compiler.ast.Ast;

public final class ValidationException extends Exception {

  public ValidationException(String msg) {
    super(msg);
  }

  public ValidationException(String msg, Ast.Pos loc) {
    this(loc.getLine() + ":" + loc.getCol() + ": " + msg);
  }
}
