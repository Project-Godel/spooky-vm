package se.jsannemo.spooky.compiler.ir;

import com.google.auto.value.AutoOneOf;

@AutoOneOf(IrIpAddr.AddrKind.class)
public abstract class IrIpAddr {
  IrIpAddr() {}

  public enum AddrKind {
    ABS_TEXT, // Absolute; TEXT space
  }
  public abstract AddrKind kind();

  public abstract int absText();

  public static IrIpAddr absText(int addr) {
    return AutoOneOf_IrIpAddr.absText(addr);
  }

  @Override
  public String toString() {
    return switch (kind()) {
      case ABS_TEXT -> "TX[" + absText() + "]";
    };
  }

}
