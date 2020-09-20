package se.jsannemo.spooky.vm.code;

/** The byte values of all VM opcodes. */
enum OpCode {
  // A binary definition.
  BINDEF((byte) 0x00),
  // Start of the text segment.
  TEXT((byte) 0x01),
  // Start of the data segment.
  DATA((byte) 0x02),
  // A move instruction.
  MOV((byte) 0x03),
  // A constant store instruction.
  CONST((byte) 0x04),
  // An add instruction.
  ADD((byte) 0x05),
  // A multiply instruction.
  MUL((byte) 0x06),
  // A subtraction instruction.
  SUB((byte) 0x07),
  // A division instruction.
  DIV((byte) 0x08),
  // A modulo instruction.
  MOD((byte) 0x0E),
  // A less than comparison instruction.
  LT((byte) 0x09),
  // An equals comparison instruction.
  EQ((byte) 0x0D),
  // A jump instruction.
  JMP((byte) 0x0A),
  // An extern call instruction.
  EXTERN((byte) 0x0B),
  // A halt instruction.
  HALT((byte) 0x0C),
  // A less than or equals comparison instruction.
  LEQ((byte) 0x0F),
  // A jump by address instruction.
  JMPADR((byte) 0x10),
// Next: 0x11
;

  final byte code;

  OpCode(byte code) {
    this.code = code;
  }
}
