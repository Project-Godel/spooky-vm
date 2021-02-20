package se.jsannemo.spooky.compiler.parser;

import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.compiler.ast.Ast;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Streaming tokenizer of Spooky source code.
 *
 * <p>Invalid tokens are generally pushed as {@link Ast.Token.Kind#UNEXPECTED} tokens. Some errors,
 * such as invalid escape characters in strings, are not pushed as unexpected tokens. They are only
 * reported as an error. Since tokenization is lazy, all errors may not be added until the token
 * stream is exhausted.
 */
public final class Tokenizer {

  private static final ImmutableMap<String, Ast.Token.Kind> KEYWORDS =
      ImmutableMap.<String, Ast.Token.Kind>builder()
          .put("true", Ast.Token.Kind.TRUE)
          .put("else", Ast.Token.Kind.ELSE)
          .put("extern", Ast.Token.Kind.EXTERN)
          .put("false", Ast.Token.Kind.FALSE)
          .put("for", Ast.Token.Kind.FOR)
          .put("func", Ast.Token.Kind.FUNC)
          .put("if", Ast.Token.Kind.IF)
          .put("return", Ast.Token.Kind.RETURN)
          .put("while", Ast.Token.Kind.WHILE)
          .put("struct", Ast.Token.Kind.STRUCT)
          .build();

  private final char[] input;

  // Positioning data of the *current character for consumption*, i.e. the one returned by peak()
  // and eat()
  private int pos = 0;
  private int line = 1;
  private int col = 1;
  // Position and text of the current token being constructed
  private Ast.Pos tokPos;
  private StringBuilder tokText;

  private Tokenizer(String input) {
    checkArgument(input != null);
    this.input = input.toCharArray();
  }

  /**
   * Returns the next token in the stream. If there are no more tokens available, {@link
   * se.jsannemo.spooky.compiler.ast.Ast.Token.Kind#EOF} tokens will be returned.
   */
  public Ast.Token next() {
    prepare();
    tokPos = Ast.Pos.newBuilder().setLine(line).setCol(col).setOffset(pos).build();
    tokText = new StringBuilder();
    if (end()) {
      return token(Ast.Token.Kind.EOF);
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
          return token(Ast.Token.Kind.AND);
        }
        return token(Ast.Token.Kind.UNEXPECTED);
      case '|':
        if (peek() == '|') {
          eat();
          return token(Ast.Token.Kind.OR);
        }
        return token(Ast.Token.Kind.UNEXPECTED);
      case '"':
        return stringLiteral();
      case '\'':
        return charLit();
      case '.':
        return token(Ast.Token.Kind.DOT);
      case ',':
        return token(Ast.Token.Kind.COMMA);
      case ';':
        return token(Ast.Token.Kind.SEMICOLON);
      case ':':
        return token(Ast.Token.Kind.COLON);
      case '?':
        return token(Ast.Token.Kind.QUESTION);
      case '[':
        return token(Ast.Token.Kind.LBRACKET);
      case ']':
        return token(Ast.Token.Kind.RBRACKET);
      case '(':
        return token(Ast.Token.Kind.LPAREN);
      case ')':
        return token(Ast.Token.Kind.RPAREN);
      case '{':
        return token(Ast.Token.Kind.LBRACE);
      case '}':
        return token(Ast.Token.Kind.RBRACE);
    }
    if ('0' <= nx && nx <= '9') {
      return intLit();
    }
    if (nx == '_' || isAlpha(nx)) {
      return idOrKeyword();
    }
    return token(Ast.Token.Kind.UNEXPECTED);
  }

  private Ast.Token stringLiteral() {
    while (peek() != '\"') {
      int nx = eat();
      // Strings are not multiline, so we treat a newline in a string as an unterminated string
      if (end() || nx == '\n') {
        return token(Ast.Token.Kind.UNTERMINATED_STRING_LIT);
      }
      // An \x escape character; eat next character too.
      if (nx == '\\') {
        eat();
      }
    }
    eat(); // Eat remaining "
    return token(Ast.Token.Kind.STRING_LIT);
  }

  private Ast.Token charLit() {
    while (peek() != '\'') {
      int nx = eat();
      // Chars are not multiline, so we treat a newline in a string as an unterminated char literal
      if (end() || nx == '\n') {
        return token(Ast.Token.Kind.UNTERMINATED_CHAR_LIT);
      }
      // An \x escape character; eat next character too.
      if (nx == '\\') {
        eat();
      }
    }
    eat(); // Eat remaining '
    return token(Ast.Token.Kind.CHAR_LIT);
  }

  private Ast.Token idOrKeyword() {
    while (isAlpha(peek()) || isDigit(peek()) || peek() == '_') {
      eat();
    }
    Ast.Token.Kind keyword = KEYWORDS.get(tokText.toString());
    if (keyword != null) {
      return token(keyword);
    }
    return token(Ast.Token.Kind.IDENTIFIER);
  }

  private Ast.Token intLit() {
    while (isDigit(peek())) {
      eat();
    }
    return token(Ast.Token.Kind.INT_LIT);
  }

  private Ast.Token plus() {
    if (peek() == '+') {
      return token(Ast.Token.Kind.INCREMENT);
    }
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.PLUS_EQUALS);
    }
    return token(Ast.Token.Kind.PLUS);
  }

  private Ast.Token minus() {
    int x = 1;
    if (peek() == '-') {
      return token(Ast.Token.Kind.DECREMENT);
    }
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.MINUS_EQUALS);
    }
    if (peek() == '>') {
      eat();
      return token(Ast.Token.Kind.ARROW);
    }
    return token(Ast.Token.Kind.MINUS);
  }

  private Ast.Token asterisk() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.TIMES_EQUALS);
    }
    return token(Ast.Token.Kind.ASTERISK);
  }

  private Ast.Token slash() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.DIV_EQUALS);
    }
    return token(Ast.Token.Kind.SLASH);
  }

  private Ast.Token percent() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.MOD_EQUALS);
    }
    return token(Ast.Token.Kind.PERCENT);
  }

  private Ast.Token exclaim() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.NOT_EQUALS);
    }
    return token(Ast.Token.Kind.EXCLAIM);
  }

  private Ast.Token less() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.LESS_EQUALS);
    }
    return token(Ast.Token.Kind.LESS);
  }

  private Ast.Token greater() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.GREATER_EQUALS);
    }
    return token(Ast.Token.Kind.GREATER);
  }

  private Ast.Token eq() {
    if (peek() == '=') {
      eat();
      return token(Ast.Token.Kind.EQUALS);
    }
    return token(Ast.Token.Kind.ASSIGN);
  }

  private void prepare() {
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

  private Ast.Token token(Ast.Token.Kind kind) {
    return Ast.Token.newBuilder()
        .setKind(kind)
        .setPosition(tokPos)
        .setText(tokText.toString())
        .build();
  }

  public static Tokenizer create(String input) {
    return new Tokenizer(input);
  }

  private static boolean isAlpha(int nx) {
    return ('a' <= nx && nx <= 'z') || ('A' <= nx && nx <= 'Z');
  }

  private static boolean isDigit(int nx) {
    return ('0' <= nx && nx <= '9');
  }
}
