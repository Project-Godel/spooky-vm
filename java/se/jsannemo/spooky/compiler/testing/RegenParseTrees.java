package se.jsannemo.spooky.compiler.testing;

import com.google.protobuf.TextFormat;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class RegenParseTrees {
  public static void main(String[] args) throws IOException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      Parser parser =
          Parser.create(
              Tokenizer.create(
                  Files.readString(
                      Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8),
                  new Errors()),
              new Errors());
      String textProto = TextFormat.printer().printToString(parser.parse());
      Files.writeString(
          Paths.get("test_programs/parse_trees/" + tc.getName() + ".parsetree"),
          textProto,
          StandardCharsets.UTF_8,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.CREATE);
    }
  }
}
