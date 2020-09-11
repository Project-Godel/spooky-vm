package se.jsannemo.spooky.vm;

import java.util.Random;

/**
 * {@link StdLib} provides utility methods to implement extern functions with the same calling
 * convention as the Spooky standard library.
 */
public final class StdLib {

  private static final int STACK_PTR = 0;
  private static final Random RANDOM = new Random();

  private StdLib() {}

  /**
   * Pops the last argument for the function from the stack and returns the value.
   *
   * @throws VmException if the pop causes the stack to underflow.
   */
  public static int popArg(SpookyVm vm) throws VmException {
    int sp = vm.getM(STACK_PTR);
    int val = vm.getM(sp - 1);
    vm.setM(STACK_PTR, sp - 1);
    return val;
  }

  /**
   * Pushes a return value for a function to the stack.
   *
   * @throws VmException if the push causes the stack to overflow.
   */
  public static void pushArg(SpookyVm vm, int val) throws VmException {
    int sp = vm.getM(STACK_PTR);
    vm.setM(sp, val);
    vm.setM(STACK_PTR, sp + 1);
  }

  static void random(SpookyVm vm) throws VmException {
    pushArg(vm, RANDOM.nextInt());
  }

  static void print(SpookyVm vm) throws VmException {
    System.out.print((char) popArg(vm));
  }
}
