package se.jsannemo.spooky.compiler.parser;

import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;
import se.jsannemo.spooky.compiler.testing.TestTokenization;
import se.jsannemo.spooky.compiler.testing.TokenUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;

public class TokenizerTest {

  @Test
  public void testExamplePrograms() throws IOException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      Tokenizer tok =
          Tokenizer.create(
              Files.readString(
                  Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8));
      TestTokenization serialized = TokenUtils.serialize(tok);
      for (Ast.Token t : serialized.getTokensList()) {
        assertThat(t.getKind()).isNotEqualTo(Ast.Token.Kind.KIND_UNSPECIFIED);
        Ast.Pos pos = t.getPosition();
        assertThat(pos.getLine()).isAtLeast(1);
        assertThat(pos.getEndLine()).isAtLeast(pos.getLine());

        assertThat(pos.getCol()).isAtLeast(1);
        assertThat(pos.getEndCol()).isAtLeast(pos.getCol());

        assertThat(pos.getOffset() == 0).isEqualTo(pos.getLine() == 1 && pos.getCol() == 1);
        assertThat(pos.getEndOffset()).isAtLeast(pos.getOffset());
      }
      TestTokenization expected =
          TextFormat.parse(
              Files.readString(
                  Paths.get("test_programs/tokenizations/" + tc.getName() + ".tokens"),
                  StandardCharsets.UTF_8),
              TestTokenization.class);
      ProtoTruth.assertThat(serialized).isEqualTo(expected);
    }
  }
}
