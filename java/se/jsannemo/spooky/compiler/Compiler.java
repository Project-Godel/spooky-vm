package se.jsannemo.spooky.compiler;

import jsinterop.annotations.JsType;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.ir.IrGen;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.typecheck.TypeChecker;

@JsType(namespace = "spooky.compiler")
public final class Compiler {

  public static Ir.Program compile(String source) {
    Errors errs = new Errors();
    Ast.Program parsed = Parser.parse(Tokenizer.create(source), errs);
    Prog.Program program = TypeChecker.typeCheck(parsed, errs);
    if (errs.errors().isEmpty()) {
      return IrGen.generateIr(program);
    }
    throw new RuntimeException(errs.errors().get(0).toString());
  }
}
