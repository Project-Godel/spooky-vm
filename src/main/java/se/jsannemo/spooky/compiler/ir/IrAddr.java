package se.jsannemo.spooky.compiler.ir;

import com.google.auto.value.AutoOneOf;

@AutoOneOf(IrAddr.AddrKind.class)
public abstract class IrAddr {
    IrAddr() {}

    public enum AddrKind {
        REL_SP,
        ABS_DATA,
        ABS_STACK,
    }

    public abstract AddrKind kind();

    /** Returns the offset relative to the current stack pointer value. */
    public abstract int relSp();

    /** Returns the address of the data cell in real memory space; i.e. the x'th data cell is mapped to -x. */
    public abstract int absData();

    /** Returns the absolute address in the stack space. */
    public abstract int absStack();

    public static IrAddr relSp(int addr) {
        return AutoOneOf_IrAddr.relSp(addr);
    }

    public static IrAddr dataCell(int cellIdx) {
        return AutoOneOf_IrAddr.absData(-cellIdx-1);
    }

    public static IrAddr absStack(int addr) {
        return AutoOneOf_IrAddr.absStack(addr);
    }

    @Override
    public String toString() {
        return switch (kind()) {
            case ABS_DATA -> "DT[" + absData() + "]";
            case ABS_STACK -> "[" + absStack() +"]";
            case REL_SP -> "[SP" + (relSp() < 0 ? "":"+") + relSp() + "]";
        };
    }

}