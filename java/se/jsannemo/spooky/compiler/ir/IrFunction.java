package se.jsannemo.spooky.compiler.ir;

import se.jsannemo.spooky.compiler.ir.IrStatement.IrLabel;

import java.util.ArrayList;

public final class IrFunction {

  public final ArrayList<IrType> paramSignature = new ArrayList<>();
  public IrType returnSignature;

  public boolean extern;
  public IrIpAddr address; // In global address space.
  public final ArrayList<IrStatement> body = new ArrayList<>();
  public int labels = 0;
  public int retValue;
  public int retAddress;
  public boolean isMain;

  @Override
  public String toString() {
    return "IrFunction{"
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

  public IrLabel newLabel() {
    return IrStatement.IrLabel.of(labels++);
  }

  public void newStatement(IrStatement st) {
    body.add(st);
  }
}
