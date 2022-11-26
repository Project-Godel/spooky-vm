package se.jsannemo.spooky.compiler.ir;

import java.util.ArrayList;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrLabel;

public final class IrFunction {

  public final ArrayList<IrType> paramSignature = new ArrayList<>();
  public IrType returnSignature;

  public boolean extern;
  public IrIpAddr address; // In global address space.
  public final ArrayList<IrStatement> body = new ArrayList<>();
  public int labels = 0;

  // Stack pointer offset of where to store the return value of the function.
  public int retValue;

  // Stack pointer-relative address of what instruction address to jump to upon returning.
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
