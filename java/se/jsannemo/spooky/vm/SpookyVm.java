package se.jsannemo.spooky.vm;

import com.google.common.collect.ImmutableMap;

import java.io.PrintStream;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A virtual machine, executing parsed Spooky code.
 *
 * <p>The Spooky code is given to the VM in the form of a {@link Executable}. It will start
 * executing the first instruction in the text segment.
 */
public final class SpookyVm {

  private final ImmutableMap<String, ExternCall> externs;
  private final int[] memory;
  /** The executable that we are currently executing instructions in. */
  private final Executable curExecutable;
  /**
   * The value of the instruction pointer, with the index of the text instructions in the current
   * executable that should be executed.
   */
  private int ip;

  private int instructions = 0;
  private int maxMemory = -1;

  private SpookyVm(
      Executable executable, ImmutableMap<String, ExternCall> externs, int memoryCells) {
    this.externs = externs;
    this.curExecutable = executable;
    this.ip = 0;
    this.memory = new int[memoryCells];
  }

  /**
   * Executes the current instruction of the VM, advancing the instruction pointer afterwards.
   *
   * <p>If the instruction pointer points to an invalid instruction (i.e. one that is smaller or
   * larger than the amount of instructions in the current executable), an error is thrown.
   *
   * @return false if and only if the program halted.
   * @throws VmException if the instruction caused a run-time fault in the VM.
   */
  public boolean executeInstruction() throws VmException {
    // Halt VM in case if an out-of-bounds instruction.
    if (ip < 0 || ip >= curExecutable.getCodeCount()) {
      throw new VmException("Instruction pointer out-of-bounds");
    }
    instructions++;
    Instruction ins = curExecutable.getCode(ip++);
    switch (ins.getInsCase()) {
      case MOVE:
        Move mov = ins.getMove();
        setM(mov.getTarget(), getM(mov.getSource()));
        break;
      case ADD:
        Add add = ins.getAdd();
        setM(add.getTarget(), getM(add.getOp1()) + getM(add.getOp2()));
        break;
      case SUB:
        Sub sub = ins.getSub();
        setM(sub.getTarget(), getM(sub.getOp1()) - getM(sub.getOp2()));
        break;
      case MUL:
        Mul mul = ins.getMul();
        setM(mul.getTarget(), getM(mul.getOp1()) * getM(mul.getOp2()));
        break;
      case DIV:
        Div div = ins.getDiv();
        int denominator = getM(div.getOp2());
        if (denominator == 0) {
          throw new VmException("Division by zero");
        }
        setM(div.getTarget(), getM(div.getOp1()) / denominator);
        break;
      case MOD:
        Mod mod = ins.getMod();
        int modDenom = getM(mod.getOp2());
        if (modDenom == 0) {
          throw new VmException("Division by zero");
        }
        setM(mod.getTarget(), getM(mod.getOp1()) % modDenom);
        break;
      case LESS:
        LessThan lt = ins.getLess();
        setM(lt.getTarget(), getM(lt.getOp1()) < getM(lt.getOp2()) ? 1 : 0);
        break;
      case LEQ:
        LessEquals leq = ins.getLeq();
        setM(leq.getTarget(), getM(leq.getOp1()) <= getM(leq.getOp2()) ? 1 : 0);
        break;
      case EQ:
        Equals eq = ins.getEq();
        setM(eq.getTarget(), getM(eq.getOp1()) == getM(eq.getOp2()) ? 1 : 0);
        break;
      case NEQ:
        NotEquals neq = ins.getNeq();
        setM(neq.getTarget(), getM(neq.getOp1()) != getM(neq.getOp2()) ? 1 : 0);
        break;
      case OR:
        BitOr or = ins.getOr();
        setM(or.getTarget(), getM(or.getOp1()) | getM(or.getOp2()));
        break;
      case AND:
        BitAnd and = ins.getAnd();
        setM(and.getTarget(), getM(and.getOp1()) & getM(and.getOp2()));
        break;
      case JMP:
        Jump jmp = ins.getJmp();
        if ((getM(jmp.getFlag()) != 0) == jmp.getNonzero()) {
          ip = getM(jmp.getAddr());
        }
        break;
      case EXTERN:
        Extern ext = ins.getExtern();
        callExtern(ext.getName());
        break;
      case HALT:
        return false;
      default:
        throw new IllegalArgumentException("Invalid operation in VM: " + ins);
    }
    return true;
  }

  private void callExtern(String extern) throws VmException {
    if (!externs.containsKey(extern)) {
      throw new VmException("Attempted to call non-existent extern " + extern);
    }
    externs.get(extern).call(this);
  }

  /**
   * Returns the memory at position {@code pos}.
   *
   * <p>If {@code pos < 0}, the index {@code -(pos + 1)} of the data segment is returned. Otherwise,
   * the position {@code pos} from the main memory is returned.
   *
   * @throws VmException if {@code pos} is invalid.
   */
  public int getM(int pos) throws VmException {
    if (0 <= pos && pos < memory.length) {
      maxMemory = Math.max(pos, maxMemory);
      return memory[pos];
    }
    if (-this.curExecutable.getDataCount() <= pos && pos < 0) {
      return this.curExecutable.getData(-(pos + 1));
    }
    throw new VmException("Memory position " + pos + " is out of bounds");
  }

  public int getM(Address addr) throws VmException {
    return getM(resolveAddress(addr));
  }

  /**
   * Sets the memory at position {@code pos} in the main memory.
   *
   * @throws VmException if {@code pos} is invalid.
   */
  public void setM(int pos, int value) throws VmException {
    if (pos < 0 || pos >= memory.length) {
      throw new VmException("Memory position " + pos + " is out of bounds");
    }
    memory[pos] = value;
  }

  public void setM(Address addr, int value) throws VmException {
    setM(resolveAddress(addr), value);
  }

  private int resolveAddress(Address addr) throws VmException {
    int sp = getM(0);
    int a = getM(addr.getA() + (addr.getASp() ? sp : 0));
    int b = addr.getW() * getM(addr.getB() + (addr.getBSp() ? sp : 0));
    int c = addr.getC();
    System.out.println("Resolve " + addr + " to " + (a + b + c));
    return a + b + c;
  }

  /** Returns a new builder for {@link SpookyVm} instances. */
  public static Builder newBuilder(Executable executable) {
    return new Builder(executable);
  }

  /** Returns the number of instructions the VM has executed so far. */
  public int getInstructions() {
    return instructions;
  }

  /** Returns the maximum stack/heap size used so far. */
  public int getMaxMemory() {
    return maxMemory + 1;
  }

  /** A builder for {@link SpookyVm} instances. */
  public static class Builder {
    private final Executable executable;
    private final ImmutableMap.Builder<String, ExternCall> externBuilder = ImmutableMap.builder();
    private int memoryCells;

    private Builder(Executable executable) {
      this.executable = executable;
      memoryCells = 0;
    }

    /** Make available an external call named {@code name} invoking {@code callback} when called. */
    public Builder addExtern(String name, ExternCall callback) {
      externBuilder.put(name, callback);
      return this;
    }

    /** Add the external calls that the standard library provides. */
    public Builder addStdLib(PrintStream stdOut) {
      externBuilder.put("random", Calls.retInt(() -> ThreadLocalRandom.current().nextInt()));
      externBuilder.put("print", Calls.getInt(ch -> stdOut.print((char) (int) ch)));
      externBuilder.put("printInt", Calls.getInt(stdOut::print));
      return this;
    }

    /** Set the memory size in (integer-sized) cells. */
    public Builder setMemorySize(int memoryCells) {
      this.memoryCells = memoryCells;
      return this;
    }

    public SpookyVm build() {
      return new SpookyVm(executable, externBuilder.build(), memoryCells);
    }
  }
}
