package se.jsannemo.spooky.vm;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.codegen.CodeGen;
import se.jsannemo.spooky.compiler.ir.IrProgram;
import se.jsannemo.spooky.compiler.ir.ToIr;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

public class FullProgramTest {

  @Test
  public void testParser() throws IOException, ValidationException, VmException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      Errors parseErr = new Errors();
      Parser parser =
          Parser.create(
              Tokenizer.create(
                  Files.readString(
                      Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8)),
              parseErr);

      Ast.Program parsed = parser.parse();
      if (tc.getFailure() == FailureMode.RUNTIME
          || tc.getFailure() == FailureMode.FAILURE_MODE_UNSPECIFIED) {
        Truth.assertWithMessage(tc.getName() + " parsing errors").that(parseErr.errors()).isEmpty();
        IrProgram ir = ToIr.generate(parsed);
        Executable exec = CodeGen.codegen(ir);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SpookyVm vm =
            SpookyVm.newBuilder(exec)
                .addStdLib(new PrintStream(bos))
                .setMemorySize(1000)
                .addExtern("echo", Calls.intToInt(Function.identity()))
                .build();
        for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
          ;
        String v = bos.toString();
        Truth.assertThat(v).isEqualTo(tc.getExpectedOutput());
      }
    }
  }
}
