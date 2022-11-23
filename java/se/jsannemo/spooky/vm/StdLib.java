package se.jsannemo.spooky.vm;

import java.util.Random;

/**
 * {@link StdLib} provides the Spooky standard library and utility methods to implement extern
 * functions with the same calling convention.
 */
public final class StdLib {

  private static final Random RANDOM = new Random();

  private StdLib() {}

  static void random(SpookyVm vm) throws VmException {
    CallingConvention.setReturn(vm, 0, RANDOM.nextInt());
  }

  static void printChar(SpookyVm vm) throws VmException {
    vm.getStdOut().print((char) CallingConvention.getArg(vm, 0));
  }

  static void printInt(SpookyVm vm) throws VmException {
    vm.getStdOut().print(CallingConvention.getArg(vm, 0));
  }
}
