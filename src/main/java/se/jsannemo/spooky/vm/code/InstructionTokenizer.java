package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;
import static se.jsannemo.spooky.vm.code.Instructions.Extern.create;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.Sub;
import se.jsannemo.spooky.vm.code.Instructions.Text;

/** A tokenizer of raw bytes into the corresponding instructions. */
final class InstructionTokenizer {
  @FunctionalInterface
  private interface Tokenizer {
    Instruction parse(ByteStreamIterator context) throws InstructionException;
  }

  private static final ImmutableMap<Byte, Tokenizer> TOKENIZERS =
      ImmutableMap.<Byte, Tokenizer>builder()
          .put(OpCodes.BINDEF, InstructionTokenizer::parseBinDef)
          .put(OpCodes.TEXT, InstructionTokenizer::parseText)
          .put(OpCodes.DATA, InstructionTokenizer::parseData)
          .put(OpCodes.MOV, InstructionTokenizer::parseMov)
          .put(OpCodes.CONST, InstructionTokenizer::parseConst)
          .put(OpCodes.ADD, InstructionTokenizer::parseAdd)
          .put(OpCodes.SUB, InstructionTokenizer::parseSub)
          .put(OpCodes.MUL, InstructionTokenizer::parseMul)
          .put(OpCodes.DIV, InstructionTokenizer::parseDiv)
          .put(OpCodes.LT, InstructionTokenizer::parseLessThan)
          .put(OpCodes.JMP, InstructionTokenizer::parseJump)
          .put(OpCodes.EXTERN, InstructionTokenizer::parseExtern)
          .build();

  static ImmutableList<Instruction> tokenize(byte[] content) throws InstructionException {
    ByteStreamIterator context = new ByteStreamIterator(content);
    ImmutableList.Builder<Instruction> instruction = ImmutableList.builder();
    while (!context.finished()) {
      if (TOKENIZERS.containsKey(context.currentByte())) {
        try {
          instruction.add(TOKENIZERS.get(context.currentByte()).parse(context));
        } catch (IllegalArgumentException iae) {
          throw new InstructionException("Could not parse instruction", iae);
        }
      } else {
        throw new InstructionException("Invalid instruction: " + context.currentByte());
      }
    }
    return instruction.build();
  }

  private static Instruction parseData(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.DATA, "Expected DATA byte");
    context.advance(1);
    ImmutableList.Builder<Integer> data = ImmutableList.builder();
    while (!context.finished()) {
      data.add(Serialization.readInt(context));
    }
    return Instructions.Data.create(data.build());
  }

  private static Instruction parseText(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.TEXT, "Expected TEXT byte");
    context.advance(1);
    return Text.create();
  }

  private static Instruction parseBinDef(ByteStreamIterator context) throws InstructionException {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.BINDEF, "Expected BINDEF byte");
    context.advance(1);
    String binName = Serialization.readString(context);
    if (binName.isEmpty()) {
      throw new InstructionException("Empty executable name is not allowed");
    }
    return BinDef.create(binName);
  }

  private static Instruction parseExtern(ByteStreamIterator context) throws InstructionException {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.EXTERN, "Expected EXTERN byte");
    context.advance(1);
    String funcName = Serialization.readString(context);
    if (funcName.isEmpty()) {
      throw new InstructionException("Empty function name is not allowed");
    }
    return create(funcName);
  }

  private static Instruction parseConst(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.CONST, "Expected CONST byte");
    context.advance(1);
    int value = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Const.create(value, target);
  }

  private static Instruction parseMov(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.MOV, "Expected MOV byte");
    context.advance(1);
    int source = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Move.create(source, target);
  }

  private static Instruction parseAdd(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.ADD, "Expected ADD byte");
    context.advance(1);
    int op1 = Serialization.readInt(context);
    int op2 = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Add.create(op1, op2, target);
  }

  private static Instruction parseSub(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.SUB, "Expected SUB byte");
    context.advance(1);
    int op1 = Serialization.readInt(context);
    int op2 = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Sub.create(op1, op2, target);
  }

  private static Instruction parseMul(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.MUL, "Expected MUL byte");
    context.advance(1);
    int op1 = Serialization.readInt(context);
    int op2 = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Mul.create(op1, op2, target);
  }

  private static Instruction parseDiv(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.DIV, "Expected DIV byte");
    context.advance(1);
    int op1 = Serialization.readInt(context);
    int op2 = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return Div.create(op1, op2, target);
  }

  private static Instruction parseJump(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.JMP, "Expected JMP byte");
    context.advance(1);
    int flag = Serialization.readInt(context);
    int addr = Serialization.readInt(context);
    return Jump.create(flag, addr);
  }

  private static Instruction parseLessThan(ByteStreamIterator context) {
    checkArgument(!context.finished() && context.currentByte() == OpCodes.LT, "Expected LT byte");
    context.advance(1);
    int op1 = Serialization.readInt(context);
    int op2 = Serialization.readInt(context);
    int target = Serialization.readInt(context);
    return LessThan.create(op1, op2, target);
  }

}
