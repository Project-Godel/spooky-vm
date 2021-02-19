package se.jsannemo.spooky.compiler.testing;

import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.parser.Tokenizer;

public final class TokenUtils {

  public static TestTokenization serialize(Tokenizer tokenizer) {
    TestTokenization.Builder tokenization = TestTokenization.newBuilder();
    for (Ast.Token t = tokenizer.next(); t.getKind() != Ast.Token.Kind.EOF; t = tokenizer.next()) {
      tokenization.addTokens(t);
    }
    return tokenization.build();
  }
}
