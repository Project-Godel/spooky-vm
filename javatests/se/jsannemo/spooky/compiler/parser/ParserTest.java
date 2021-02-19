package se.jsannemo.spooky.compiler.parser;

import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ParserTest {

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
                      Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8),
                  new Errors()),
              parseErr);

      Ast.Program parsed = parser.parse();
      Ast.Program expected =
          TextFormat.parse(
              Files.readString(
                  Paths.get("test_programs/parse_trees/" + tc.getName() + ".parsetree"),
                  StandardCharsets.UTF_8),
              Ast.Program.class);
      ProtoTruth.assertThat(parsed).isEqualTo(expected);
      if (tc.getFailure() == FailureMode.PARSING) {
        Truth.assertWithMessage(tc.getName() + " parsing errors")
            .that(parseErr.errors())
            .isNotEmpty();
      } else if (tc.getFailure() != FailureMode.TOKENIZATION) {
        Truth.assertWithMessage(tc.getName() + " parsing errors").that(parseErr.errors()).isEmpty();
      }
    }
  }
}
