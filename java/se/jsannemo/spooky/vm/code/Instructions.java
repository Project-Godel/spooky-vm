package se.jsannemo.spooky.vm.code;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import jsinterop.annotations.JsType;

/** Data classes for all the supported instructions. */
public final class Instructions {

  private Instructions() {}

  @AutoValue
  public abstract static class Address {
    Address() {}

    public abstract int baseAddr();

    public abstract int offset();

    public static Address baseAndOffset(int baseAddr, int offset) {
      return new AutoValue_Instructions_Address(baseAddr, offset);
    }
  }

  /**
   * Parent class for instructions. Instances can be created using the specific instruction type.
   */
  @JsType
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

    /** The name of the binary defined by the instruction. */
    public abstract String name();

    public static BinDef create(String name) {
      return new AutoValue_Instructions_BinDef(name);
    }

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.BINDEF.code);
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
      os.write(OpCode.TEXT.code);
    }
  }

  /**
   * Copies the value from the memory cell point to by {@code addr} into the memory cell pointed to
   * by {@code target}. Essentially, it performs something akin to <code>
   * mem[target] = mem[source]</code>
   */
  @AutoValue
  public abstract static class Move extends Instruction {
    Move() {}

    public abstract Address source();

    public abstract Address target();

    public static Move create(Address source, Address target) {
      return new AutoValue_Instructions_Move(source, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.MOV.code);
      Serialization.writeAddr(os, source());
      Serialization.writeAddr(os, target());
    }
  }

  /** Stores {@code value} at the address {@code target}. */
  @AutoValue
  public abstract static class Const extends Instruction {
    Const() {}

    public abstract int value();

    public abstract Address target();

    public static Const create(int value, Address target) {
      return new AutoValue_Instructions_Const(value, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.CONST.code);
      Serialization.writeInt(os, value());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Adds the values at addresses {@code op1} and {@code op2} and stores the result at address
   * {@code target}.
   */
  @AutoValue
  public abstract static class Add extends Instruction {
    Add() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Add create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Add(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.ADD.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Subtracts the value at address {@code op2} from the value at addresss {@code op} and stores the
   * result at address {@code target}.
   */
  @AutoValue
  public abstract static class Sub extends Instruction {
    Sub() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Sub create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Sub(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.SUB.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Multiplies the values at addresses {@code op1} and {@code op2} and stores the result at address
   * {@code target}.
   */
  @AutoValue
  public abstract static class Mul extends Instruction {
    Mul() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Mul create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Mul(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.MUL.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Divides the values at address {@code op1} with the value at address {@code op2} and stores the
   * result at address {@code target}.
   *
   * <p>Throws an exception when dividing by 0.
   */
  @AutoValue
  public abstract static class Div extends Instruction {
    Div() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Div create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Div(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.DIV.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Computes the remainder of the value at address {@code op1} when divided by the with the value
   * at address {@code op2} and stores the result at address {@code target}.
   *
   * <p>Throws an exception when dividing by 0.
   */
  @AutoValue
  public abstract static class Mod extends Instruction {
    Mod() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Mod create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Mod(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.MOD.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Compares the values at addresses {@code op1} and {@code op2} and stores 1 (if {@code op1 <
   * op2}) or 0 (otherwise) at address {@code target}.
   */
  @AutoValue
  public abstract static class LessThan extends Instruction {
    LessThan() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static LessThan create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_LessThan(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.LT.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Compares the values at addresses {@code op1} and {@code op2} and stores 1 (if {@code op1 <=
   * op2}) or 0 (otherwise) at address {@code target}.
   */
  @AutoValue
  public abstract static class LessEquals extends Instruction {
    LessEquals() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static LessEquals create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_LessEquals(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.LEQ.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Compares the values at addresses {@code op1} and {@code op2} and stores 1 (if {@code op1 ==
   * op2}) or 0 (otherwise) at address {@code target}.
   */
  @AutoValue
  public abstract static class Equals extends Instruction {
    Equals() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Equals create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_Equals(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.EQ.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Compares the values at addresses {@code op1} and {@code op2} and stores 0 (if {@code op1 ==
   * op2}) or 1 (otherwise) at address {@code target}.
   */
  @AutoValue
  public abstract static class NotEquals extends Instruction {
    NotEquals() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static Instructions.NotEquals create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_NotEquals(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.NEQ.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Computes the bitwise and of the value at address {@code op2} and the value at addresss {@code
   * op} and stores the result at address {@code target}.
   */
  @AutoValue
  public abstract static class BitAnd extends Instruction {
    BitAnd() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static BitAnd create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_BitAnd(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.BITAND.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Computes the bitwise or of the value at address {@code op2} and the value at addresss {@code
   * op} and stores the result at address {@code target}.
   */
  @AutoValue
  public abstract static class BitOr extends Instruction {
    BitOr() {}

    public abstract Address op1();

    public abstract Address op2();

    public abstract Address target();

    public static BitOr create(Address op1, Address op2, Address target) {
      return new AutoValue_Instructions_BitOr(op1, op2, target);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.BITOR.code);
      Serialization.writeAddr(os, op1());
      Serialization.writeAddr(os, op2());
      Serialization.writeAddr(os, target());
    }
  }

  /**
   * Jumps to the instruction at {@code addr} (indexed by 0 starting at the first instruction in the
   * text segment) if the value stored at {@code flag} is 0, i.e. <code>
   * ip = mem[addr]</code>
   */
  @AutoValue
  public abstract static class Jump extends Instruction {
    Jump() {}

    public abstract Address flag();

    public abstract int addr();

    public static Jump create(Address flag, int addr) {
      return new AutoValue_Instructions_Jump(flag, addr);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.JMP.code);
      Serialization.writeAddr(os, flag());
      Serialization.writeInt(os, addr());
    }
  }

  /**
   * Jumps to the instruction at {@code addr} (indexed by 0 starting at the first instruction in the
   * text segment) if the value stored at {@code flag} is 1, i.e. <code>
   * ip = mem[addr]</code>
   */
  @AutoValue
  public abstract static class JumpN extends Instruction {
    JumpN() {}

    public abstract Address flag();

    public abstract int addr();

    public static JumpN create(Address flag, int addr) {
      return new AutoValue_Instructions_JumpN(flag, addr);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.JMPN.code);
      Serialization.writeAddr(os, flag());
      Serialization.writeInt(os, addr());
    }
  }

  /**
   * Jumps to the instruction pointed to by {@code addr} (indexed by 0 starting at the first
   * instruction in the text segment).
   */
  @AutoValue
  public abstract static class JumpAddress extends Instruction {
    JumpAddress() {}

    public abstract Address addr();

    public static JumpAddress create(Address addr) {
      return new AutoValue_Instructions_JumpAddress(addr);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.JMPADR.code);
      Serialization.writeAddr(os, addr());
    }
  }

  /** Calls the extern function with name {@code name}. */
  @AutoValue
  public abstract static class Extern extends Instruction {
    Extern() {}

    public abstract String name();

    public static Extern create(String name) {
      return new AutoValue_Instructions_Extern(name);
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.EXTERN.code);
      Serialization.writeString(os, name());
    }
  }

  /** An instruction halting all execution. */
  @AutoValue
  public abstract static class Halt extends Instruction {
    Halt() {}

    public static Halt create() {
      return new AutoValue_Instructions_Halt();
    }

    @Override
    public boolean isExecutable() {
      return true;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.HALT.code);
    }
  }

  /** An instruction marking the start of the data segment. */
  @AutoValue
  public abstract static class Data extends Instruction {
    Data() {}

    public abstract ImmutableList<Integer> data();

    public static Data create(ImmutableList<Integer> data) {
      return new AutoValue_Instructions_Data(data);
    }

    @Override
    public boolean isExecutable() {
      return false;
    }

    @Override
    public void writeBinary(OutputStream os) throws IOException {
      os.write(OpCode.DATA.code);
      for (int i : data()) {
        Serialization.writeInt(os, i);
      }
    }
  }
}
