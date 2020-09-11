package se.jsannemo.spooky.vm.code;

/** The byte values of all VM opcodes. */
final class OpCodes {

  private OpCodes() {}

  // A library definition.
  static final byte BINDEF = 0x00;

  // Start of the text segment.
  static final byte TEXT = 0x01;

  // Start of the data segment.
  static final byte DATA = 0x02;

  // A move instruction.
  static final byte MOV = 0x03;

  // A const instruction.
  static final byte CONST = 0x04;

  // An add instruction.
  static final byte ADD = 0x05;

  // A multiply instruction.
  static final byte MUL = 0x06;

  // A subtraction instruction.
  static final byte SUB = 0x07;

  // A division instruction.
  static final byte DIV = 0x0B;

  // A less than comparison instruction.
  static final byte LT = 0x08;

  // A jump instruction.
  static final byte JMP = 0x09;

  // An extern call instruction.
  static final byte EXTERN = 0x0A;

  // Next: 0x0B

}
