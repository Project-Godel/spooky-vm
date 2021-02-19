package se.jsannemo.spooky.compiler.parser;

import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.TextFormat;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.testing.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TokenizerTest {

  @Test
  public void testTokenizer() throws IOException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      Errors err = new Errors();
      Tokenizer tok =
          Tokenizer.create(
              Files.readString(
                  Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8),
              err);
      TestTokenization serialized = TokenUtils.serialize(tok);
      TestTokenization expected =
          TextFormat.parse(
              Files.readString(
                  Paths.get("test_programs/tokenizations/" + tc.getName() + ".tokens"),
                  StandardCharsets.UTF_8),
              TestTokenization.class);
      ProtoTruth.assertThat(serialized).isEqualTo(expected);
      Truth.assertThat(!err.errors().isEmpty())
          .isEqualTo(tc.getFailure() == FailureMode.TOKENIZATION);
    }
  }
}
