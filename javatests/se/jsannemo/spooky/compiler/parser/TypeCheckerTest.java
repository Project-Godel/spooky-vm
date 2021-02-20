package se.jsannemo.spooky.compiler.parser;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;
import se.jsannemo.spooky.compiler.typecheck.TypeChecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TypeCheckerTest {

  @Test
  public void testParser() throws IOException {
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
      if (!parseErr.errors().isEmpty()) {
        Truth.assertThat(tc.getFailure()).isNotEqualTo(FailureMode.VALIDATION);
        continue;
      }
      TypeChecker.typeCheck(parsed, parseErr);
      if (tc.getFailure() == FailureMode.VALIDATION) {
        Truth.assertWithMessage(tc.getName() + " validation errors")
            .that(parseErr.errors())
            .isNotEmpty();
      } else {
        Truth.assertWithMessage(tc.getName() + " validation errors").that(parseErr.errors()).isEmpty();
      }
    }
  }
}
