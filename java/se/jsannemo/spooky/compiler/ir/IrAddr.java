package se.jsannemo.spooky.compiler.ir;

import com.google.auto.value.AutoOneOf;
import se.jsannemo.spooky.vm.CallingConvention;

@AutoOneOf(IrAddr.AddrKind.class)
public abstract class IrAddr {

  public static final IrAddr STACK_POINTER = IrAddr.absStack(CallingConvention.STACK_POINTER_STACK);
  public static final IrAddr REG_1 = IrAddr.absStack(CallingConvention.REG_1_STACK);
  public static final IrAddr NEXT_STACK = IrAddr.absStack(CallingConvention.NEXT_STACK_STACK);
  public static final IrAddr CONST_ZERO = IrAddr.dataCell(CallingConvention.CONST_ZERO_DATA);

  public static final IrAddr CONST_ONE = IrAddr.dataCell(CallingConvention.CONST_ONE_DATA);

  IrAddr() {}

  public enum AddrKind {
    REL_SP,
    ABS_DATA,
    ABS_STACK,
  }

  public abstract AddrKind kind();

  /** Returns the offset relative to the current stack pointer value. */
  public abstract int relSp();

  /**
   * Returns the address of the data cell in real memory space; i.e. the x'th data cell is mapped to
   * -x.
   */
  public abstract int absData();

  /** Returns the absolute address in the stack space. */
  public abstract int absStack();

  public static IrAddr relSp(int addr) {
    return AutoOneOf_IrAddr.relSp(addr);
  }

  public static IrAddr dataCell(int cellIdx) {
    return AutoOneOf_IrAddr.absData(-cellIdx - 1);
  }

  public static IrAddr absStack(int addr) {
    return AutoOneOf_IrAddr.absStack(addr);
  }

  @Override
  public String toString() {
    return switch (kind()) {
      case ABS_DATA -> "DT[" + absData() + "]";
      case ABS_STACK -> "[" + absStack() + "]";
      case REL_SP -> "[SP+" + relSp() + "]";
    };
  }
}
