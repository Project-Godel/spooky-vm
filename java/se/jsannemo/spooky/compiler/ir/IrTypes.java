package se.jsannemo.spooky.compiler.ir;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.AbstractMap;
import java.util.Map;

public final class IrTypes {
  public static final ImmutableSet<String> TYPES = ImmutableSet.of("Int", "Void");
  public static final ImmutableMap<String, Map.Entry<String, Integer>> ALIAS =
      ImmutableMap.of(
          "String", new AbstractMap.SimpleEntry<>("Char", 1),
          "Char", new AbstractMap.SimpleEntry<>("Int", 0),
          "Boolean", new AbstractMap.SimpleEntry<>("Int", 0));

  public static final Ir.Type INT = fromName("Int", 0);
  public static final Ir.Type CHAR = fromName("Char", 0);
  public static final Ir.Type BOOL = fromName("Boolean", 0);
  public static final Ir.Type VOID = fromName("Void", 0);

  public static Ir.Type fromName(String name, int dim) {
    String baseType = name;
    String typeCheckingType = baseType;
    while (ALIAS.containsKey(baseType)) {
      Map.Entry<String, Integer> aliased = ALIAS.get(baseType);
      baseType = aliased.getKey();
      dim += aliased.getValue();
    }
    if (!TYPES.contains(baseType)) {
      // TODO: this should be in the typechecking stage
      throw new IllegalArgumentException("Invalid base type: " + name);
    }
    return Ir.Type.newBuilder()
        .setBaseType(baseType)
        .setDim(dim)
        .setTypeCheckType(typeCheckingType)
        .build();
  }

  public static Ir.Type fromAst(Ast.Type type) throws ValidationException {
    return fromName(type.getName(), 0);
  }

  public static int memSize(Ir.Type type) {
    if (type.getBaseType().equals(VOID.getBaseType())) {
      return 0;
    }
    return 1;
  }

  public static boolean typeChecks(Ir.Type a, Ir.Type b) {
    return a.getTypeCheckType().equals(b.getTypeCheckType()) && a.getDim() == b.getDim();
  }
}
