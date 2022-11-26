package se.jsannemo.spooky.compiler.codegen;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import jsinterop.annotations.JsMethod;
import se.jsannemo.spooky.compiler.ir.IrAddr;
import se.jsannemo.spooky.compiler.ir.IrFunction;
import se.jsannemo.spooky.compiler.ir.IrIpAddr;
import se.jsannemo.spooky.compiler.ir.IrProgram;
import se.jsannemo.spooky.compiler.ir.IrStatement;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrJmpAdr;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrJmpZero;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrLabel;
import se.jsannemo.spooky.vm.code.Instructions;
import se.jsannemo.spooky.vm.code.Instructions.Address;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.JumpN;

public final class CodeGen {

  private CodeGen() {}

  /** Generates a list of Spooky VM instructions executing {@code program}. */
  @JsMethod
  public static List<Instructions.Instruction> codegen(String programName, IrProgram program) {
    ArrayList<Instructions.Instruction> code = new ArrayList<>();
    code.add(Instructions.BinDef.create(programName));
    code.add(Instructions.Text.create());

    // New list for instructions to ensure that in-function instruction addressing is correct.
    // It is absolute within the text segment, not the entire binary.
    ArrayList<Instructions.Instruction> ins = new ArrayList<>();
    addPreamble(ins);

    HashMap<Integer, String> funcLabelFills = new HashMap<>();
    HashMap<String, Integer> funcAddresses = new HashMap<>();
    // __init__ must be exported first, then main, since execution starts from IP = 0.
    program.functions.forEach(
        (name, func) -> {
          if (name.equals("__init__")) {
            funcAddresses.put(name, ins.size());
            function(func, ins, funcLabelFills);
          }
        });
    program.functions.forEach(
        (name, func) -> {
          if (name.equals("main")) {
            funcAddresses.put(name, ins.size());
            function(func, ins, funcLabelFills);
          }
        });
    program.functions.forEach(
        (name, func) -> {
          if (!name.equals("main") && !name.equals("__init__")) {
            funcAddresses.put(name, ins.size());
            function(func, ins, funcLabelFills);
          }
        });

    for (Entry<Integer, String> label : funcLabelFills.entrySet()) {
      Instructions.Jump jum = (Jump) ins.get(label.getKey());
      ins.set(
          label.getKey(),
          Instructions.Jump.create(jum.flag(), funcAddresses.get(label.getValue())));
    }

    code.addAll(ins);
    code.add(Instructions.Data.create(ImmutableList.of(0, 1)));
    return code;
  }

  private static void addPreamble(ArrayList<Instructions.Instruction> code) {
    // Reserve stack slot for the stack pointer.
    code.add(
        Instructions.Const.create(IrAddr.NEXT_STACK.absStack(), addressTo(IrAddr.STACK_POINTER)));
  }

  private static void function(
      IrFunction func, ArrayList<Instruction> code, HashMap<Integer, String> funcLabelFills) {
    func.address = IrIpAddr.absText(code.size());
    HashMap<IrLabel, Integer> labelAddresses = new HashMap<>();
    HashMap<Integer, IrLabel> labelFills = new HashMap<>();
    for (IrStatement st : func.body) {
      if (st instanceof IrStatement.IrHalt) {
        code.add(Instructions.Halt.create());
      } else if (st instanceof IrStatement.IrExtern) {
        // New stack pointer. Optimize changes for no-arg calls.
        IrStatement.IrExtern extern = (IrStatement.IrExtern) st;
        if (extern.spOffset() != 0) {
          code.add(Instructions.Const.create(extern.spOffset(), addressTo(IrAddr.REG_1)));
          code.add(
              Instructions.Add.create(
                  addressTo(IrAddr.STACK_POINTER),
                  addressTo(IrAddr.REG_1),
                  addressTo(IrAddr.STACK_POINTER)));
        }
        code.add(Instructions.Extern.create(extern.name()));
        if (extern.spOffset() != 0) {
          code.add(Instructions.Const.create(extern.spOffset(), addressTo(IrAddr.REG_1)));
          code.add(
              Instructions.Sub.create(
                  addressTo(IrAddr.STACK_POINTER),
                  addressTo(IrAddr.REG_1),
                  addressTo(IrAddr.STACK_POINTER)));
        }
      } else if (st instanceof IrStatement.IrStore) {
        IrStatement.IrStore store = (IrStatement.IrStore) st;
        code.add(Instructions.Const.create(store.value(), addressTo(store.addr())));
      } else if (st instanceof IrStatement.IrStoreLabel) {
        IrStatement.IrStoreLabel store = (IrStatement.IrStoreLabel) st;
        labelFills.put(code.size(), store.label());
        code.add(Instructions.Const.create(-1, addressTo(store.addr())));
      } else if (st instanceof IrStatement.IrAdd) {
        IrStatement.IrAdd add = (IrStatement.IrAdd) st;

        code.add(
            Instructions.Add.create(
                addressTo(add.a()), addressTo(add.b()), addressTo(add.result())));
      } else if (st instanceof IrStatement.IrSub) {
        IrStatement.IrSub sub = (IrStatement.IrSub) st;

        code.add(
            Instructions.Sub.create(
                addressTo(sub.a()), addressTo(sub.b()), addressTo(sub.result())));
      } else if (st instanceof IrStatement.IrMul) {
        IrStatement.IrMul mul = (IrStatement.IrMul) st;
        code.add(
            Instructions.Mul.create(
                addressTo(mul.a()), addressTo(mul.b()), addressTo(mul.result())));
      } else if (st instanceof IrStatement.IrDiv) {
        IrStatement.IrDiv div = (IrStatement.IrDiv) st;
        code.add(
            Instructions.Div.create(
                addressTo(div.a()), addressTo(div.b()), addressTo(div.result())));
      } else if (st instanceof IrStatement.IrMod) {
        IrStatement.IrMod mod = (IrStatement.IrMod) st;
        code.add(
            Instructions.Mod.create(
                addressTo(mod.a()), addressTo(mod.b()), addressTo(mod.result())));
      } else if (st instanceof IrStatement.IrCopy) {
        IrStatement.IrCopy copy = (IrStatement.IrCopy) st;
        code.add(Instructions.Move.create(addressTo(copy.from()), addressTo(copy.to())));
      } else if (st instanceof IrJmpZero) {
        IrJmpZero jmp = (IrJmpZero) st;
        labelFills.put(code.size(), jmp.label());
        code.add(Instructions.Jump.create(addressTo(jmp.flag()), -1));
      } else if (st instanceof IrStatement.IrJmpNZero) {
        IrStatement.IrJmpNZero jmp = (IrStatement.IrJmpNZero) st;
        labelFills.put(code.size(), jmp.label());
        code.add(Instructions.JumpN.create(addressTo(jmp.flag()), -1));
      } else if (st instanceof IrStatement.IrJmp) {
        IrStatement.IrJmp jmp = (IrStatement.IrJmp) st;
        labelFills.put(code.size(), jmp.label());
        code.add(Instructions.Jump.create(addressTo(IrAddr.CONST_ZERO), -1));
      } else if (st instanceof IrJmpAdr) {
        IrJmpAdr jmp = (IrJmpAdr) st;
        code.add(Instructions.JumpAddress.create(addressTo(jmp.addr())));
      } else if (st instanceof IrStatement.IrLabel) {
        IrLabel label = (IrLabel) st;
        labelAddresses.put(label, code.size());
      } else if (st instanceof IrStatement.IrLessThan) {
        IrStatement.IrLessThan lt = (IrStatement.IrLessThan) st;
        code.add(
            Instructions.LessThan.create(
                addressTo(lt.a()), addressTo(lt.b()), addressTo(lt.result())));
      } else if (st instanceof IrStatement.IrLessEquals) {
        IrStatement.IrLessEquals leq = (IrStatement.IrLessEquals) st;
        code.add(
            Instructions.LessEquals.create(
                addressTo(leq.a()), addressTo(leq.b()), addressTo(leq.result())));
      } else if (st instanceof IrStatement.IrEquals) {
        IrStatement.IrEquals eq = (IrStatement.IrEquals) st;
        code.add(
            Instructions.Equals.create(
                addressTo(eq.a()), addressTo(eq.b()), addressTo(eq.result())));
      } else if (st instanceof IrStatement.IrNotEquals) {
        IrStatement.IrNotEquals neq = (IrStatement.IrNotEquals) st;
        code.add(
            Instructions.NotEquals.create(
                addressTo(neq.a()), addressTo(neq.b()), addressTo(neq.result())));
      } else if (st instanceof IrStatement.IrCall) {
        // New stack pointer. Optimize changes for no-arg calls.
        IrStatement.IrCall call = (IrStatement.IrCall) st;

        if (call.spOffset() != 0) {
          code.add(Instructions.Const.create(call.spOffset(), addressTo(IrAddr.REG_1)));
          code.add(
              Instructions.Add.create(
                  addressTo(IrAddr.STACK_POINTER),
                  addressTo(IrAddr.REG_1),
                  addressTo(IrAddr.STACK_POINTER)));
        }
        funcLabelFills.put(code.size(), call.name());
        // Placeholder address; the real address is filled in once all functions are laid out.
        code.add(Instructions.Jump.create(addressTo(IrAddr.CONST_ZERO), -1));
        labelAddresses.put(call.jumpAfter(), code.size());
        if (call.spOffset() != 0) {
          code.add(Instructions.Const.create(call.spOffset(), addressTo(IrAddr.REG_1)));
          code.add(
              Instructions.Sub.create(
                  addressTo(IrAddr.STACK_POINTER),
                  addressTo(IrAddr.REG_1),
                  addressTo(IrAddr.STACK_POINTER)));
        }
      } else if (st instanceof IrStatement.IrBitAnd) {
        IrStatement.IrBitAnd bitAnd = (IrStatement.IrBitAnd) st;
        code.add(
            Instructions.BitAnd.create(
                addressTo(bitAnd.a()), addressTo(bitAnd.b()), addressTo(bitAnd.result())));
      } else if (st instanceof IrStatement.IrBitOr) {
        IrStatement.IrBitOr bitOr = (IrStatement.IrBitOr) st;
        code.add(
            Instructions.BitOr.create(
                addressTo(bitOr.a()), addressTo(bitOr.b()), addressTo(bitOr.result())));
      } else {
        throw new UnsupportedOperationException("Unhandled IR: " + st);
      }
    }
    for (Entry<Integer, IrLabel> label : labelFills.entrySet()) {
      Instructions.Instruction ins = code.get(label.getKey());
      if (ins instanceof Jump) {
        Jump jum = (Jump) ins;
        code.set(
            label.getKey(),
            Instructions.Jump.create(jum.flag(), labelAddresses.get(label.getValue())));
      } else if (ins instanceof JumpN) {
        JumpN jum = (JumpN) ins;
        code.set(
            label.getKey(),
            Instructions.JumpN.create(jum.flag(), labelAddresses.get(label.getValue())));
      } else if (ins instanceof Const) {
        Const cnst = (Const) ins;
        code.set(
            label.getKey(),
            Instructions.Const.create(labelAddresses.get(label.getValue()), cnst.target()));
      }
    }
  }

  private static Address addressTo(IrAddr addr) {
    switch (addr.kind()) {
      case REL_SP:
        return Address.baseAndOffset(IrAddr.STACK_POINTER.absStack(), addr.relSp());
      case ABS_DATA:
        return Address.baseAndOffset(IrAddr.CONST_ZERO.absData(), addr.absData());
      case ABS_STACK:
        return Address.baseAndOffset(IrAddr.CONST_ZERO.absData(), addr.absStack());
      default:
        throw new IllegalArgumentException();
    }
  }
}
