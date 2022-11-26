package se.jsannemo.spooky.vm;

public final class CallingConvention {

  private CallingConvention() {}

  // Stack address of the stack pointer
  public static final int STACK_POINTER_STACK = 0;

  // Stack address of register 1
  public static final int REG_1_STACK = 1;

  // Starting address of the free stack space
  public static final int NEXT_STACK_STACK = 2;

  // Data address guaranteed to contain 0
  public static final int CONST_ZERO_DATA = 0;

  // Data address guaranteed to contain 1
  public static final int CONST_ONE_DATA = 1;

  /**
   * Returns the argument with offset {@code offset} from the back, starting from 0.
   *
   * @throws VmException if the stack pointer or the argument is out of bounds.
   */
  public static int getArg(SpookyVm vm, int offset) throws VmException {
    int sp = vm.getM(STACK_POINTER_STACK);
    return vm.getM(sp - 1 - offset);
  }

  /**
   * Set the return value of a call to {@code value}, where the parameters of the call had size
   * {@code argSize}.
   *
   * @throws VmException if not enough stack is reserved for the return value.
   */
  public static void setReturn(SpookyVm vm, int argSize, int value) throws VmException {
    int sp = vm.getM(STACK_POINTER_STACK);
    vm.setM(sp - 1 - argSize, value);
  }
}
