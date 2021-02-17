package se.jsannemo.spooky.vm.code;

/** An exception signifying an error in the binary format of an executable being parsed. */
public final class InstructionException extends Exception {
  InstructionException(String msg) {
    super(msg);
  }

  InstructionException(String msg, Exception cause) {
    super(msg, cause);
  }
}
