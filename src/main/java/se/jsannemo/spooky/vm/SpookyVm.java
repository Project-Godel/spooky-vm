package se.jsannemo.spooky.vm;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.Sub;

/**
 * A virtual machine, executing parsed Spooky code.
 *
 * <p>The Spooky code is given to the VM in the form of a {@link Executable}. It will start executing
 * the first instruction in the text segment.
 */
public final class SpookyVm {

  private final ImmutableMap<String, ExternCall> externs;
  private final int[] memory;
  /**
   * The value of the instruction pointer, with the index of the text instructions in the current
   * executable that should be executed.
   */
  private int ip;
  /** The executable that we are currently executing instructions in. */
  private Executable curExecutable;

  private SpookyVm(Executable executable, ImmutableMap<String, ExternCall> externs, int memoryCells) {
    this.externs = externs;
    this.curExecutable = executable;
    this.ip = 0;
    this.memory = new int[memoryCells];
  }

  /**
   * Executes the current instruction of the VM, advancing the instruction pointer afterwards.
   *
   * <p>If the instruction pointer points to an invalid instruction (i.e. one that is smaller or
   * larger than the amount of instructions in the current executable), nothing happens.
   *
   * @throws VmException if the instruction caused a run-time fault in the VM.
   */
  public void executeInstruction() throws VmException {
    // Halt VM in case if an out-of-bounds instruction.
    if (ip < 0 || ip >= curExecutable.text().size()) {
      throw new VmException("Instruction pointer out-of-bounds");
    }
    Instruction ins = curExecutable.text().get(ip++);
    checkState(ins.isExecutable());
    if (ins instanceof Move) {
      Move mov = (Move) ins;
      setM(getM(mov.target()), getM(getM(mov.source())));
    }
    if (ins instanceof Const) {
      Const cnst = (Const) ins;
      setM(cnst.target(), cnst.value());
    }
    if (ins instanceof Add) {
      Add add = (Add) ins;
      setM(add.target(), getM(add.op1()) + getM(add.op2()));
    }
    if (ins instanceof Sub) {
      Sub sub = (Sub) ins;
      setM(sub.target(), getM(sub.op1()) - getM(sub.op2()));
    }
    if (ins instanceof Mul) {
      Mul mul = (Mul) ins;
      setM(mul.target(), getM(mul.op1()) * getM(mul.op2()));
    }
    if (ins instanceof Div) {
      Div div = (Div) ins;
      setM(div.target(), getM(div.op1()) / getM(div.op2()));
    }
    if (ins instanceof LessThan) {
      LessThan lt = (LessThan) ins;
      setM(lt.target(), getM(lt.op1()) < getM(lt.op2()) ? 1 : 0);
    }
    if (ins instanceof Jump) {
      Jump jmp = (Jump) ins;
      if (getM(jmp.flag()) == 0) {
        ip = getM(jmp.addr());
      }
    }
    if (ins instanceof Extern) {
      Extern ext = (Extern) ins;
      callExtern(ext.name());
    }
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
      return memory[pos];
    }
    if (-this.curExecutable.data().size() <= pos && pos < 0) {
      return this.curExecutable.data().get(-(pos + 1));
    }
    throw new VmException("Memory position " + pos + " is out of bounds");
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

  /** Returns a new builder for {@link SpookyVm} instances. */
  public static Builder newBuilder(Executable executable) {
    return new Builder(executable);
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
    public Builder addStdLib() {
      externBuilder.put("random", StdLib::random);
      externBuilder.put("print", StdLib::print);
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
