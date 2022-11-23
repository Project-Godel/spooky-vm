package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;
import static se.jsannemo.spooky.vm.code.Instructions.Extern.create;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.vm.code.Instructions.*;

/** A tokenizer of raw bytes into the corresponding instructions. */
final class InstructionTokenizer {
  private static final ImmutableMap<Byte, Tokenizer> TOKENIZERS =
      ImmutableMap.<Byte, Tokenizer>builder()
          .put(OpCode.BINDEF.code, InstructionTokenizer::parseBinDef)
          .put(OpCode.TEXT.code, InstructionTokenizer::parseText)
          .put(OpCode.DATA.code, InstructionTokenizer::parseData)
          .put(OpCode.MOV.code, InstructionTokenizer::parseMov)
          .put(OpCode.CONST.code, InstructionTokenizer::parseConst)
          .put(OpCode.ADD.code, InstructionTokenizer::parseAdd)
          .put(OpCode.SUB.code, InstructionTokenizer::parseSub)
          .put(OpCode.MUL.code, InstructionTokenizer::parseMul)
          .put(OpCode.DIV.code, InstructionTokenizer::parseDiv)
          .put(OpCode.MOD.code, InstructionTokenizer::parseMod)
          .put(OpCode.LT.code, InstructionTokenizer::parseLessThan)
          .put(OpCode.LEQ.code, InstructionTokenizer::parseLessEquals)
          .put(OpCode.EQ.code, InstructionTokenizer::parseEquals)
          .put(OpCode.NEQ.code, InstructionTokenizer::parseNotEquals)
          .put(OpCode.JMP.code, InstructionTokenizer::parseJump)
          .put(OpCode.JMPN.code, InstructionTokenizer::parseJumpN)
          .put(OpCode.JMPADR.code, InstructionTokenizer::parseJumpAddress)
          .put(OpCode.EXTERN.code, InstructionTokenizer::parseExtern)
          .put(OpCode.HALT.code, InstructionTokenizer::parseHalt)
          .put(OpCode.BITAND.code, InstructionTokenizer::parseBitAnd)
          .put(OpCode.BITOR.code, InstructionTokenizer::parseBitOr)
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
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.DATA.code, "Expected DATA byte");
    context.advance(1);
    ImmutableList.Builder<Integer> data = ImmutableList.builder();
    while (!context.finished()) {
      data.add(Serialization.readInt(context));
    }
    return Instructions.Data.create(data.build());
  }

  private static Instruction parseText(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.TEXT.code, "Expected TEXT byte");
    context.advance(1);
    return Text.create();
  }

  private static Instruction parseBinDef(ByteStreamIterator context) throws InstructionException {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.BINDEF.code, "Expected BINDEF byte");
    context.advance(1);
    String binName = Serialization.readString(context);
    if (binName.isEmpty()) {
      throw new InstructionException("Empty executable name is not allowed");
    }
    return BinDef.create(binName);
  }

  private static Instruction parseExtern(ByteStreamIterator context) throws InstructionException {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.EXTERN.code, "Expected EXTERN byte");
    context.advance(1);
    String funcName = Serialization.readString(context);
    if (funcName.isEmpty()) {
      throw new InstructionException("Empty function name is not allowed");
    }
    return create(funcName);
  }

  private static Instruction parseConst(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.CONST.code, "Expected CONST byte");
    context.advance(1);
    int value = Serialization.readInt(context);
    Address addr = Serialization.readAddr(context);
    return Const.create(value, addr);
  }

  private static Instruction parseMov(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.MOV.code, "Expected MOV byte");
    context.advance(1);
    Address source = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Move.create(source, target);
  }

  private static Instruction parseAdd(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.ADD.code, "Expected ADD byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Add.create(op1, op2, target);
  }

  private static Instruction parseSub(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.SUB.code, "Expected SUB byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Sub.create(op1, op2, target);
  }

  private static Instruction parseMul(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.MUL.code, "Expected MUL byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Mul.create(op1, op2, target);
  }

  private static Instruction parseDiv(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.DIV.code, "Expected DIV byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Div.create(op1, op2, target);
  }

  private static Instruction parseMod(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.MOD.code, "Expected MOD byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Mod.create(op1, op2, target);
  }

  private static Instruction parseJump(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.JMP.code, "Expected JMP byte");
    context.advance(1);
    Address flag = Serialization.readAddr(context);
    int addr = Serialization.readInt(context);
    return Jump.create(flag, addr);
  }

  private static Instruction parseJumpN(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.JMPN.code, "Expected JMPN byte");
    context.advance(1);
    Address flag = Serialization.readAddr(context);
    int addr = Serialization.readInt(context);
    return JumpN.create(flag, addr);
  }

  private static Instruction parseJumpAddress(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.JMPADR.code, "Expected JMPADR byte");
    context.advance(1);
    Address addr = Serialization.readAddr(context);
    return JumpAddress.create(addr);
  }

  private static Instruction parseLessThan(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.LT.code, "Expected LT byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return LessThan.create(op1, op2, target);
  }

  private static Instruction parseLessEquals(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.LEQ.code, "Expected LEQ byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return LessEquals.create(op1, op2, target);
  }

  private static Instruction parseEquals(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.EQ.code, "Expected EQ byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return Equals.create(op1, op2, target);
  }

  private static Instruction parseNotEquals(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.NEQ.code, "Expected NEQ byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return NotEquals.create(op1, op2, target);
  }

  private static Instruction parseHalt(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.HALT.code, "Expected HALT byte");
    context.advance(1);
    return Halt.create();
  }

  private static Instruction parseBitAnd(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.BITAND.code, "Expected BITAND byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return BitAnd.create(op1, op2, target);
  }

  private static Instruction parseBitOr(ByteStreamIterator context) {
    checkArgument(
        !context.finished() && context.currentByte() == OpCode.BITOR.code, "Expected BITOR byte");
    context.advance(1);
    Address op1 = Serialization.readAddr(context);
    Address op2 = Serialization.readAddr(context);
    Address target = Serialization.readAddr(context);
    return BitOr.create(op1, op2, target);
  }

  @FunctionalInterface
  private interface Tokenizer {
    Instruction parse(ByteStreamIterator context) throws InstructionException;
  }
}
