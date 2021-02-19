package se.jsannemo.spooky.compiler;

import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.codegen.CodeGen;
import se.jsannemo.spooky.compiler.ir.IrProgram;
import se.jsannemo.spooky.compiler.ir.ToIr;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.vm.Executable;

public final class Compiler {

  private Compiler() {}

  public static byte[] compile(String source) throws ValidationException {
    Errors err = new Errors();
    Parser parser = Parser.create(Tokenizer.create(source), err);
    Ast.Program parse = parser.parse();
    if (!err.errors().isEmpty()) {
      throw new ValidationException(err.errors().get(0).msg, err.errors().get(0).position);
    }
    IrProgram ir = ToIr.generate(parse);
    Executable exec = CodeGen.codegen(ir);
    return exec.toByteArray();
  }
}
