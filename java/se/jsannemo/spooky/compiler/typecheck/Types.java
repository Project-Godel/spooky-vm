package se.jsannemo.spooky.compiler.typecheck;

import com.google.common.collect.ImmutableMap;
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

  private static final ImmutableMap<String, Prog.Type.Builtin> TYPE_NAMES =
      ImmutableMap.of(
          "Int", Prog.Type.Builtin.INT,
          "Boolean", Prog.Type.Builtin.BOOLEAN,
          "Char", Prog.Type.Builtin.CHAR,
          "String", Prog.Type.Builtin.STRING);

  public static Optional<Prog.Type> builtin(Ast.Type type) {
    Prog.Type.Builtin builtin = TYPE_NAMES.get(type.getName());
    if (builtin == null) {
      return Optional.empty();
    }
    Prog.Type t = Prog.Type.newBuilder().setBuiltin(builtin).build();
    for (int i = 0; i < type.getDimension(); i++) {
      t = Prog.Type.newBuilder().setArray(t).build();
    }
    return Optional.of(t);
  }

  public static String toString(Prog.Type type) {
    return type.toString();
  }
}
