package se.jsannemo.spooky.compiler.codegen;

import se.jsannemo.spooky.compiler.ir.IrAddr;

public final class Conventions {

  private Conventions() {}

  public static final IrAddr STACK_POINTER = IrAddr.absStack(0);
  public static final IrAddr REG_1 = IrAddr.absStack(1);
  public static final IrAddr NEXT_STACK = IrAddr.absStack(2);

  public static final IrAddr CONST_ZERO = IrAddr.dataCell(0);
}
