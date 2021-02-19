package se.jsannemo.spooky.compiler.ir;

public final class IrAddrs {

  public static Ir.Addr absStack(int i) {
    return Ir.Addr.newBuilder().setAbsStack(i).build();
  }

  public static Ir.Addr dataCell(int i) {
    return Ir.Addr.newBuilder().setAbsData(i).build();
  }

  public static Ir.IpAddr absText(int i) {
    return Ir.IpAddr.newBuilder().setAbsText(i).build();
  }

  public static Ir.Addr relSp(int i) {
    return Ir.Addr.newBuilder().setRelSp(i).build();
  }
}
