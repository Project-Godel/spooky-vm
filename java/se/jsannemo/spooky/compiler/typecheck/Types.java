package se.jsannemo.spooky.compiler.typecheck;

import com.google.common.collect.ImmutableBiMap;
import se.jsannemo.spooky.compiler.Prog;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.Optional;

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

  private static final ImmutableBiMap<String, Prog.Type.Builtin> TYPE_NAMES =
      ImmutableBiMap.of(
          "Int", Prog.Type.Builtin.INT,
          "Boolean", Prog.Type.Builtin.BOOLEAN,
          "Char", Prog.Type.Builtin.CHAR,
          "String", Prog.Type.Builtin.STRING);

  public static Optional<Prog.Type> builtin(Ast.Type type) {
    Prog.Type.Builtin builtin = TYPE_NAMES.get(type.getName());
    if (builtin == null) {
      return Optional.empty();
    }
    return Optional.of(
        arrayify(Prog.Type.newBuilder().setBuiltin(builtin).build(), type.getDimension()));
  }

  private static Prog.Type arrayify(Prog.Type type, int dim) {
    for (int i = 0; i < dim; i++) {
      type = Prog.Type.newBuilder().setArray(type).build();
    }
    return type;
  }

  public static Prog.Type resolve(Ast.Type type, TypeChecker tc) {
    Optional<Prog.Type> builtin = builtin(type);
    if (builtin.isPresent()) {
      return builtin.get();
    }
    if (tc.structs.containsKey(type.getName())) {
      return arrayify(tc.structs.get(type.getName()).type, type.getDimension());
    }
    return ERROR;
  }

  public static Prog.Type struct(int index) {
    return Prog.Type.newBuilder().setStruct(index).build();
  }

  public static String asString(Prog.Type type, TypeChecker tc) {
    switch (type.getTypeCase()) {
      case BUILTIN:
        return TYPE_NAMES.inverse().get(type.getBuiltin());
      case STRUCT:
        return tc.structNames.get(type.getStruct());
      case ARRAY:
        return asString(type.getArray(), tc) + "[]";
      default:
        return "<error>";
    }
  }
}
