package se.jsannemo.spooky.compiler.codegen;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
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

public final class CodeGen {

    private CodeGen() {}

    /** Generates a list of Spooky VM instructionx executing {@code program}. */
    public static List<Instructions.Instruction> codegen(String programName, IrProgram program) {
        ArrayList<Instructions.Instruction> code = new ArrayList<>();
        code.add(Instructions.BinDef.create(programName));
        code.add(Instructions.Text.create());

        // New list for instructions to ensure that in-function instruction addressing is correct.
        // It is absolute within the text segment, not the entire binary.
        ArrayList<Instructions.Instruction> ins  = new ArrayList<>();
        addPreamble(ins);

        HashMap<Integer, String> funcLabelFills = new HashMap<>();
        HashMap<String, Integer> funcAddresses = new HashMap<>();
        // Main must be exported first, since execution starts from IP = 0.
        program.functions.forEach((name, func) -> {
            if (name.equals("main")) {
                funcAddresses.put(name, ins.size());
                function(func, ins, funcLabelFills);
            }
        });
        program.functions.forEach((name, func) -> {
            if (!name.equals("main")) {
                funcAddresses.put(name, ins.size());
                function(func, ins, funcLabelFills);
            }
        });

        for (Entry<Integer, String> label : funcLabelFills.entrySet()) {
            Instructions.Jump jum = (Jump) ins.get(label.getKey());
            ins.set(label.getKey(), Instructions.Jump.create(jum.flag(), funcAddresses.get(label.getValue())));
        }

        code.addAll(ins);
        code.add(Instructions.Data.create(ImmutableList.of(0)));
        return code;
    }

    private static void addPreamble(ArrayList<Instructions.Instruction> code) {
        // Reserve stack slot for the stack pointer.
        code.add(Instructions.Const.create(Conventions.NEXT_STACK.absStack(), addressTo(Conventions.STACK_POINTER)));
    }

    private static void function(IrFunction func, ArrayList<Instruction> code,
        HashMap<Integer, String> funcLabelFills) {
        func.address = IrIpAddr.absText(code.size());
        HashMap<IrLabel, Integer> labelAddresses = new HashMap<>();
        HashMap<Integer, IrLabel> labelFills = new HashMap<>();
        for (IrStatement st : func.body) {
            if (st instanceof IrStatement.IrHalt) {
                code.add(Instructions.Halt.create());
            } else if (st instanceof IrStatement.IrExtern extern) {
                // New stack pointer
                code.add(Instructions.Const.create(extern.spOffset(), addressTo(Conventions.REG_1)));
                code.add(Instructions.Add.create(addressTo(Conventions.STACK_POINTER), addressTo(Conventions.REG_1), addressTo(Conventions.STACK_POINTER)));
                code.add(Instructions.Extern.create(extern.name()));
                code.add(Instructions.Const.create(extern.spOffset(), addressTo(Conventions.REG_1)));
                code.add(Instructions.Sub.create(addressTo(Conventions.STACK_POINTER), addressTo(Conventions.REG_1), addressTo(Conventions.STACK_POINTER)));
            } else if (st instanceof IrStatement.IrStore store) {
                code.add(Instructions.Const.create(store.value(), addressTo(store.addr())));
            } else if (st instanceof IrStatement.IrStoreLabel store) {
                labelFills.put(code.size(), store.label());
                code.add(Instructions.Const.create(-1, addressTo(store.addr())));
            } else if (st instanceof IrStatement.IrAdd add) {
                code.add(Instructions.Add.create(addressTo(add.a()), addressTo(add.b()), addressTo(add.result())));
            } else if (st instanceof IrStatement.IrSub sub) {
                code.add(Instructions.Sub.create(addressTo(sub.a()), addressTo(sub.b()), addressTo(sub.result())));
            } else if (st instanceof IrStatement.IrMul mul) {
                code.add(Instructions.Mul.create(addressTo(mul.a()), addressTo(mul.b()), addressTo(mul.result())));
            } else if (st instanceof IrStatement.IrDiv div) {
                code.add(Instructions.Div.create(addressTo(div.a()), addressTo(div.b()), addressTo(div.result())));
            } else if (st instanceof IrStatement.IrMod mod) {
                code.add(Instructions.Mod.create(addressTo(mod.a()), addressTo(mod.b()), addressTo(mod.result())));
            } else if (st instanceof IrStatement.IrCopy copy) {
                code.add(Instructions.Move.create(addressTo(copy.from()), addressTo(copy.to())));
            } else if (st instanceof IrJmpZero jmp) {
                labelFills.put(code.size(), jmp.label());
                code.add(Instructions.Jump.create(addressTo(jmp.flag()), -1));
            } else if (st instanceof IrJmpAdr jmp) {
                code.add(Instructions.JumpAddress.create(addressTo(jmp.addr())));
            } else if (st instanceof IrStatement.IrLabel label) {
                labelAddresses.put(label, code.size());
            } else if (st instanceof IrStatement.IrLessThan lt) {
                code.add(Instructions.LessThan.create(addressTo(lt.a()), addressTo(lt.b()), addressTo(lt.result())));
            } else if (st instanceof IrStatement.IrLessEquals leq) {
                code.add(Instructions.LessEquals.create(addressTo(leq.a()), addressTo(leq.b()), addressTo(leq.result())));
            } else if (st instanceof IrStatement.IrEquals eq) {
                code.add(Instructions.Equals.create(addressTo(eq.a()), addressTo(eq.b()), addressTo(eq.result())));
            } else if (st instanceof IrStatement.IrCall call) {
                code.add(Instructions.Const.create(call.spOffset(), addressTo(Conventions.REG_1)));
                code.add(Instructions.Add.create(addressTo(Conventions.STACK_POINTER), addressTo(Conventions.REG_1), addressTo(Conventions.STACK_POINTER)));

                funcLabelFills.put(code.size(), call.name());
                code.add(Instructions.Jump.create(addressTo(Conventions.CONST_ZERO), -1));
                labelAddresses.put(call.jumpAfter(), code.size());

                code.add(Instructions.Const.create(call.spOffset(), addressTo(Conventions.REG_1)));
                code.add(Instructions.Sub.create(addressTo(Conventions.STACK_POINTER), addressTo(Conventions.REG_1), addressTo(Conventions.STACK_POINTER)));
            } else {
                throw new UnsupportedOperationException("Unhandled IR: " + st);
            }
        }
        for (Entry<Integer, IrLabel> label : labelFills.entrySet()) {
            Instructions.Instruction ins = code.get(label.getKey());
            if (ins instanceof Jump jum) {
                code.set(label.getKey(), Instructions.Jump.create(jum.flag(), labelAddresses.get(label.getValue())));
            } else if (ins instanceof Const cnst) {
                code.set(label.getKey(), Instructions.Const.create(labelAddresses.get(label.getValue()), cnst.target()));
            }
        }
    }

    private static Address addressTo(IrAddr addr) {
        return switch (addr.kind()) {
            case REL_SP -> Address.baseAndOffset(Conventions.STACK_POINTER.absStack(), addr.relSp());
            case ABS_DATA -> Address.baseAndOffset(Conventions.CONST_ZERO.absData(), addr.absData());
            case ABS_STACK -> Address.baseAndOffset(Conventions.CONST_ZERO.absData(), addr.absStack());
        };
    }

}
