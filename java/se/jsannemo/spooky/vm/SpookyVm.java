package se.jsannemo.spooky.vm;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.io.PrintStream;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.Instructions;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Address;
import se.jsannemo.spooky.vm.code.Instructions.BitAnd;
import se.jsannemo.spooky.vm.code.Instructions.BitOr;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Equals;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Halt;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.JumpAddress;
import se.jsannemo.spooky.vm.code.Instructions.JumpN;
import se.jsannemo.spooky.vm.code.Instructions.LessEquals;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Mod;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.NotEquals;
import se.jsannemo.spooky.vm.code.Instructions.Sub;

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

  private final PrintStream stdOut;
  private int instructions = 0;
  private int maxMemory = -1;

  private SpookyVm(
      Executable executable,
      ImmutableMap<String, ExternCall> externs,
      int memoryCells,
      PrintStream stdOut) {
    this.externs = externs;
    this.curExecutable = executable;
    this.ip = 0;
    this.memory = new int[memoryCells];
    this.stdOut = stdOut;
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
    if (ip < 0 || ip >= curExecutable.text().size()) {
      throw new VmException("Instruction pointer out-of-bounds");
    }
    instructions++;
    Instructions.Instruction ins = curExecutable.text().get(ip++);
    checkState(ins.isExecutable());
    if (ins instanceof Instructions.Move) {
      Move mov = (Move) ins;
      setM(mov.target(), getM(mov.source()));
    } else if (ins instanceof Const) {
      Const cnst = (Const) ins;
      setM(cnst.target(), cnst.value());
    } else if (ins instanceof Add) {
      Add add = (Add) ins;
      setM(add.target(), getM(add.op1()) + getM(add.op2()));
    } else if (ins instanceof Sub) {
      Sub sub = (Sub) ins;
      setM(sub.target(), getM(sub.op1()) - getM(sub.op2()));
    } else if (ins instanceof Mul) {
      Mul mul = (Mul) ins;
      setM(mul.target(), getM(mul.op1()) * getM(mul.op2()));
    } else if (ins instanceof Div) {
      Div div = (Div) ins;
      int denominator = getM(div.op2());
      if (denominator == 0) {
        throw new VmException("Division by zero");
      }
      setM(div.target(), getM(div.op1()) / denominator);
    } else if (ins instanceof Mod) {
      Mod mod = (Mod) ins;
      int denominator = getM(mod.op2());
      if (denominator == 0) {
        throw new VmException("Division by zero");
      }
      setM(mod.target(), getM(mod.op1()) % denominator);
    } else if (ins instanceof LessThan) {
      LessThan lt = (LessThan) ins;
      setM(lt.target(), getM(lt.op1()) < getM(lt.op2()) ? 1 : 0);
    } else if (ins instanceof LessEquals) {
      LessEquals leq = (LessEquals) ins;
      setM(leq.target(), getM(leq.op1()) <= getM(leq.op2()) ? 1 : 0);
    } else if (ins instanceof Equals) {
      Equals eq = (Equals) ins;
      setM(eq.target(), getM(eq.op1()) == getM(eq.op2()) ? 1 : 0);
    } else if (ins instanceof NotEquals) {
      NotEquals neq = (NotEquals) ins;
      setM(neq.target(), getM(neq.op1()) != getM(neq.op2()) ? 1 : 0);
    } else if (ins instanceof BitOr) {
      BitOr or = (BitOr) ins;
      setM(or.target(), getM(or.op1()) | getM(or.op2()));
    } else if (ins instanceof BitAnd) {
      BitAnd and = (BitAnd) ins;
      setM(and.target(), getM(and.op1()) & getM(and.op2()));
    } else if (ins instanceof Jump) {
      Jump jmp = (Jump) ins;
      if (getM(jmp.flag()) == 0) {
        ip = jmp.addr();
      }
    } else if (ins instanceof JumpN) {
      JumpN jmp = (JumpN) ins;
      if (getM(jmp.flag()) != 0) {
        ip = jmp.addr();
        System.out.println("JNZ jump to " + ip + " of " + curExecutable.text().size());
      }
    } else if (ins instanceof JumpAddress) {
      JumpAddress jmp = (JumpAddress) ins;
      ip = getM(jmp.addr());
    } else if (ins instanceof Extern) {
      Extern ext = (Extern) ins;
      callExtern(ext.name());
    } else if (ins instanceof Halt) {
      return false;
    } else {
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
    if (-this.curExecutable.data().size() <= pos && pos < 0) {
      return this.curExecutable.data().get(-(pos + 1));
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
    return getM(addr.baseAddr()) + addr.offset();
  }

  public PrintStream getStdOut() {
    return stdOut;
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
    private PrintStream stdOut;

    private Builder(Executable executable) {
      this.executable = executable;
      memoryCells = 0;
      stdOut = System.out;
    }

    /** Make available an external call named {@code name} invoking {@code callback} when called. */
    public Builder addExtern(String name, ExternCall callback) {
      externBuilder.put(name, callback);
      return this;
    }

    /** Add the external calls that the standard library provides. */
    public Builder addStdLib() {
      externBuilder.put("random", StdLib::random);
      externBuilder.put("print", StdLib::printChar);
      externBuilder.put("printInt", StdLib::printInt);
      return this;
    }

    /** Set the memory size in (integer-sized) cells. */
    public Builder setMemorySize(int memoryCells) {
      this.memoryCells = memoryCells;
      return this;
    }

    public SpookyVm build() {
      return new SpookyVm(executable, externBuilder.build(), memoryCells, stdOut);
    }

    public Builder setStdOut(PrintStream writer) {
      this.stdOut = writer;
      return this;
    }
  }
}
