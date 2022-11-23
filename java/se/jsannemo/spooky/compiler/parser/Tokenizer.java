package se.jsannemo.spooky.compiler.parser;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.compiler.ast.SourcePos;
import se.jsannemo.spooky.compiler.ast.SourceRange;
import se.jsannemo.spooky.compiler.ast.Token;
import se.jsannemo.spooky.compiler.ast.TokenKind;

/**
 * Streaming tokenizer of Spooky source code.
 *
 * <p>Invalid tokens are generally pushed as {@link TokenKind#UNEXPECTED} tokens. Some errors, such
 * as invalid escape characters in strings, are not pushed as unexpected tokens. They are only
 * reported as an error. Since tokenization is lazy, all errors may not be added until the token
 * stream is exhausted.
 */
public final class Tokenizer {

  private static final ImmutableMap<String, TokenKind> KEYWORDS =
      ImmutableMap.<String, TokenKind>builder()
          .put("true", TokenKind.TRUE)
          .put("else", TokenKind.ELSE)
          .put("extern", TokenKind.EXTERN)
          .put("false", TokenKind.FALSE)
          .put("for", TokenKind.FOR)
          .put("func", TokenKind.FUNC)
          .put("if", TokenKind.IF)
          .put("return", TokenKind.RETURN)
          .put("while", TokenKind.WHILE)
          .put("struct", TokenKind.STRUCT)
          .put("default", TokenKind.DEFAULT)
          .build();

  private final char[] input;

  // Positioning data of the *current character for consumption*, i.e. the one returned by peak().
  private int pos = 0;
  private int line = 1;
  private int col = 1;
  // Positioning data of the *last consumed character*, i.e. the one last returned by eat().
  private int eatPos = 0;
  private int eatLine = 1;
  private int eatCol = 1;
  // Position and text of the current token being constructed.
  private SourcePos tokPos;
  private StringBuilder tokText = new StringBuilder();

  private Tokenizer(char[] input) {
    checkArgument(input != null);
    this.input = input;
  }

  /**
   * Returns the next token in the stream. If there are no more tokens available, {@link
   * se.jsannemo.spooky.compiler.ast.TokenKind#EOF} tokens will be returned.
   */
  public Token next() {
    ignoreBefore();
    tokPos = SourcePos.of(line, col, pos);
    tokText = new StringBuilder();
    if (end()) {
      return token(TokenKind.EOF);
    }
    // Special characters
    char nx = eat();
    switch (nx) {
      case '+':
        return plus();
      case '-':
        return minus();
      case '*':
        return asterisk();
      case '/':
        return slash();
      case '%':
        return percent();
      case '!':
        return exclaim();
      case '<':
        return less();
      case '>':
        return greater();
      case '=':
        return eq();
      case '&':
        if (peek() == '&') {
          eat();
          return token(TokenKind.AND);
        }
        return token(TokenKind.UNEXPECTED);
      case '|':
        if (peek() == '|') {
          eat();
          return token(TokenKind.OR);
        }
        return token(TokenKind.UNEXPECTED);
      case '"':
        return stringLiteral();
      case '\'':
        return charLit();
      case '.':
        return dot();
      case ',':
        return token(TokenKind.COMMA);
      case ';':
        return token(TokenKind.SEMICOLON);
      case ':':
        return token(TokenKind.COLON);
      case '?':
        return token(TokenKind.QUESTION);
      case '[':
        return token(TokenKind.LBRACKET);
      case ']':
        return token(TokenKind.RBRACKET);
      case '(':
        return token(TokenKind.LPAREN);
      case ')':
        return token(TokenKind.RPAREN);
      case '{':
        return token(TokenKind.LBRACE);
      case '}':
        return token(TokenKind.RBRACE);
    }
    if ('0' <= nx && nx <= '9') {
      return intLit();
    }
    if (nx == '_' || isAlpha(nx)) {
      return idOrKeyword();
    }
    return token(TokenKind.UNEXPECTED);
  }

  private Token dot() {
    if (peek(0) != '.' || peek(1) != '.') {
      return token(TokenKind.DOT);
    }
    eat();
    eat();
    return token(TokenKind.ELLIPSIS);
  }

  private Token stringLiteral() {
    while (peek() != '\"') {
      int nx = eat();
      // Strings are not multiline, so we treat a newline in a string as an unterminated string
      if (end() || nx == '\n') {
        return token(TokenKind.UNTERMINATED_STRING_LIT);
      }
      // An \x escape character; eat next character too.
      if (nx == '\\') {
        eat();
      }
    }
    eat(); // Eat remaining "
    return token(TokenKind.STRING_LIT);
  }

  private Token charLit() {
    while (peek() != '\'') {
      int nx = eat();
      // Chars are not multiline, so we treat a newline in a string as an unterminated char literal
      if (end() || nx == '\n') {
        return token(TokenKind.UNTERMINATED_CHAR_LIT);
      }
      // An \x escape character; eat next character too.
      if (nx == '\\') {
        eat();
      }
    }
    eat(); // Eat remaining '
    return token(TokenKind.CHAR_LIT);
  }

  private Token idOrKeyword() {
    while (isAlpha(peek()) || isDigit(peek()) || peek() == '_') {
      eat();
    }
    TokenKind keyword = KEYWORDS.get(tokText.toString());
    if (keyword != null) {
      return token(keyword);
    }
    return token(TokenKind.IDENTIFIER);
  }

  private Token intLit() {
    while (isDigit(peek())) {
      eat();
    }
    return token(TokenKind.INT_LIT);
  }

  private Token plus() {
    if (peek() == '+') {
      eat();
      return token(TokenKind.INCREMENT);
    }
    if (peek() == '=') {
      eat();
      return token(TokenKind.PLUS_EQUALS);
    }
    return token(TokenKind.PLUS);
  }

  private Token minus() {
    if (peek() == '-') {
      eat();
      return token(TokenKind.DECREMENT);
    }
    if (peek() == '=') {
      eat();
      return token(TokenKind.MINUS_EQUALS);
    }
    if (peek() == '>') {
      eat();
      return token(TokenKind.ARROW);
    }
    return token(TokenKind.MINUS);
  }

  private Token asterisk() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.TIMES_EQUALS);
    }
    return token(TokenKind.ASTERISK);
  }

  private Token slash() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.DIV_EQUALS);
    }
    return token(TokenKind.SLASH);
  }

  private Token percent() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.MOD_EQUALS);
    }
    return token(TokenKind.PERCENT);
  }

  private Token exclaim() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.NOT_EQUALS);
    }
    return token(TokenKind.EXCLAIM);
  }

  private Token less() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.LESS_EQUALS);
    }
    return token(TokenKind.LESS);
  }

  private Token greater() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.GREATER_EQUALS);
    }
    return token(TokenKind.GREATER);
  }

  private Token eq() {
    if (peek() == '=') {
      eat();
      return token(TokenKind.EQUALS);
    }
    return token(TokenKind.ASSIGN);
  }

  // Eats any characters that should be ignored between tokens.
  private void ignoreBefore() {
    while (true) {
      // Ignore whitespace
      while (Character.isWhitespace(peek())) {
        eat();
      }
      // Ignore comments
      if (peek() == '/' && peek(1) == '/') {
        eatUntil('\n');
        continue; // We may have new whitespace and comments after this
      }
      break;
    }
  }

  private void eatUntil(char c) {
    while (!end()) {
      if (eat() == c) break;
    }
  }

  private char eat() {
    eatPos = pos;
    eatLine = line;
    eatCol = col;
    char ch = input[pos++];
    if (ch > 127) {
      throw new IllegalArgumentException("Only ASCII source is supported.");
    }
    tokText.append(ch);
    if (ch == '\n') {
      line++;
      col = 1;
    } else {
      col++;
    }
    return ch;
  }

  private int peek(int lookahead) {
    return pos + lookahead >= input.length ? -1 : input[pos + lookahead];
  }

  private int peek() {
    return peek(0);
  }

  private boolean end() {
    return pos == input.length;
  }

  private Token token(TokenKind kind) {
    return Token.create(
        kind,
        tokText.toString(),
        SourceRange.between(tokPos, SourcePos.of(eatLine, eatCol, eatPos)));
  }

  private static boolean isAlpha(int nx) {
    return ('a' <= nx && nx <= 'z') || ('A' <= nx && nx <= 'Z');
  }

  private static boolean isDigit(int nx) {
    return ('0' <= nx && nx <= '9');
  }

  public static Tokenizer create(String input) {
    return new Tokenizer(input.toCharArray());
  }
}
