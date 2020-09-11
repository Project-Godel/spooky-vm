package se.jsannemo.spooky.compiler;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import se.jsannemo.spooky.vm.code.ExecutableParser;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;

/** Assembler for VM instructions into binary code. */
public final class Assembler {

  /**
   * Assembles {@code instructions} into a binary format that can be parsed by {@link
   * ExecutableParser#fromBinary(byte[])}.
   */
  public static byte[] assemble(ImmutableList<Instruction> instructions) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      for (Instruction inst : instructions) {
        inst.writeBinary(bos);
      }
    } catch (IOException ioe) {
      throw new AssertionError("ByteArrayOutputStream shouldn't throw IOException");
    }
    return bos.toByteArray();
  }
}
