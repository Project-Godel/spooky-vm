package se.jsannemo.spooky.compiler.ir;

import java.util.ArrayList;

public final class IrFunction {

  public final ArrayList<Ir.Type> paramSignature = new ArrayList<>();
  public Ir.Type returnSignature;

  public boolean extern;
  public Ir.IpAddr address; // In global address space.
  public final ArrayList<Ir.Statement.Builder> body = new ArrayList<>();
  public int labels = 0;
  public int retValue;
  public int retAddress;
  public boolean isMain;

  @Override
  public String toString() {
    return "IrFunction{"
            + (extern ? "extern " : "")
        + "paramSignature="
        + paramSignature
        + ", returnSignature="
        + returnSignature
        + ", address="
        + address
        + ", body="
        + body
        + ", labels="
        + labels
        + '}';
  }

  public Ir.Label newLabel() {
    return Ir.Label.newBuilder().setLabel(labels++).build();
  }

  public Ir.Statement.Builder newStatement() {
    Ir.Statement.Builder b = Ir.Statement.newBuilder();
    body.add(b);
    return b;
  }
}
