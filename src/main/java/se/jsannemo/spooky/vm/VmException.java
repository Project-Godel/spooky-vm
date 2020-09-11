package se.jsannemo.spooky.vm;

/** Exception signifying a run-time error in the program executing in the VM. */
public final class VmException extends Throwable {
  VmException(String s) {
    super(s);
  }
}
