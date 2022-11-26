package se.jsannemo.spooky.compiler.ir;

import java.util.HashMap;
import jsinterop.annotations.JsConstructor;

public final class IrProgram {
  public final HashMap<String, IrFunction> functions = new HashMap<>();

  @JsConstructor
  IrProgram() {}

  @Override
  public String toString() {
    return "IrProgram{" + "functions=" + functions + '}';
  }
}
