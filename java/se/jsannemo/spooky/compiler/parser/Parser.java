package se.jsannemo.spooky.compiler.parser;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import jsinterop.annotations.JsMethod;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Assignment;
import se.jsannemo.spooky.compiler.ast.BinaryExpr;
import se.jsannemo.spooky.compiler.ast.BinaryOp;
import se.jsannemo.spooky.compiler.ast.Block;
import se.jsannemo.spooky.compiler.ast.Conditional;
import se.jsannemo.spooky.compiler.ast.Expression;
import se.jsannemo.spooky.compiler.ast.Func;
import se.jsannemo.spooky.compiler.ast.FuncCall;
import se.jsannemo.spooky.compiler.ast.FuncDecl;
import se.jsannemo.spooky.compiler.ast.Identifier;
import se.jsannemo.spooky.compiler.ast.Loop;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.ast.ReturnValue;
import se.jsannemo.spooky.compiler.ast.Select;
import se.jsannemo.spooky.compiler.ast.SourcePos;
import se.jsannemo.spooky.compiler.ast.SourceRange;
import se.jsannemo.spooky.compiler.ast.Statement;
import se.jsannemo.spooky.compiler.ast.Ternary;
import se.jsannemo.spooky.compiler.ast.Token;
import se.jsannemo.spooky.compiler.ast.TokenKind;
import se.jsannemo.spooky.compiler.ast.Type;
import se.jsannemo.spooky.compiler.ast.UnaryExpr;
import se.jsannemo.spooky.compiler.ast.UnaryOp;
import se.jsannemo.spooky.compiler.ast.Value;
import se.jsannemo.spooky.compiler.ast.VarDecl;
import se.jsannemo.spooky.util.CircularQueue;

/**
 * Parser of Spooky code. Tokenization (and thus input to the parser) is set up in another class,
 * {@link Tokenizer}.
 */
public final class Parser {

  /** When a statement parse fails, tokens are ignored until one of the following are reached. */
  private static final ImmutableSet<TokenKind> STATEMENT_FAILURE_ENDS =
      ImmutableSet.of(TokenKind.SEMICOLON, TokenKind.RBRACE, TokenKind.LBRACE);
  /** When a condition parse fails, tokens are ignored until one of the following are reached. */
  private static final ImmutableSet<TokenKind> COND_FAILURE_ENDS =
      ImmutableSet.of(TokenKind.SEMICOLON, TokenKind.RBRACE, TokenKind.LBRACE, TokenKind.RPAREN);
  /** When an expression parse fails, tokens are ignored until one of the following are reached. */
  private static final ImmutableSet<TokenKind> EXPRESSION_FAILURE_ENDS =
      ImmutableSet.of(
          TokenKind.SEMICOLON,
          TokenKind.COMMA,
          TokenKind.RBRACE,
          TokenKind.LBRACE,
          TokenKind.RBRACKET,
          TokenKind.RPAREN);

  private static final ImmutableSet<TokenKind> TYPES =
      // TODO: float support
      ImmutableSet.of(
          TokenKind.INT, TokenKind.BOOL, TokenKind.CHAR, TokenKind.STRUCT, TokenKind.VOID);

  private final Tokenizer tokens;
  private final CircularQueue<Token> lookahead = CircularQueue.withCapacity(3);
  private final Errors errors;
  private final Program.Builder program = Program.builder().setValid(true);
  // The last token that was eat()en.
  private Token last;

  private Parser(Tokenizer tokens, Errors errors) {
    this.tokens = tokens;
    this.errors = errors;
  }

  private Program parse() {
    while (peek().kind() != TokenKind.EOF) {
      topLevel(program);
    }
    return program.build();
  }

  private void topLevel(Program.Builder program) {
    boolean errored = false;
    while (true) {
      TokenKind nx = peek().kind();
      if (nx == TokenKind.EOF) {
        return;
      } else if (TYPES.contains(nx)) {
        declaration(program);
        continue;
      } else if (nx == TokenKind.EXTERN) {
        extern().ifPresent(program::addExtern);
        continue;
      }
      // We only want to error out for the first invalid top-level token to avoid noise.
      if (!errored) {
        errPeek("expected func, extern or variable declaration.");
        errored = true;
      }
      eat();
    }
  }

  private void declaration(Program.Builder program) {
    // All declarations are <type> <name>  followed by = for globals or ( for functions
    if (peek(2).kind() == TokenKind.ASSIGN) {
      varDecl().ifPresent(statement -> program.addGlobal(statement.varDecl()));
      eatIfExpected("expected ;", TokenKind.SEMICOLON);
    } else if (peek(2).kind() == TokenKind.LPAREN) {
      func().ifPresent(program::addFunction);
    } else {
      Token type = eat();
      eatLine(type);
      errAt("expected function or global declaration", type);
    }
  }

  private Optional<FuncDecl> funcDecl() {
    Optional<Type> returnTypeOpt = type();
    if (returnTypeOpt.isEmpty()) {
      eatLine(last);
      return Optional.empty();
    }
    Type returnType = returnTypeOpt.get();
    SourceRange pos = returnType.pos();
    Optional<Identifier> name = identifier("expected function name");
    Token lpar = eatIfExpected("expected (", TokenKind.LPAREN);
    if (lpar == null) {
      eatLine(last);
      return Optional.empty();
    }
    ImmutableList<FuncDecl.FuncParam> params = parameterList();
    Token rpar = eatIfExpected("expected )", TokenKind.RPAREN);
    if (rpar == null) {
      eatLine(last);
      return Optional.empty();
    }
    pos = pos.extend(rpar.pos());
    return Optional.of(
        FuncDecl.create(
            name.orElse(missingIdentifier(returnType.pos().to(), lpar.pos().from())),
            params,
            returnType,
            pos));
  }

  private ImmutableList<FuncDecl.FuncParam> parameterList() {
    ImmutableList.Builder<FuncDecl.FuncParam> builder = ImmutableList.builder();
    while (TYPES.contains(peek().kind())) {
      Optional<Type> typeOpt = type();
      if (typeOpt.isPresent()) {
        Optional<Identifier> nameOpt = identifier("expected parameter name");
        if (nameOpt.isPresent()) {
          Type type = typeOpt.get();
          Identifier name = nameOpt.get();
          builder.add(FuncDecl.FuncParam.create(name, type, name.pos().extend(type.pos())));
        } else {
          eatLineUntil(last, EXPRESSION_FAILURE_ENDS);
        }
      } else {
        eatLineUntil(last, EXPRESSION_FAILURE_ENDS);
      }
      if (peek().kind() == TokenKind.COMMA) {
        eat();
      }
    }
    return builder.build();
  }

  private Optional<FuncDecl> extern() {
    checkState(eat().kind() == TokenKind.EXTERN);
    return funcDecl();
  }

  private Optional<Func> func() {
    Optional<FuncDecl> maybeDecl = funcDecl();
    if (maybeDecl.isEmpty()) {
      return Optional.empty();
    }
    FuncDecl decl = maybeDecl.get();
    Optional<Statement> bodyOpt = block();
    if (bodyOpt.isEmpty()) {
      return Optional.empty();
    }
    Statement body = bodyOpt.get();
    return Optional.of(Func.create(decl, body, decl.pos().extend(body.pos())));
  }

  private Optional<Statement> block() {
    Token lbrace = eatIfExpected("expected block", TokenKind.LBRACE);
    if (lbrace == null) {
      return Optional.empty();
    }
    ImmutableList.Builder<Statement> statements = ImmutableList.builder();
    SourceRange pos = lbrace.pos();
    while (true) {
      Token nx = peek();
      if (nx.kind() == TokenKind.RBRACE) {
        pos = pos.extend(eat().pos());
        break;
      }
      if (nx.kind() == TokenKind.EOF) {
        errAt("Unterminated block", lbrace);
        break;
      }
      Optional<Statement> st = statement();
      if (st.isEmpty()) {
        eatUntil(TokenKind.RBRACE);
        return Optional.empty();
      }
      statements.add(st.get());
    }
    return Optional.of(Statement.ofBlock(Block.create(statements.build(), pos)));
  }

  private Optional<Statement> statement() {
    TokenKind nx = peek().kind();
    if (nx == TokenKind.SEMICOLON) {
      // ; is allowed to end null-statements
      Token semi = eat();
      return Optional.of(Statement.ofBlock(Block.empty(semi.pos())));
    } else if (nx == TokenKind.IF) {
      return conditional();
    } else if (nx == TokenKind.FOR) {
      return forLoop();
    } else if (nx == TokenKind.WHILE) {
      return whileLoop();
    } else if (nx == TokenKind.RETURN) {
      return returnStmt();
    } else if (nx == TokenKind.LBRACE) {
      return block();
    } else if (TYPES.contains(peek().kind())) {
      Optional<Statement> statement = varDecl();
      finishLine();
      return statement;
    } else {
      Optional<Statement> statement = exprStatement();
      finishLine();
      return statement;
    }
  }

  private Optional<Statement> varDecl() {
    Optional<Type> type = type();
    if (type.isEmpty()) {
      eatLine(last);
      return Optional.empty();
    }
    Optional<Identifier> nameOpt = identifier("expected variable name");
    if (nameOpt.isEmpty()) {
      return Optional.empty();
    }
    Identifier name = nameOpt.get();
    if (eatIfExpected("expected =", TokenKind.ASSIGN) == null) {
      eatLine(last);
      return Optional.empty();
    }
    Optional<Expression> value = initVal();
    if (value.isEmpty()) {
      eatLine(last);
      return Optional.empty();
    }
    Expression init = value.get();
    return Optional.of(
        Statement.ofVarDecl(
            VarDecl.create(nameOpt.get(), type.get(), init, name.pos().extend(init.pos()))));
  }

  private Optional<Expression> initVal() {
    return expression();
  }

  private Optional<Statement> returnStmt() {
    Token retTok = eatIfExpected("expected return", TokenKind.RETURN);
    if (retTok == null) {
      return Optional.empty();
    }
    if (peek().kind() == TokenKind.SEMICOLON) {
      return Optional.of(
          Statement.ofReturnValue(ReturnValue.create(Optional.empty(), retTok.pos())));
    }
    Optional<Expression> valueOpt = expression();
    if (valueOpt.isEmpty()) {
      return Optional.empty();
    }
    Expression value = valueOpt.get();
    Token token = eatIfExpected("expected ;", TokenKind.SEMICOLON);
    return Optional.of(
        Statement.ofReturnValue(
            ReturnValue.create(
                Optional.of(value), retTok.pos().extend(value.pos()).extend(token))));
  }

  private Optional<Statement> whileLoop() {
    Token whileTok = eatIfExpected("expected while", TokenKind.WHILE);
    if (whileTok == null) {
      return Optional.empty();
    }
    if (eatIfExpected("expected (", TokenKind.LPAREN) == null) {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
      return Optional.empty();
    }
    Optional<Expression> cond = expression();
    if (cond.isEmpty()) {
      eatLineUntil(last, COND_FAILURE_ENDS);
    }
    eatIfExpected("expected )", TokenKind.RPAREN);
    Optional<Statement> bodyOpt = statement();
    if (bodyOpt.isEmpty()) {
      return Optional.empty();
    }

    Statement body = bodyOpt.get();
    Loop.Builder loop = Loop.builder().setBody(body).setPos(whileTok.pos().extend(body.pos()));
    cond.ifPresent(loop::setCondition);
    loop.setBody(body);
    return Optional.of(Statement.ofLoop(loop.build()));
  }

  private Optional<Statement> forLoop() {
    Token forTok = eatIfExpected("expected for", TokenKind.FOR);
    if (forTok == null) {
      return Optional.empty();
    }
    Token lpar = eatIfExpected("expected (", TokenKind.LPAREN);
    if (lpar == null) {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
      return Optional.empty();
    }
    Optional<Statement> init = Optional.empty();
    if (peek().kind() != TokenKind.SEMICOLON) {
      init = simpleStatement();
      if (init.isEmpty()) {
        eatLineUntil(last, COND_FAILURE_ENDS);
      }
    }
    eatIfExpected("expected ;", TokenKind.SEMICOLON);
    Optional<Expression> cond = Optional.empty();
    if (peek().kind() != TokenKind.SEMICOLON) {
      cond = expression();
      if (cond.isEmpty()) {
        eatLineUntil(last, COND_FAILURE_ENDS);
      }
    }
    eatIfExpected("expected ;", TokenKind.SEMICOLON);
    Optional<Statement> increment = Optional.empty();
    if (peek().kind() != TokenKind.RPAREN) {
      increment = exprStatement();
      if (increment.isEmpty()) {
        eatLineUntil(last, COND_FAILURE_ENDS);
      }
    }
    eatIfExpected("expected )", TokenKind.RPAREN);
    Optional<Statement> bodyOpt = statement();
    if (bodyOpt.isEmpty()) {
      return Optional.empty();
    }

    Statement body = bodyOpt.get();
    Loop.Builder loop = Loop.builder().setBody(body).setPos(forTok.pos().extend(body.pos()));
    init.ifPresent(loop::setInit);
    cond.ifPresent(loop::setCondition);
    increment.ifPresent(loop::setIncrement);
    loop.setBody(body);
    return Optional.of(Statement.ofLoop(loop.build()));
  }

  private Optional<Statement> simpleStatement() {
    if (TYPES.contains(peek().kind())) {
      return varDecl();
    }
    return exprStatement();
  }

  private Optional<Statement> conditional() {
    Token ifTok = eatIfExpected("expected if", TokenKind.IF);
    if (ifTok == null) {
      return Optional.empty();
    }
    if (eatIfExpected("expected (", TokenKind.LPAREN) == null) {
      eatLine(last);
    }
    Optional<Expression> cond = expression();
    if (eatIfExpected("expected )", TokenKind.RPAREN) == null) {
      eatLine(last);
    }
    Optional<Statement> body = statement();
    Optional<Statement> elseBody = Optional.empty();
    if (peek().kind() == TokenKind.ELSE) {
      eat();
      elseBody = statement();
    }
    if (cond.isEmpty() || body.isEmpty()) {
      return Optional.empty();
    }
    Expression condExpr = cond.get();
    SourceRange range = condExpr.pos();
    Statement bodyStmt = body.get();
    if (elseBody.isPresent()) {
      range.extend(elseBody.get().pos());
    } else {
      range.extend(bodyStmt.pos());
    }
    return Optional.of(
        Statement.ofConditional(Conditional.create(condExpr, bodyStmt, elseBody, range)));
  }

  private Optional<Statement> exprStatement() {
    Optional<Expression> maybeExpr = expression();
    if (maybeExpr.isEmpty()) {
      return Optional.empty();
    }
    Expression expr = maybeExpr.get();
    return Optional.of(Statement.ofExpression(expr));
  }

  private Optional<Expression> expression() {
    return assignment();
  }

  private static final ImmutableMap<TokenKind, BinaryOp> assignments =
      ImmutableMap.<TokenKind, BinaryOp>builder()
          .put(TokenKind.PLUS_EQUALS, BinaryOp.ADD)
          .put(TokenKind.MINUS_EQUALS, BinaryOp.SUBTRACT)
          .put(TokenKind.DIV_EQUALS, BinaryOp.DIVIDE)
          .put(TokenKind.TIMES_EQUALS, BinaryOp.MULTIPLY)
          .put(TokenKind.MOD_EQUALS, BinaryOp.MODULO)
          .build();

  private Optional<Expression> assignment() {
    Optional<Expression> lhs = ternary();
    if (lhs.isPresent()
        && (peek().kind() == TokenKind.ASSIGN || assignments.containsKey(peek().kind()))) {
      Expression reference = lhs.get();
      Token opTok = eat();
      Optional<BinaryOp> op = Optional.ofNullable(assignments.get(opTok.kind()));
      Optional<Expression> rhs = assignment();
      if (rhs.isEmpty()) {
        return Optional.empty();
      }
      Expression value = rhs.get();
      return Optional.of(
          Expression.ofAssignment(
              Assignment.create(reference, value, op, reference.pos().extend(value.pos()))));
    }
    return lhs;
  }

  private Optional<Expression> ternary() {
    Optional<Expression> e = orExpr();
    if (e.isPresent() && peek().kind() == TokenKind.QUESTION) {
      Expression cond = e.get();
      eat();
      Optional<Expression> leftOpt = ternary();
      if (leftOpt.isEmpty()) {
        return Optional.empty();
      }
      if (eatIfExpected("expected :", TokenKind.COLON) == null) {
        return Optional.empty();
      }
      Optional<Expression> rightOpt = ternary();
      if (rightOpt.isEmpty()) {
        return Optional.empty();
      }
      Expression right = rightOpt.get();
      return Optional.of(
          Expression.ofConditional(
              Ternary.create(cond, leftOpt.get(), right, cond.pos().extend(right.pos()))));
    }
    return e;
  }

  private static final ImmutableMap<TokenKind, BinaryOp> BIN_OPS =
      ImmutableMap.<TokenKind, BinaryOp>builder()
          .put(TokenKind.OR, BinaryOp.OR)
          .put(TokenKind.AND, BinaryOp.AND)
          .put(TokenKind.PLUS, BinaryOp.ADD)
          .put(TokenKind.MINUS, BinaryOp.SUBTRACT)
          .put(TokenKind.SLASH, BinaryOp.DIVIDE)
          .put(TokenKind.ASTERISK, BinaryOp.MULTIPLY)
          .put(TokenKind.PERCENT, BinaryOp.MODULO)
          .put(TokenKind.EQUALS, BinaryOp.EQUALS)
          .put(TokenKind.NOT_EQUALS, BinaryOp.NOT_EQUALS)
          .put(TokenKind.LESS, BinaryOp.LESS_THAN)
          .put(TokenKind.GREATER, BinaryOp.GREATER_THAN)
          .put(TokenKind.LESS_EQUALS, BinaryOp.LESS_EQUALS)
          .put(TokenKind.GREATER_EQUALS, BinaryOp.GREATER_EQUALS)
          .build();

  private Optional<Expression> leftAssociative(
      Supplier<Optional<Expression>> next, TokenKind... opKinds) {
    Optional<Expression> e = next.get();
    while (e.isPresent() && Arrays.stream(opKinds).anyMatch(t -> t == peek().kind())) {
      Expression lhs = e.get();
      TokenKind opKind = eat().kind();
      Optional<Expression> rhsOpt = next.get();
      if (rhsOpt.isEmpty()) {
        return Optional.empty();
      }
      Expression rhs = rhsOpt.get();
      e =
          Optional.of(
              Expression.ofBinary(
                  BinaryExpr.create(lhs, rhs, BIN_OPS.get(opKind), lhs.pos().extend(rhs.pos()))));
    }
    return e;
  }

  private Optional<Expression> orExpr() {
    return leftAssociative(this::andExpr, TokenKind.OR);
  }

  private Optional<Expression> andExpr() {
    return leftAssociative(this::equalityExpr, TokenKind.AND);
  }

  private Optional<Expression> equalityExpr() {
    return leftAssociative(this::cmpExpr, TokenKind.EQUALS, TokenKind.NOT_EQUALS);
  }

  private Optional<Expression> cmpExpr() {
    return leftAssociative(
        this::addExpr,
        TokenKind.LESS,
        TokenKind.LESS_EQUALS,
        TokenKind.GREATER,
        TokenKind.GREATER_EQUALS);
  }

  private Optional<Expression> addExpr() {
    return leftAssociative(this::mulExpr, TokenKind.PLUS, TokenKind.MINUS);
  }

  private Optional<Expression> mulExpr() {
    return leftAssociative(this::unaryExpr, TokenKind.SLASH, TokenKind.ASTERISK, TokenKind.PERCENT);
  }

  private static final ImmutableMap<TokenKind, UnaryOp> UNARY_OPS =
      ImmutableMap.of(
          TokenKind.MINUS,
          UnaryOp.NEGATE,
          TokenKind.EXCLAIM,
          UnaryOp.NOT,
          TokenKind.INCREMENT,
          UnaryOp.PREFIX_INCREMENT,
          TokenKind.DECREMENT,
          UnaryOp.PREFIX_DECREMENT);

  private Optional<Expression> unaryExpr() {
    if (peek().kind() == TokenKind.MINUS && peek(1).kind() == TokenKind.INT_LIT) {
      // Was actually negative constant, not unary operator
      SourceRange pos = eat().pos();
      Token intLit = eat();
      String text = intLit.text();
      int lit = 0;
      try {
        lit = Integer.parseInt("-" + text);
      } catch (NumberFormatException nfe) {
        errLast("Integer " + text + " out of range");
        return Optional.empty();
      }
      return Optional.of(Expression.ofValue(Value.ofIntLit(lit, pos.extend(intLit.pos()))));
    } else if (UNARY_OPS.containsKey(peek().kind())) {
      Token tok = eat();
      UnaryOp op = UNARY_OPS.get(tok.kind());
      Optional<Expression> expression = unaryExpr();
      if (expression.isPresent()) {
        Expression operand = expression.get();
        return Optional.of(
            Expression.ofUnary(UnaryExpr.create(operand, op, tok.pos().extend(operand.pos()))));
      }
      return Optional.empty();
    }
    return unary2Expr();
  }

  private Optional<Expression> unary2Expr() {
    Optional<Expression> e;
    if (peek().kind() != TokenKind.IDENTIFIER) {
      e = literal();
    } else if (peek(1).kind() == TokenKind.LPAREN) {
      e = funcCall();
    } else {
      e = reference();
    }
    while (e.isPresent()) {
      Expression lhs = e.get();
      TokenKind next = peek().kind();
      if (next == TokenKind.INCREMENT) {
        Token t = eat();
        e =
            Optional.of(
                Expression.ofUnary(
                    UnaryExpr.create(lhs, UnaryOp.POSTFIX_INCREMENT, lhs.pos().extend(t.pos()))));
      } else if (next == TokenKind.DECREMENT) {
        Token t = eat();
        e =
            Optional.of(
                Expression.ofUnary(
                    UnaryExpr.create(lhs, UnaryOp.POSTFIX_DECREMENT, lhs.pos().extend(t.pos()))));
      } else if (false && next == TokenKind.LBRACKET) {
        // TODO: enable when arrays are supported
        e = arrayIndex(e.get());
      } else if (false && next == TokenKind.DOT && peek(1).kind() == TokenKind.IDENTIFIER) {
        // TODO: enable when structs are supported
        eat();
        e = select(e.get());
      } else if (next == TokenKind.LPAREN) {
        errPeek("expected function name");
        eatLineUntil(last, STATEMENT_FAILURE_ENDS);
        break;
      } else {
        break;
      }
    }
    return e;
  }

  private Optional<Expression> arrayIndex(Expression lhs) {
    eat(); // [
    Optional<Expression> rhs = expression();
    if (rhs.isEmpty()) {
      return Optional.empty();
    }
    Token token = eatIfExpected("expected ]", TokenKind.RBRACKET);
    if (token == null) {
      return Optional.empty();
    }
    return Optional.of(
        Expression.ofBinary(
            BinaryExpr.create(
                lhs, rhs.get(), BinaryOp.ARRAY_ACCESS, lhs.pos().extend(token.pos()))));
  }

  private Optional<Expression> funcCall() {
    Identifier funcName =
        identifier("expected identifier")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "funcCall() must only be called when an identifier has been peeked"));
    eat(); // The following LPAREN
    ImmutableList.Builder<Expression> params = ImmutableList.builder();
    while (peek().kind() != TokenKind.RPAREN) {
      Optional<Expression> param = expression();
      if (param.isEmpty()) {
        eatLineUntil(last, ImmutableSet.of(TokenKind.RPAREN));
        return Optional.empty();
      }
      params.add(param.get());
      if (peek().kind() != TokenKind.COMMA) {
        break;
      }
      eat();
    }
    eatIfExpected("expected )", TokenKind.RPAREN);
    return Optional.of(
        Expression.ofCall(
            FuncCall.create(funcName, params.build(), funcName.pos().extend(last.pos()))));
  }

  private Optional<Expression> select(Expression operand) {
    Optional<Identifier> fieldOpt = identifier("expected identifier");
    if (fieldOpt.isPresent()) {
      Identifier field = fieldOpt.get();
      return Optional.of(
          Expression.ofSelect(Select.create(operand, field, operand.pos().extend(field.pos()))));
    }
    return Optional.empty();
  }

  private Optional<Expression> reference() {
    Optional<Identifier> identifier = identifier("expected identifier");
    return identifier.map(Expression::ofReference);
  }

  private Optional<Expression> literal() {
    if (peek().kind() == TokenKind.TRUE) {
      return Optional.of(Expression.ofValue(Value.ofBoolLit(true, eat().pos())));
    } else if (peek().kind() == TokenKind.FALSE) {
      return Optional.of(Expression.ofValue(Value.ofBoolLit(false, eat().pos())));
    } else if (peek().kind() == TokenKind.INT_LIT) {
      return intLit().map(Expression::ofValue);
    } else if (peek().kind() == TokenKind.UNTERMINATED_CHAR_LIT) {
      errPeek("Unterminated character literal");
      eat();
      return Optional.empty();
    } else if (peek().kind() == TokenKind.UNTERMINATED_STRING_LIT) {
      errPeek("Unterminated string literal");
      eat();
      return Optional.empty();
    } else if (peek().kind() == TokenKind.CHAR_LIT) {
      return charLit().map(Expression::ofValue);
    } else if (false && peek().kind() == TokenKind.STRING_LIT) {
      // TODO: enable when string literals are supported
      return stringLit().map(Expression::ofValue);
    } else {
      return parenthesized();
    }
  }

  public Optional<Value> intLit() {
    Token intLit = eatIfExpected("expected integer literal", TokenKind.INT_LIT);
    if (intLit == null) {
      return Optional.empty();
    }
    String text = intLit.text();
    try {
      int lit = Integer.parseInt(text);
      return Optional.of(Value.ofIntLit(lit, intLit.pos()));
    } catch (NumberFormatException nfe) {
      errLast("Integer " + text + " out of range");
      return Optional.empty();
    }
  }

  public Optional<Value> charLit() {
    Token charLit = eatIfExpected("expected character literal", TokenKind.CHAR_LIT);
    if (charLit == null) {
      return Optional.empty();
    }
    String content = charLit.text();
    content = content.substring(1, content.length() - 1); // Strip ''
    content = unescape(content);
    if (content.length() != 1) {
      errLast("Invalid character literal");
      return Optional.empty();
    }
    return Optional.of(Value.ofCharLit(content.charAt(0), charLit.pos()));
  }

  public Optional<Value> stringLit() {
    Token stringLit = eatIfExpected("expected string literal", TokenKind.STRING_LIT);
    if (stringLit == null) {
      return Optional.empty();
    }
    String content = stringLit.text();
    content = content.substring(1, content.length() - 1); // Strip ""
    content = unescape(content);
    return Optional.of(Value.ofStringLit(content, stringLit.pos()));
  }

  private Optional<Expression> parenthesized() {
    if (peek().kind() == TokenKind.LPAREN) {
      eat();
      Optional<Expression> ret = expression();
      eatIfExpected("expected )", TokenKind.RPAREN);
      return ret;
    }
    errPeek("unexpected " + peek().text());
    eatLineUntil(peek(), EXPRESSION_FAILURE_ENDS);
    return Optional.empty();
  }

  private String unescape(String content) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < content.length(); i++) {
      char ch = content.charAt(i);
      if (ch != '\\') {
        sb.append(ch);
      } else {
        char esc = content.charAt(++i);
        if (esc == 'n') sb.append("\n");
        else if (esc == 'r') sb.append("\r");
        else if (esc == 't') sb.append("\t");
        else if (esc == '"') sb.append("\"");
        else if (esc == '\'') sb.append("'");
        else if (esc == '\\') sb.append("\\");
        else {
          errLast("Invalid escape character \\" + esc);
        }
      }
    }
    return sb.toString();
  }

  private Optional<Type> type() {
    if (!TYPES.contains(peek().kind())) {
      errAt("expected type", peek());
      return Optional.empty();
    }
    Token typeToken = eat();
    Type type;
    if (typeToken.kind() == TokenKind.STRUCT) {
      Optional<Identifier> structName = identifier("expected struct name");
      if (structName.isEmpty()) {
        return Optional.empty();
      }
      type = Type.normal(structName.get().text(), typeToken.pos().extend(structName.get().pos()));
    } else {
      type = Type.normal(typeToken.text(), typeToken.pos());
    }

    // Parse [1][2][3] array dimensions
    ImmutableList.Builder<Type.ArrayDimension> dims = ImmutableList.builder();
    // TODO: enable when arrays are supported
    while (false && peek().kind() == TokenKind.LBRACKET) {
      eat();
      Optional<Value> dimOpt = intLit();
      if (dimOpt.isEmpty()) {
        errAt("expected array dimension", last);
      } else {
        Value.IntLit value = dimOpt.get().intLit();
        dims.add(Type.ArrayDimension.fixed(value.value(), value.pos()));
      }
      if (peek().kind() != TokenKind.RBRACKET) {
        eatLineUntil(last, ImmutableSet.of(TokenKind.RBRACKET, TokenKind.EQUALS, TokenKind.COMMA));
      }
      eatIfExpected("expected closing ]", TokenKind.RBRACKET);
    }
    ImmutableList<Type.ArrayDimension> arrayDims = dims.build();
    if (arrayDims.isEmpty()) {
      return Optional.of(type);
    } else {
      return Optional.of(Type.array(type.name(), arrayDims, typeToken.pos().extend(last.pos())));
    }
  }

  private Optional<Identifier> identifier(String error) {
    Token name = eatIfExpected(error, TokenKind.IDENTIFIER);
    if (name == null) {
      return Optional.empty();
    }
    return Optional.of(Identifier.create(name.text(), name.pos()));
  }

  /** Consumes the next token if it was of kind {@code kind}, otherwise logging {@code errorMsg}. */
  private Token eatIfExpected(String errorMsg, TokenKind kind) {
    Token t = peek();
    if (t.kind() != kind) {
      errPeek(errorMsg);
      return null;
    }
    eat();
    return t;
  }

  /**
   * When expecting a ; to finish a statement, consume the remainder of the line if there was no ;
   */
  private void finishLine() {
    if (eatIfExpected("expected ;", TokenKind.SEMICOLON) == null) {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
      if (peek().kind() == TokenKind.SEMICOLON) {
        eat();
      }
    }
  }

  /** Eats all tokens on the same line as that of {@code tok}. */
  private void eatLine(Token tok) {
    eatLineUntil(tok, Collections.emptySet());
  }

  /**
   * Eats all tokens on the same line as that of {@code tok}, or until one of {@code kinds} is
   * encountered.
   */
  private void eatLineUntil(Token tok, Set<TokenKind> kinds) {
    while (peek().kind() != TokenKind.EOF
        && !kinds.contains(peek().kind())
        && peek().pos().from().line() == tok.pos().from().line()) {
      eat();
    }
  }

  private void eatUntil(TokenKind kind) {
    while (peek().kind() != TokenKind.EOF && peek().kind() != kind) {
      eat();
    }
  }

  private void errAt(String msg, Token tok) {
    errors.error(tok.pos(), msg);
    program.setValid(false);
  }

  /** Adds an error pointing to the token that next token in the stream. */
  private void errPeek(String msg) {
    errAt(msg, peek());
  }

  /** Adds an error pointing to the last token to be eat()en. */
  private void errLast(String msg) {
    errAt(msg, last);
  }

  /**
   * Returns the token {@code offset} tokens from the next one in the stream, so that peek(0) is the
   * next token of the stream.
   */
  private Token peek(int offset) {
    while (lookahead.size() <= offset) {
      lookahead.add(tokens.next());
    }
    return lookahead.get(offset);
  }

  /** Returns the next token of the stream. */
  private Token peek() {
    return peek(0);
  }

  /**
   * Returns and consumes the next token of the stream, updating the end of the provided {@code
   * sourceRange} to include the token.
   */
  private Token eat() {
    return last = lookahead.empty() ? tokens.next() : lookahead.poll();
  }

  private static Identifier missingIdentifier(SourcePos from, SourcePos to) {
    return Identifier.create("<missing>", SourceRange.between(from, to));
  }

  /**
   * Parses a Spooky program using the given token source. The returned {@link Program} is only
   * valid if no errors were added.
   *
   * <p>The provided {@link Tokenizer} is always fully exhausted after parsing.
   */
  @JsMethod
  public static Program parse(Tokenizer toks, Errors err) {
    return new Parser(toks, err).parse();
  }
}
