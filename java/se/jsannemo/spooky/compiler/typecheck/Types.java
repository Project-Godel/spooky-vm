package se.jsannemo.spooky.compiler.typecheck;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import se.jsannemo.spooky.compiler.Prog;
import se.jsannemo.spooky.compiler.ast.Ast;

public final class Types {
  public static final Prog.Type VOID =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.VOID).build();
  public static final Prog.Type ERROR =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.ERROR).build();
  public static final Prog.Type BOOLEAN =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.BOOLEAN).build();
  public static final Prog.Type INT =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.INT).build();
  public static final Prog.Type STRING =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.STRING).build();
  public static final Prog.Type CHAR =
      Prog.Type.newBuilder().setBuiltin(Prog.Type.Builtin.CHAR).build();

  private static final ImmutableSet<Prog.Type> COMPARABLE = ImmutableSet.of(BOOLEAN, INT, CHAR);

  private static final ImmutableBiMap<String, Prog.Type.Builtin> TYPE_NAMES =
      ImmutableBiMap.of(
          "Int", Prog.Type.Builtin.INT,
          "Boolean", Prog.Type.Builtin.BOOLEAN,
          "Char", Prog.Type.Builtin.CHAR,
          "String", Prog.Type.Builtin.STRING);

  public static Optional<Prog.Type> builtin(Ast.Type astType) {
    Prog.Type.Builtin builtin = TYPE_NAMES.get(astType.getName());
    if (builtin == null) {
      return Optional.empty();
    }
    Prog.Type builtinType = Prog.Type.newBuilder().setBuiltin(builtin).build();
    if (astType.getDimensionsCount() > 0) {
      return Optional.of(arrayify(builtinType, astType.getDimensionsList()));
    }
    return Optional.of(builtinType);
  }

  private static Prog.Type arrayify(Prog.Type type, List<Ast.Type.ArrayDimension> dim) {
    if (dim.isEmpty()) {
      return type;
    }
    Prog.Type.Array.Builder array = Prog.Type.Array.newBuilder().setArrayOf(type);
    dim.forEach(d -> array.addDimensions(d.getDimension()));
    return Prog.Type.newBuilder().setArray(array).build();
  }

  public static boolean isAssignable(Prog.Type to, Prog.Type from) {
    if (to.equals(Types.ERROR) || from.equals(Types.ERROR)) {
      return true;
    }
    return to.equals(from);
  }

  public static boolean isCopyable(Prog.Type type) {
    return true;
  }

  private static boolean isStruct(Prog.Type type) {
    return type.getTypeCase() == Prog.Type.TypeCase.STRUCT;
  }

  private static boolean isArray(Prog.Type type) {
    return type.getTypeCase() == Prog.Type.TypeCase.ARRAY;
  }

  public static boolean isVoid(Prog.Type returnType) {
    return returnType.getBuiltin() == Prog.Type.Builtin.VOID;
  }

  public static Prog.Type resolve(Ast.Type type, TypeChecker tc) {
    Optional<Prog.Type> builtin = builtin(type);
    if (builtin.isPresent()) {
      return builtin.get();
    }
    if (tc.structs.containsKey(type.getName())) {
      TypeChecker.StructData structData = tc.structs.get(type.getName());
      return arrayify(structData.type, type.getDimensionsList());
    }
    return ERROR;
  }

  public static Prog.Type struct(int index) {
    return Prog.Type.newBuilder().setStruct(index).build();
  }

  public static boolean hasBuiltin(String type) {
    return TYPE_NAMES.containsKey(type);
  }

  public static boolean isComparable(Prog.Type left, Prog.Type right) {
    if (left.equals(ERROR) || right.equals(ERROR)) {
      return true;
    }
    return COMPARABLE.contains(left) && left.equals(right);
  }

  public static String asString(Prog.Type type, TypeChecker tc) {
    if (type.equals(Types.ERROR)) {
      return "<error type>";
    }
    switch (type.getTypeCase()) {
      case BUILTIN:
        return TYPE_NAMES.inverse().get(type.getBuiltin());
      case STRUCT:
        return tc.structsIdx.get(type.getStruct()).name;
      case ARRAY:
        return asString(type.getArray().getArrayOf(), tc)
            + type.getArray().getDimensionsList().stream()
                .map(d -> "[" + (d > 0 ? d : "") + "]")
                .collect(Collectors.joining());
      default:
        return "<UNSET TYPE: THIS IS A BUG>";
    }
  }

  public static Prog.Type subarray(Prog.Type type) {
    if (type.equals(Types.ERROR)) {
      return Types.ERROR;
    }
    Prog.Type.Array array = type.getArray();
    if (array.getDimensionsCount() == 1) {
      return array.getArrayOf();
    }
    List<Integer> dims = array.getDimensionsList();
    return Prog.Type.newBuilder()
        .setArray(
            Prog.Type.Array.newBuilder()
                .setArrayOf(array.getArrayOf())
                .addAllDimensions(dims.subList(1, dims.size())))
        .build();
  }

  public static long arraySize(Prog.Type type) {
    long size = 1;
    for (Integer dim : type.getArray().getDimensionsList()) {
      if (dim == 0) {
        return 0;
      }
      if (size * dim >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
      size *= dim;
    }
    return size;
  }

  public static Prog.Type inheritDims(Prog.Type typeToInfer, Prog.Type inferredType) {
    checkArgument(typeToInfer.hasArray() && inferredType.hasArray());
    Prog.Type.Array.Builder array =
        typeToInfer
            .getArray()
            .toBuilder()
            .clearDimensions()
            .addAllDimensions(inferredType.getArray().getDimensionsList());
    return typeToInfer.toBuilder().setArray(array).build();
  }

  public static Optional<Prog.Type> unify(Prog.Type left, Prog.Type right) {
    if (left.equals(ERROR) || right.equals(ERROR)) {
      return Optional.of(ERROR);
    }
    if (!left.equals(right)) {
      return Optional.empty();
    }
    return Optional.of(left);
  }
}
