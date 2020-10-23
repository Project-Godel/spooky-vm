package se.jsannemo.spooky.compiler.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class IrProgram {
  public final HashMap<String, IrFunction> functions = new HashMap<>();
  public final List<Integer> data = new ArrayList<>(Arrays.asList(0));
  
  IrProgram() {}

  @Override
  public String toString() {
    return "IrProgram{" + "functions=" + functions + '}';
  }
}
