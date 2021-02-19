package se.jsannemo.spooky.compiler.codegen;

import se.jsannemo.spooky.compiler.ir.*;
import se.jsannemo.spooky.vm.Address;
import se.jsannemo.spooky.vm.Executable;
import se.jsannemo.spooky.vm.Instruction;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class CodeGen {

  private CodeGen() {}

  /** Generates a list of Spooky VM instructionx executing {@code program}. */
  public static Executable codegen(IrProgram program) {
    Code code = new Code();
    addPreamble(code);

    HashMap<Integer, String> funcLabelFills = new HashMap<>();
    HashMap<String, Integer> funcAddresses = new HashMap<>();
    // __init__ must be exported first, then main, since execution starts from IP = 0.
    program.functions.forEach(
        (name, func) -> {
          if (name.equals("__init__")) {
            funcAddresses.put(name, code.size());
            function(func, code, funcLabelFills);
          }
        });
    program.functions.forEach(
        (name, func) -> {
          if (name.equals("main")) {
            funcAddresses.put(name, code.size());
            function(func, code, funcLabelFills);
          }
        });
    program.functions.forEach(
        (name, func) -> {
          if (!name.equals("main") && !name.equals("__init__") && !func.extern) {
            funcAddresses.put(name, code.size());
            function(func, code, funcLabelFills);
          }
        });

    for (Entry<Integer, String> label : funcLabelFills.entrySet()) {
      Instruction.Builder jum = code.get(label.getKey());
      jum.getJmpBuilder().setAddr(code.storeData(funcAddresses.get(label.getValue())));
    }
    return code.build();
  }

  private static void addPreamble(Code code) {
    // Reserve stack slot for the stack pointer.
    code.storeData(0);
    code.store(Conventions.NEXT_STACK.getAbsStack(), addressTo(Conventions.STACK_POINTER));
  }

  private static void function(
      IrFunction func, Code code, HashMap<Integer, String> funcLabelFills) {
    func.address = IrAddrs.absText(code.size());
    HashMap<Ir.Label, Integer> labelAddresses = new HashMap<>();
    HashMap<Integer, Ir.Label> labelFills = new HashMap<>();
    HashMap<Integer, Ir.Label> labelDataFills = new HashMap<>();
    for (Ir.Statement.Builder st : func.body) {
      switch (st.getStatementCase()) {
        case EXTERN:
          Ir.Extern extern = st.getExtern();
          // New stack pointer. Optimize changes for no-arg calls.
          if (extern.getSpOffset() != 0) {
            code.store(extern.getSpOffset(), addressTo(Conventions.REG_1));
            code.add()
                .getAddBuilder()
                .setOp1(addressTo(Conventions.STACK_POINTER))
                .setOp2(addressTo(Conventions.REG_1))
                .setTarget(addressTo(Conventions.STACK_POINTER));
          }
          code.add().getExternBuilder().setName(extern.getName());
          if (extern.getSpOffset() != 0) {
            code.store(extern.getSpOffset(), addressTo(Conventions.REG_1));
            code.add()
                .getSubBuilder()
                .setOp1(addressTo(Conventions.STACK_POINTER))
                .setOp2(addressTo(Conventions.REG_1))
                .setTarget(addressTo(Conventions.STACK_POINTER));
          }
          break;
        case HALT:
          code.add().getHaltBuilder();
          break;
        case STORE:
          Ir.Store store = st.getStore();
          code.store(store.getValue(), addressTo(store.getAddr()));
          break;
        case STORE_LABEL:
          Ir.StoreLabel storeLabel = st.getStoreLabel();
          int dataPos = code.dataPlaceholder();
          labelDataFills.put(dataPos, storeLabel.getLabel());
          code.add()
              .getMoveBuilder()
              .setSource(addressTo(IrAddrs.dataCell(dataPos)))
              .setTarget(addressTo(storeLabel.getAddr()));
          break;
        case ADD:
          Ir.Add add = st.getAdd();
          code.add()
              .getAddBuilder()
              .setOp1(addressTo(add.getA()))
              .setOp2(addressTo(add.getB()))
              .setTarget(addressTo(add.getResult()));
          break;
        case SUB:
          Ir.Sub sub = st.getSub();
          code.add()
              .getSubBuilder()
              .setOp1(addressTo(sub.getA()))
              .setOp2(addressTo(sub.getB()))
              .setTarget(addressTo(sub.getResult()));
          break;
        case MUL:
          Ir.Mul mul = st.getMul();
          code.add()
              .getMulBuilder()
              .setOp1(addressTo(mul.getA()))
              .setOp2(addressTo(mul.getB()))
              .setTarget(addressTo(mul.getResult()));
          break;
        case DIV:
          Ir.Div div = st.getDiv();
          code.add()
              .getDivBuilder()
              .setOp1(addressTo(div.getA()))
              .setOp2(addressTo(div.getB()))
              .setTarget(addressTo(div.getResult()));
          break;
        case MOD:
          Ir.Mod mod = st.getMod();
          code.add()
              .getModBuilder()
              .setOp1(addressTo(mod.getA()))
              .setOp2(addressTo(mod.getB()))
              .setTarget(addressTo(mod.getResult()));
          break;
        case COPY:
          Ir.Copy copy = st.getCopy();
          code.add()
              .getMoveBuilder()
              .setSource(addressTo(copy.getFrom()))
              .setTarget(addressTo(copy.getTo()));
          break;
        case JMP_ZERO:
          Ir.JmpZero jmpZ = st.getJmpZero();
          labelFills.put(code.size(), jmpZ.getLabel());
          code.add().getJmpBuilder().setFlag(addressTo(jmpZ.getFlag()));
          break;
        case JMP_NZERO:
          Ir.JmpNZero jmpNZ = st.getJmpNzero();
          labelFills.put(code.size(), jmpNZ.getLabel());
          code.add().getJmpBuilder().setFlag(addressTo(jmpNZ.getFlag())).setNonzero(true);
          break;
        case JMP:
          Ir.Jmp jmp = st.getJmp();
          labelFills.put(code.size(), jmp.getLabel());
          code.add().getJmpBuilder().setFlag(addressTo(Conventions.CONST_ZERO));
          break;
        case JMP_ADDR:
          Ir.JmpAdr jmpAdr = st.getJmpAddr();
          code.add()
              .getJmpBuilder()
              .setFlag(addressTo(Conventions.CONST_ZERO))
              .setAddr(addressTo(jmpAdr.getAddr()));
          break;
        case LABEL:
          Ir.Label label = st.getLabel();
          labelAddresses.put(label, code.size());
          break;
        case LESS_THAN:
          Ir.LessThan lt = st.getLessThan();
          code.add()
              .getLessBuilder()
              .setOp1(addressTo(lt.getA()))
              .setOp2(addressTo(lt.getB()))
              .setTarget(addressTo(lt.getResult()));
          break;
        case LESS_EQUALS:
          Ir.LessEquals leq = st.getLessEquals();
          code.add()
              .getLeqBuilder()
              .setOp1(addressTo(leq.getA()))
              .setOp2(addressTo(leq.getB()))
              .setTarget(addressTo(leq.getResult()));
          break;
        case EQUALS:
          Ir.Equals eq = st.getEquals();
          code.add()
              .getEqBuilder()
              .setOp1(addressTo(eq.getA()))
              .setOp2(addressTo(eq.getB()))
              .setTarget(addressTo(eq.getResult()));
          break;
        case NOT_EQUALS:
          Ir.NotEquals neq = st.getNotEquals();
          code.add()
              .getNeqBuilder()
              .setOp1(addressTo(neq.getA()))
              .setOp2(addressTo(neq.getB()))
              .setTarget(addressTo(neq.getResult()));
          break;
        case CALL:
          Ir.Call call = st.getCall();
          // New stack pointer. Optimize changes for no-arg calls.
          if (call.getSpOffset() != 0) {
            code.add()
                .getAddBuilder()
                .setOp1(addressTo(Conventions.STACK_POINTER))
                .setOp2(code.storeData(call.getSpOffset()))
                .setTarget(addressTo(Conventions.STACK_POINTER));
          }
          funcLabelFills.put(code.size(), call.getName());
          // Placeholder address; the real address is filled in once all functions are laid out.
          code.add().getJmpBuilder().setFlag(addressTo(Conventions.CONST_ZERO));
          labelAddresses.put(call.getLabel(), code.size());
          if (call.getSpOffset() != 0) {
            code.add()
                .getSubBuilder()
                .setOp1(addressTo(Conventions.STACK_POINTER))
                .setOp2(code.storeData(call.getSpOffset()))
                .setTarget(addressTo(Conventions.STACK_POINTER));
          }
          break;
        case BIT_AND:
          Ir.BitAnd bitAnd = st.getBitAnd();
          code.add()
              .getAndBuilder()
              .setOp1(addressTo(bitAnd.getA()))
              .setOp2(addressTo(bitAnd.getB()))
              .setTarget(addressTo(bitAnd.getResult()));
          break;
        case BIT_OR:
          Ir.BitOr bitOr = st.getBitOr();
          code.add()
              .getAndBuilder()
              .setOp1(addressTo(bitOr.getA()))
              .setOp2(addressTo(bitOr.getB()))
              .setTarget(addressTo(bitOr.getResult()));
          break;
        default:
          throw new UnsupportedOperationException("Unhandled IR: " + st);
      }
    }
    for (Entry<Integer, Ir.Label> label : labelFills.entrySet()) {
      code.get(label.getKey())
          .getJmpBuilder()
          .setAddr(code.storeData(labelAddresses.get(label.getValue())));
    }
    for (Entry<Integer, Ir.Label> label : labelDataFills.entrySet()) {
      code.exec.setData(label.getKey(), labelAddresses.get(label.getValue()));
    }
  }

  private static Address addressTo(Ir.Addr addr) {
    switch (addr.getAddrCase()) {
      case RELSP:
        return Address.newBuilder()
            .setBase(Conventions.STACK_POINTER.getAbsStack())
            .setOffset(addr.getRelSp())
            .build();
      case ABSDATA:
        return Address.newBuilder()
            .setBase(-Conventions.CONST_ZERO.getAbsData() - 1)
            .setOffset(-addr.getAbsData() - 1)
            .build();
      case ABSSTACK:
        return Address.newBuilder()
            .setBase(-Conventions.CONST_ZERO.getAbsData() - 1)
            .setOffset(addr.getAbsStack())
            .build();
      default:
        throw new IllegalArgumentException();
    }
  }

  static class Code {
    Executable.Builder exec = Executable.newBuilder();
    Map<Integer, Integer> dataPos = new HashMap<>();

    Instruction.Builder add() {
      return exec.addCodeBuilder();
    }

    public int size() {
      return exec.getCodeCount();
    }

    public Instruction.Builder get(int idx) {
      return exec.getCodeBuilder(idx);
    }

    public Address storeData(int val) {
      if (dataPos.containsKey(val)) {
        return addressTo(IrAddrs.dataCell(dataPos.get(val)));
      }
      int nxpos = exec.getDataCount();
      dataPos.put(val, nxpos);
      exec.addData(val);
      return addressTo(IrAddrs.dataCell(nxpos));
    }

    public Executable build() {
      return exec.build();
    }

    public void store(int val, Address pos) {
      add().getMoveBuilder().setSource(storeData(val)).setTarget(pos);
    }

    public int dataPlaceholder() {
      int pos = exec.getDataCount();
      exec.addData(-1);
      return pos;
    }
  }
}
