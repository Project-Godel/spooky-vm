package se.jsannemo.spooky.compiler;

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.util.List;
import jsinterop.annotations.JsType;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.ir.IrGen;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.typecheck.TypeChecker;

@JsType(namespace = "spooky.compiler")
public final class Compiler {

  public static List<String> compile(String source) {
    Errors errs = new Errors();
    Ast.Program parsed = Parser.parse(Tokenizer.create(source), errs);
    Prog.Program program = TypeChecker.typeCheck(parsed, errs);
    IrGen.generateIr(program);
    return errs.errors().stream().map(Errors.Error::toString).collect(toImmutableList());
  }
}
