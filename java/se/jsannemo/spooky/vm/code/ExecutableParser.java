package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.util.List;
import jsinterop.annotations.JsMethod;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Data;
import se.jsannemo.spooky.vm.code.Instructions.Text;

/** Parser for a binary Spooky executable into a {@link Executable}. */
public final class ExecutableParser {

  private ExecutableParser() {}

  /**
   * Parses an executable definition, returning a view of {@code instructions} with the instructions
   * used in the segment consumed.
   */
  private static List<Instructions.Instruction> parseBinDef(
      Executable.Builder builder, List<Instructions.Instruction> instructions)
      throws InstructionException {
    checkArgument(!instructions.isEmpty(), "Expected BINDEF, was EOF");
    Instructions.Instruction first = instructions.get(0);
    if (first instanceof BinDef) {
      builder.name(((BinDef) first).name());
    } else {
      throw new InstructionException("Expected BINDEF, was " + first);
    }
    return instructions.subList(1, instructions.size());
  }

  /**
   * Parses a text segment, returning a view of {@code instructions} with the instructions used in
   * the segment consumed.
   */
  private static List<Instructions.Instruction> parseText(
      Executable.Builder builder, List<Instructions.Instruction> instructions)
      throws InstructionException {
    checkArgument(!instructions.isEmpty(), "Expected TEXT, was EOF");
    Instructions.Instruction first = instructions.get(0);
    checkArgument(first instanceof Text, "Expected TEXT, was " + first);

    instructions = instructions.subList(1, instructions.size());
    ImmutableList.Builder<Instructions.Instruction> textSegment = ImmutableList.builder();
    // Extract all instructions until the data segment
    for (int i = 0; i < instructions.size(); i++) {
      Instructions.Instruction instruction = instructions.get(i);
      if (instruction instanceof Instructions.Data) {
        instructions = instructions.subList(i, instructions.size());
        break;
      }
      if (!instruction.isExecutable()) {
        throw new InstructionException(
            "Instruction " + instruction + " in text segment is not executable");
      }
      textSegment.add(instruction);
    }
    builder.text(textSegment.build());
    return instructions;
  }

  /**
   * Parses a data segment, returning a view of {@code instructions} with the instructions used in
   * the segment consumed.
   */
  private static List<Instructions.Instruction> parseData(
      Executable.Builder builder, List<Instructions.Instruction> instructions)
      throws InstructionException {
    if (instructions.isEmpty()) {
      return instructions;
    }
    Instructions.Instruction first = instructions.get(0);
    if (first instanceof Data) {
      builder.data(((Data) first).data());
    } else {
      throw new InstructionException("Expected data instruction after text; was " + first);
    }
    return instructions.subList(1, instructions.size());
  }

  /** Attempts to parse the binary data {@code bytes} as a Spooky executable. */
  public static Executable fromBinary(byte[] bytes) throws InstructionException {
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(bytes);
    return fromInstructions(instruction);
  }

  /**
   * Parses the list {@code instructions} into a {@link Executable}. If an error is found in the
   * structure of the instruction list, an {@link InstructionException} is thrown.
   *
   * <p>Executables must have the structure:
   *
   * <ul>
   *   <li>Metadata, starting with a BINDEF
   *   <li>Text, starting with a TEXT instruction followed by only executable instructions
   *   <li>Optionally, data, starting with a DATA followed by binary data
   * </ul>
   */
  @JsMethod
  public static Executable fromInstructions(List<Instructions.Instruction> instructions)
      throws InstructionException {
    Executable.Builder builder = Executable.builder();
    try {
      instructions = parseBinDef(builder, instructions);
      instructions = parseText(builder, instructions);
      instructions = parseData(builder, instructions);
    } catch (IllegalArgumentException iae) {
      throw new InstructionException("Failed parsing executable", iae);
    }
    checkState(instructions.isEmpty(), "Parsing did not consume all instructions");
    return builder.build();
  }
}
