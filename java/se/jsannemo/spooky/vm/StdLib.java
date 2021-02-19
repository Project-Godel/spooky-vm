package se.jsannemo.spooky.vm;

import se.jsannemo.spooky.compiler.ir.Conventions;

/**
 * {@link StdLib} provides the Spooky standard library with utility methods to implement extern
 * functions with the same calling convention.
 */
public final class StdLib {

  private StdLib() {}

  /**
   * Returns the argument with offset {@code offset} from the back, starting from 0.
   *
   * @throws VmException if the stack pointer or the argument is out of bounds.
   */
  public static int getArg(SpookyVm vm, int offset) throws VmException {
    int sp = vm.getM(Conventions.STACK_POINTER.getAbs());
    return vm.getM(sp - 1 - offset);
  }

  /**
   * Set the return value of a call to {@code value}, where the parameters of the call had size
   * {@code argSize}.
   *
   * @throws VmException if not enough stack is reserved for the return value.
   */
  public static void setReturn(SpookyVm vm, int argSize, int value) throws VmException {
    int sp = vm.getM(Conventions.STACK_POINTER.getAbs());
    vm.setM(sp - 1 - argSize, value);
  }
}
