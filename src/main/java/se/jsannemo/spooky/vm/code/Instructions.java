package se.jsannemo.spooky.vm.code;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Data classes for all the supported instructions. */
public final class Instructions {

  private Instructions() {}

  /**
   * Parent class for instructions. Instances can be created using the specific instruction type.
   */
  public abstract static class Instruction {
    private Instruction() {}

    /**
     * Returns whether the instruction is executable and can thus appear in the text segment of a
     * binary.
     */
    public boolean isExecutable() {
      return true;
    }

    /**
     * Writes the binary representation of the instruction to {@code os}, as can be parsed by {@link
     * ExecutableParser#fromBinary(byte[])}.
     */
    public abstract void writeBinary(OutputStream os) throws IOException;
  }

  /**
   * A binary definition with a given name.
   *
   * <p>Must be the first instruction in a binary.
   */
  @AutoValue
  public abstract static class BinDef extends Instruction {
    BinDef() {}

    public static BinDef create(String name) {
      return new AutoValue_Instructions_BinDef(name);
    }

    /** The name of the binary defined by the instruction. */
    public abstract String name();

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.BINDEF);
      byte[] bytes = name().getBytes(StandardCharsets.UTF_8);
      os.write(bytes.length);
      os.write(bytes);
    }
  }

  /** An instruction marking the start of the text segment. */
  @AutoValue
  public abstract static class Text extends Instruction {
    Text() {}

    public static Text create() {
      return new AutoValue_Instructions_Text();
    }

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.TEXT);
    }
  }

  /**
   * Copies the value from the memory cell point to by {@code addr} into the memory cell pointed to
   * by {@code target}. Essentially, it performs something akin to <code>
   * mem[mem[target]] = mem[mem[source]]</code>
   */
  @AutoValue
  public abstract static class Move extends Instruction {
    Move() {}

    public static Move create(int source, int target) {
      return new AutoValue_Instructions_Move(source, target);
    }

    public abstract int source();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.MOV);
      Serialization.writeInt(os, source());
      Serialization.writeInt(os, target());
    }
  }

  /** Stores {@code value} at the address {@code target}. */
  @AutoValue
  public abstract static class Const extends Instruction {
    Const() {}

    public static Const create(int value, int target) {
      return new AutoValue_Instructions_Const(value, target);
    }

    public abstract int value();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.CONST);
      Serialization.writeInt(os, value());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Adds the values at addresses {@code op1} and {@code op2} and stores the result at address
   * {@code target}.
   */
  @AutoValue
  public abstract static class Add extends Instruction {
    Add() {}

    public static Add create(int op1, int op2, int target) {
      return new AutoValue_Instructions_Add(op1, op2, target);
    }

    public abstract int op1();

    public abstract int op2();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.ADD);
      Serialization.writeInt(os, op1());
      Serialization.writeInt(os, op2());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Subtracts the value at address {@code op2} from the value at addresss {@code op} and stores the
   * result at address {@code target}.
   */
  @AutoValue
  public abstract static class Sub extends Instruction {
    Sub() {}

    public static Sub create(int op1, int op2, int target) {
      return new AutoValue_Instructions_Sub(op1, op2, target);
    }

    public abstract int op1();

    public abstract int op2();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.SUB);
      Serialization.writeInt(os, op1());
      Serialization.writeInt(os, op2());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Multiplies the values at addresses {@code op1} and {@code op2} and stores the result at address
   * {@code target}.
   */
  @AutoValue
  public abstract static class Mul extends Instruction {
    Mul() {}

    public static Mul create(int op1, int op2, int target) {
      return new AutoValue_Instructions_Mul(op1, op2, target);
    }

    public abstract int op1();

    public abstract int op2();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.MUL);
      Serialization.writeInt(os, op1());
      Serialization.writeInt(os, op2());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Divides the values at address {@code op1} with the value at address {@code op2} and stores the
   * result at address {@code target}.
   */
  @AutoValue
  public abstract static class Div extends Instruction {
    Div() {}

    public static Div create(int op1, int op2, int target) {
      return new AutoValue_Instructions_Div(op1, op2, target);
    }

    public abstract int op1();

    public abstract int op2();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.DIV);
      Serialization.writeInt(os, op1());
      Serialization.writeInt(os, op2());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Compares the values at addresses {@code op1} and {@code op2} and stores 1 (if {@code op1 <
   * op2}) or 0 (otherwise) at address {@code target}.
   */
  @AutoValue
  public abstract static class LessThan extends Instruction {
    LessThan() {}

    public static LessThan create(int op1, int op2, int target) {
      return new AutoValue_Instructions_LessThan(op1, op2, target);
    }

    public abstract int op1();

    public abstract int op2();

    public abstract int target();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.LT);
      Serialization.writeInt(os, op1());
      Serialization.writeInt(os, op2());
      Serialization.writeInt(os, target());
    }
  }

  /**
   * Jumps to the instruction pointed to by {@code addr} (indexed by 0 starting at the first
   * instruction in the text segment) if the value stored at {@code flag} is 0, i.e. <code>
   * ip = mem[addr]</code>
   */
  @AutoValue
  public abstract static class Jump extends Instruction {
    Jump() {}

    public static Jump create(int flag, int addr) {
      return new AutoValue_Instructions_Jump(flag, addr);
    }

    public abstract int flag();

    public abstract int addr();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.JMP);
      Serialization.writeInt(os, flag());
      Serialization.writeInt(os, addr());
    }
  }

  /** Calls the extern function with name {@code name}. */
  @AutoValue
  public abstract static class Extern extends Instruction {
    Extern() {}

    public static Extern create(String name) {
      return new AutoValue_Instructions_Extern(name);
    }

    public abstract String name();

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.EXTERN);
      Serialization.writeString(os, name());
    }
  }

  /** An instruction marking the start of the data segment. */
  @AutoValue
  public abstract static class Data extends Instruction {
    Data() {}

    public static Data create(ImmutableList<Integer> data) {
      return new AutoValue_Instructions_Data(data);
    }

    public abstract ImmutableList<Integer> data();

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCodes.DATA);
      for (int i : data()) {
        Serialization.writeInt(os, i);
      }
    }
  }
}
