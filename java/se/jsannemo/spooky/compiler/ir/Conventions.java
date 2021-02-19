package se.jsannemo.spooky.compiler.ir;

public final class Conventions {

  private Conventions() {}

  public static final Ir.Addr STACK_POINTER = IrAddrs.absStack(0);
  public static final Ir.Addr REG_1 = IrAddrs.absStack(1);
  public static final Ir.Addr NEXT_STACK = IrAddrs.absStack(2);

  public static final Ir.Addr CONST_ZERO = IrAddrs.dataCell(0);
}
