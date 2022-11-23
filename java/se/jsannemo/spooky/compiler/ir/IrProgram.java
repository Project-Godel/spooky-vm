package se.jsannemo.spooky.compiler.ir;

import java.util.HashMap;

public final class IrProgram {
  public final HashMap<String, IrFunction> functions = new HashMap<>();

  IrProgram() {}

  @Override
  public String toString() {
    return "IrProgram{" + "functions=" + functions + '}';
  }
}
