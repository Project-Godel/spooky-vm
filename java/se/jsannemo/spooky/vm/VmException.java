package se.jsannemo.spooky.vm;

/**
 * Exception signifying a run-time error in the program executing in the VM. If an extern function
 * can throw errors that should cause the VM to crash (e.g. on invalid input), they should throw a
 * {@link VmException}.
 */
public class VmException extends Exception {
  public VmException(String s) {
    super(s);
  }
}
