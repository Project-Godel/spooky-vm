package se.jsannemo.spooky.compiler;

import se.jsannemo.spooky.compiler.codegen.CodeGen;
import se.jsannemo.spooky.compiler.ir.IrProgram;
import se.jsannemo.spooky.compiler.ir.ToIr;
import se.jsannemo.spooky.vm.code.Instructions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Compiler {

  private Compiler() {}

  public static byte[] compile(InputStream is) throws ParseException, ValidationException {
    Parser parser = new Parser(is, StandardCharsets.UTF_8.name());
    Program program = parser.Start();
    IrProgram ir = ToIr.generate(program);
    List<Instructions.Instruction> instructions = CodeGen.codegen("program", ir);
    return Assembler.assemble(instructions);
  }
}
