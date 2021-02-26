package se.jsannemo.spooky.compiler.ir;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.Prog;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;
import se.jsannemo.spooky.compiler.typecheck.TypeChecker;

public class IrGenTest {
  @Test
  public void testExamplePrograms() throws IOException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      if (tc.getFailure() != FailureMode.RUNTIME
          && tc.getFailure() != FailureMode.FAILURE_MODE_UNSPECIFIED) {
        continue;
      }
      Errors parseErr = new Errors();
      Ast.Program parsed =
          Parser.parse(
              Tokenizer.create(
                  Files.readString(
                      Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8)),
              parseErr);
      Prog.Program program = TypeChecker.typeCheck(parsed, parseErr);
      Truth.assertWithMessage(tc.getName() + " validation errors")
          .that(parseErr.errors())
          .isEmpty();
      System.out.println(tc.getName());
      System.out.println(IrGen.generateIr(program));
    }
  }
}
