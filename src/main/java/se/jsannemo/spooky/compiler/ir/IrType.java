package se.jsannemo.spooky.compiler.ir;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.compiler.ast.TypeName;

public class IrType {
  public static final ImmutableSet<String> TYPES = ImmutableSet.of("Int", "Void");
  public static final ImmutableMap<String, Map.Entry<String, Integer>> ALIAS =
      ImmutableMap.of(
          "String", new AbstractMap.SimpleEntry<>("Char", 1),
          "Char", new AbstractMap.SimpleEntry<>("Int", 0),
          "Boolean", new AbstractMap.SimpleEntry<>("Int", 0));

  public static final IrType INT = new IrType("Int", 0);
  public static final IrType BOOL = new IrType("Boolean", 0);
  public static final IrType VOID = new IrType("Void", 0);

  public final String baseType;
  public final int dim;

  private IrType(String baseType, int dim) {
    while (ALIAS.containsKey(baseType)) {
      Map.Entry<String, Integer> aliased = ALIAS.get(baseType);
      baseType = aliased.getKey();
      dim += aliased.getValue();
    }

    this.baseType = baseType;
    this.dim = dim;
    if (!TYPES.contains(baseType)) {
      throw new IllegalArgumentException("Invalid type: " + baseType);
    }
  }

  public static IrType fromTypeName(TypeName typeName) throws ValidationException {
    try {
      return new IrType(typeName.name().name(), typeName.dimension());
    } catch (IllegalArgumentException iae) {
      throw new ValidationException(iae.getMessage(), typeName.name().token());
    }
  }

  public int memSize() {
    if (baseType.equals(VOID.baseType)) {
      return 0;
    }
    return 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IrType irType = (IrType) o;
    return dim == irType.dim && Objects.equals(baseType, irType.baseType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseType, dim);
  }

  @Override
  public String toString() {
    return baseType + "[]".repeat(dim);
  }
}
