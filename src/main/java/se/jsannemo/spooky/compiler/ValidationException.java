package se.jsannemo.spooky.compiler;

public final class ValidationException extends Exception {

  public ValidationException(String msg) {
    super(msg);
  }

  public ValidationException(String msg, Token loc) {
    this(loc.beginLine + ":" + loc.beginColumn + ": " + msg);
  }
}
