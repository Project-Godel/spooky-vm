package se.jsannemo.spooky.compiler.ir;

import com.google.common.collect.ImmutableList;
import java.util.List;
import se.jsannemo.spooky.compiler.Ir;
import se.jsannemo.spooky.compiler.Prog;

public final class IrGen {

  private static final Ir.Type INT_TYPE = Ir.Type.newBuilder().setScalar(Ir.Scalar.INT).build();
  private static final Ir.Type CHAR_TYPE = INT_TYPE;
  private static final Ir.Type BOOL_TYPE = INT_TYPE;
  private static final Ir.Type STRING_STORAGE =
      Ir.Type.newBuilder().setPrefixArray(CHAR_TYPE).build();
  private static final Ir.Type STRING_TYPE =
      Ir.Type.newBuilder().setPointer(STRING_STORAGE).build();

  private final Ir.Program.Builder program = Ir.Program.newBuilder();
  private ImmutableList<Ir.Type> structs;

  private IrGen() {}

  private Ir.Program generate(Prog.Program ast) {
    registerStructs(ast.getStructsList());
    generateGlobals(ast);
    return program.build();
  }

  private void registerStructs(List<Prog.Struct> structsList) {
    ImmutableList.Builder<Ir.Type> structs = ImmutableList.builder();
    structsList.forEach(t -> structs.add(irStruct(t)));
    this.structs = structs.build();
  }

  private void generateGlobals(Prog.Program ast) {
    ast.getGlobalsList().forEach(t -> program.addGlobalSlots(irType(t)));
  }

  private Ir.Type irStruct(Prog.Struct astStruct) {
    Ir.Type.Builder type = Ir.Type.newBuilder();
    Ir.Struct.Builder struct = type.getStructBuilder();
    // The AST structure list is dependency ordered, so registering the IR structs in the same order
    // is fine.
    astStruct.getFieldsList().forEach(t -> struct.addFields(irType(t)));
    return type.build();
  }

  private Ir.Type irType(Prog.Type t) {
    if (t.hasArray()) {
      Prog.Type.Array array = t.getArray();
      Ir.Type base = irType(array.getArrayOf());
      for (int i = array.getDimensionsCount() - 1; i >= 0; i--) {
        base =
            Ir.Type.newBuilder()
                .setArray(Ir.Array.newBuilder().setSize(array.getDimensions(i)).setType(base))
                .build();
      }
      return base;
    } else if (t.getTypeCase() == Prog.Type.TypeCase.STRUCT) {
      return structs.get(t.getStruct());
    } else {
      Prog.Type.Builtin builtin = t.getBuiltin();
      switch (builtin) {
        case BOOLEAN:
          return BOOL_TYPE;
        case CHAR:
          return CHAR_TYPE;
        case INT:
          return INT_TYPE;
        case STRING:
          return STRING_TYPE;
        default:
          throw new IllegalArgumentException();
      }
    }
  }

  public static Ir.Program generateIr(Prog.Program program) {
    return new IrGen().generate(program);
  }
}
