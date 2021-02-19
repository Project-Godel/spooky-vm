package se.jsannemo.spooky.compiler.parser;

import com.google.common.collect.ImmutableMap;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

public final class Parser {

  public static final Ast.Identifier INVALID_IDENT =
      Ast.Identifier.newBuilder().setName("<invalid>").build();
  public static final Ast.Type INVALID_TYPE = Ast.Type.newBuilder().setName("<invalid>").build();

  private final Tokenizer toks;
  private final Errors err;
  private final Lookahead<Ast.Token> lookahead = new Lookahead<>();
  private Ast.Token last;

  private Parser(Tokenizer toks, Errors err) {
    this.toks = toks;
    this.err = err;
  }

  public Ast.Program parse() {
    Ast.Program.Builder builder = Ast.Program.newBuilder();
    while (peek().getKind() != Ast.Token.Kind.EOF) {
      topLevel(builder);
    }
    return builder.build();
  }

  private void topLevel(Ast.Program.Builder program) {
    boolean errored = false;
    while (true) {
      Ast.Token nx = peek();
      if (nx.getKind() == Ast.Token.Kind.EOF) {
        return;
      } else if (nx.getKind() == Ast.Token.Kind.FUNC) {
        func().ifPresent(program::addFunctions);
        continue;
      } else if (nx.getKind() == Ast.Token.Kind.EXTERN) {
        extern().ifPresent(program::addExterns);
        continue;
      } else if (nx.getKind() == Ast.Token.Kind.IDENTIFIER) {
        if (peek(1).getKind() == Ast.Token.Kind.COLON) {
          varDecl().ifPresent(decl -> program.addGlobals(decl.getDecl()));
          eat("Expected ;", Ast.Token.Kind.SEMICOLON);
          continue;
        }
      }
      if (!errored) {
        errPeek("Expected func, extern or variable declaration.");
        errored = true;
      }
      eat();
    }
  }

  private Optional<Ast.FuncDecl> decl() {
    Ast.FuncDecl.Builder builder = Ast.FuncDecl.newBuilder();
    Optional<Ast.Identifier> name = identifier("Expected function name");
    name.ifPresentOrElse(builder::setName, () -> builder.setName(INVALID_IDENT));
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat();
      parameterList(builder);
      eat("Expected )", Ast.Token.Kind.RPAREN);
    }
    if (peek().getKind() == Ast.Token.Kind.ARROW) {
      eat();
      type().ifPresentOrElse(builder::setReturnType, () -> builder.setReturnType(INVALID_TYPE));
    }
    return Optional.of(builder.build());
  }

  private void parameterList(Ast.FuncDecl.Builder builder) {
    while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.Identifier name = identifier("Expected parameter name").get();
      eat("Expected ':'", Ast.Token.Kind.COLON);
      Optional<Ast.Type> type = type();
      type.ifPresent(
          value ->
              builder.addParams(
                  Ast.FuncParam.newBuilder()
                      .setPosition(name.getPosition())
                      .setName(name)
                      .setType(value)
                      .build()));
      if (peek().getKind() == Ast.Token.Kind.COMMA) {
        eat();
      }
    }
  }

  private Optional<Ast.FuncDecl> extern() {
    eat("Expected 'extern'", Ast.Token.Kind.EXTERN);
    return decl();
  }

  private Optional<Ast.Func> func() {
    Ast.Func.Builder builder = Ast.Func.newBuilder();
    Ast.Token func;
    if ((func = eat("Expected 'func'", Ast.Token.Kind.FUNC)) == null) {
      return Optional.empty();
    }
    builder.setPosition(func.getPosition());
    Optional<Ast.FuncDecl> decl = decl();
    if (decl.isEmpty()) {
      return Optional.empty();
    } else {
      builder.setDecl(decl.get());
    }
    Optional<Ast.Statement> body = block();
    body.ifPresent(builder::setBody);
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> block() {
    Ast.Token lbrace = eat("Expected {", Ast.Token.Kind.LBRACE);
    if (lbrace == null) {
      return Optional.empty();
    }
    Ast.Statement.Builder block = Ast.Statement.newBuilder().setPosition(lbrace.getPosition());
    block.getBlockBuilder().setPosition(lbrace.getPosition());
    while (true) {
      Ast.Token nx = peek();
      if (nx.getKind() == Ast.Token.Kind.RBRACE) {
        eat();
        break;
      }
      if (nx.getKind() == Ast.Token.Kind.EOF) {
        errAt("Unterminated block", lbrace);
        break;
      }
      Optional<Ast.Statement> st = statement();
      if (st.isEmpty()) {
        break;
      }
      block.getBlockBuilder().addBody(st.get());
    }
    return Optional.of(block.build());
  }

  private Optional<Ast.Statement> statement() {
    Ast.Token.Kind nx = peek().getKind();
    if (nx == Ast.Token.Kind.IF) {
      return conditional();
    } else if (nx == Ast.Token.Kind.FOR) {
      return forLoop();
    } else if (nx == Ast.Token.Kind.WHILE) {
      return whileLoop();
    } else if (nx == Ast.Token.Kind.RETURN) {
      return returnStmt();
    } else if (nx == Ast.Token.Kind.LBRACE) {
      return block();
    } else if (nx == Ast.Token.Kind.IDENTIFIER && peek(1).getKind() == Ast.Token.Kind.COLON) {
      Optional<Ast.Statement> statement = varDecl();
      eat("Expected ;", Ast.Token.Kind.SEMICOLON);
      return statement;
    } else {
      Optional<Ast.Statement> statement = exprStatement();
      eat("Expected ;", Ast.Token.Kind.SEMICOLON);
      return statement;
    }
  }

  private Optional<Ast.Statement> varDecl() {
    Ast.Statement.Builder builder = Ast.Statement.newBuilder();
    Ast.VarDecl.Builder declBuilder = builder.getDeclBuilder();
    Optional<Ast.Identifier> name = identifier("Expected variable name");
    if (name.isEmpty()) {
      return Optional.empty();
    }
    Ast.Identifier nameId = name.get();
    builder.setPosition(nameId.getPosition());
    declBuilder.setPosition(nameId.getPosition());
    declBuilder.setName(nameId);

    if (eat("Expected :", Ast.Token.Kind.COLON) != null) {
      type().ifPresent(declBuilder::setType);
    }
    if (eat("Expected =", Ast.Token.Kind.ASSIGN) != null) {
      expression().ifPresent(declBuilder::setInit);
    }
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> returnStmt() {
    Ast.Statement.Builder builder = Ast.Statement.newBuilder();
    Ast.ReturnValue.Builder returnBuilder = builder.getReturnValueBuilder();
    Ast.Token retTok = eat("Expected return", Ast.Token.Kind.RETURN);
    if (retTok == null) {
      return Optional.empty();
    }
    builder.setPosition(retTok.getPosition());
    returnBuilder.setPosition(retTok.getPosition());
    if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
      expression().ifPresent(returnBuilder::setValue);
    }
    eat("Expected ;", Ast.Token.Kind.SEMICOLON);
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> whileLoop() {
    Ast.Statement.Builder builder = Ast.Statement.newBuilder();
    Ast.Loop.Builder loopBuilder = builder.getLoopBuilder();
    Ast.Token whileTok = eat("Expected while", Ast.Token.Kind.WHILE);
    if (whileTok == null) {
      return Optional.empty();
    }
    builder.setPosition(whileTok.getPosition());
    loopBuilder.setPosition(whileTok.getPosition());
    if (eat("Expected (", Ast.Token.Kind.LPAREN) != null) {
      expression().ifPresent(loopBuilder::setCondition);
      eat("Expected )", Ast.Token.Kind.RPAREN);
    }
    statement().ifPresent(loopBuilder::setBody);
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> forLoop() {
    Ast.Statement.Builder builder = Ast.Statement.newBuilder();
    Ast.Loop.Builder loopBuilder = builder.getLoopBuilder();
    Ast.Token forTok = eat("Expected for", Ast.Token.Kind.FOR);
    if (forTok == null) {
      return Optional.empty();
    }
    builder.setPosition(forTok.getPosition());
    loopBuilder.setPosition(forTok.getPosition());
    if (eat("Expected (", Ast.Token.Kind.LPAREN) != null) {
      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        simpleStatement().ifPresent(loopBuilder::setInit);
      }
      eat("Expected ;", Ast.Token.Kind.SEMICOLON);

      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        expression().ifPresent(loopBuilder::setCondition);
      }
      eat("Expected ;", Ast.Token.Kind.SEMICOLON);

      if (peek().getKind() != Ast.Token.Kind.RPAREN) {
        simpleStatement().ifPresent(loopBuilder::setIncrement);
      }
      eat("Expected )", Ast.Token.Kind.RPAREN);
    }
    statement().ifPresent(loopBuilder::setBody);
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> simpleStatement() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER
        && peek(1).getKind() == Ast.Token.Kind.COLON) {
      return varDecl();
    }
    return exprStatement();
  }

  private Optional<Ast.Statement> conditional() {
    Ast.Statement.Builder builder = Ast.Statement.newBuilder();
    Ast.Conditional.Builder condBuilder = builder.getConditionalBuilder();
    Ast.Token ifTok = eat("Expected if", Ast.Token.Kind.IF);
    if (ifTok == null) {
      return Optional.empty();
    }
    builder.setPosition(ifTok.getPosition());
    condBuilder.setPosition(ifTok.getPosition());
    if (eat("Expected (", Ast.Token.Kind.LPAREN) != null) {
      expression().ifPresent(condBuilder::setCondition);
      eat("Expected )", Ast.Token.Kind.RPAREN);
    }
    statement().ifPresent(condBuilder::setBody);
    if (peek().getKind() == Ast.Token.Kind.ELSE) {
      eat();
      statement().ifPresent(condBuilder::setElseBody);
    }
    return Optional.of(builder.build());
  }

  private Optional<Ast.Statement> exprStatement() {
    Optional<Ast.Expr> maybeExpr = expression();
    if (maybeExpr.isEmpty()) {
      return Optional.empty();
    }
    Ast.Expr expr = maybeExpr.get();
    return Optional.of(
        Ast.Statement.newBuilder().setExpression(expr).setPosition(expr.getPosition()).build());
  }

  private Optional<Ast.Expr> expression() {
    return assignment();
  }

  private static final ImmutableMap<Ast.Token.Kind, Ast.BinaryOp> assignments =
      ImmutableMap.<Ast.Token.Kind, Ast.BinaryOp>builder()
          .put(Ast.Token.Kind.PLUS_EQUALS, Ast.BinaryOp.ADD)
          .put(Ast.Token.Kind.MINUS_EQUALS, Ast.BinaryOp.SUBTRACT)
          .put(Ast.Token.Kind.DIV_EQUALS, Ast.BinaryOp.DIVIDE)
          .put(Ast.Token.Kind.TIMES_EQUALS, Ast.BinaryOp.MULTIPLY)
          .put(Ast.Token.Kind.MOD_EQUALS, Ast.BinaryOp.MODULO)
          .put(Ast.Token.Kind.ASSIGN, Ast.BinaryOp.BINARY_OP_UNSPECIFIED)
          .build();

  private Optional<Ast.Expr> assignment() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER
        && assignments.containsKey(peek(1).getKind())) {
      Ast.Identifier name = identifier("Expected identifier").get();
      Ast.BinaryOp op = assignments.get(eat().getKind());
      Optional<Ast.Expr> value = assignment();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(name.getPosition());
      Ast.Assignment.Builder assignmentBuilder =
          builder.getAssignmentBuilder().setPosition(name.getPosition()).setVariable(name);
      if (op == Ast.BinaryOp.BINARY_OP_UNSPECIFIED) {
        value.ifPresent(assignmentBuilder::setValue);
      } else {
        value.ifPresent(
            v -> {
              Ast.Expr.Builder compound = Ast.Expr.newBuilder().setPosition(name.getPosition());
              compound
                  .getBinaryBuilder()
                  .setPosition(name.getPosition())
                  .setOperator(op)
                  .setLeft(
                      Ast.Expr.newBuilder()
                          .setPosition(name.getPosition())
                          .setReference(name)
                          .build())
                  .setRight(v);
              assignmentBuilder.setValue(compound.build());
            });
      }
      return Optional.of(builder.build());
    } else {
      return ternary();
    }
  }

  private Optional<Ast.Expr> ternary() {
    Optional<Ast.Expr> e = orExpr();
    if (e.isPresent() && peek().getKind() == Ast.Token.Kind.QUESTION) {
      Ast.Expr cond = e.get();
      eat();
      Optional<Ast.Expr> e1 = orExpr();
      eat("Expected :", Ast.Token.Kind.COLON);
      Optional<Ast.Expr> e2 = ternary();

      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(cond.getPosition());
      Ast.Ternary.Builder conditionalBuilder =
          builder.getConditionalBuilder().setPosition(cond.getPosition()).setCond(cond);
      e1.ifPresent(conditionalBuilder::setLeft);
      e2.ifPresent(conditionalBuilder::setRight);
      return Optional.of(builder.build());
    } else {
      return e;
    }
  }

  private static final ImmutableMap<Ast.Token.Kind, Ast.BinaryOp> binOps =
      ImmutableMap.<Ast.Token.Kind, Ast.BinaryOp>builder()
          .put(Ast.Token.Kind.OR, Ast.BinaryOp.OR)
          .put(Ast.Token.Kind.AND, Ast.BinaryOp.AND)
          .put(Ast.Token.Kind.PLUS, Ast.BinaryOp.ADD)
          .put(Ast.Token.Kind.MINUS, Ast.BinaryOp.SUBTRACT)
          .put(Ast.Token.Kind.SLASH, Ast.BinaryOp.DIVIDE)
          .put(Ast.Token.Kind.ASTERISK, Ast.BinaryOp.MULTIPLY)
          .put(Ast.Token.Kind.PERCENT, Ast.BinaryOp.MODULO)
          .put(Ast.Token.Kind.EQUALS, Ast.BinaryOp.EQUALS)
          .put(Ast.Token.Kind.NOT_EQUALS, Ast.BinaryOp.NOT_EQUALS)
          .put(Ast.Token.Kind.LESS, Ast.BinaryOp.LESS_THAN)
          .put(Ast.Token.Kind.GREATER, Ast.BinaryOp.GREATER_THAN)
          .put(Ast.Token.Kind.LESS_EQUALS, Ast.BinaryOp.LESS_EQUALS)
          .put(Ast.Token.Kind.GREATER_EQUALS, Ast.BinaryOp.GREATER_EQUALS)
          .build();

  private Optional<Ast.Expr> leftAssociative(
      Supplier<Optional<Ast.Expr>> next, Ast.Token.Kind... opKinds) {
    Optional<Ast.Expr> e = next.get();
    while (e.isPresent() && Arrays.stream(opKinds).anyMatch(t -> t == peek().getKind())) {
      Ast.Token.Kind opKind = eat().getKind();
      Ast.Expr lhs = e.get();
      Optional<Ast.Expr> rhs = next.get();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(e.get().getPosition());
      Ast.BinaryExpr.Builder binaryBuilder =
          builder
              .getBinaryBuilder()
              .setPosition(e.get().getPosition())
              .setOperator(binOps.get(opKind))
              .setLeft(lhs);
      rhs.ifPresent(binaryBuilder::setRight);
      e = Optional.of(builder.build());
    }
    return e;
  }

  private Optional<Ast.Expr> orExpr() {
    return leftAssociative(this::andExpr, Ast.Token.Kind.OR);
  }

  private Optional<Ast.Expr> andExpr() {
    return leftAssociative(this::equalityExpr, Ast.Token.Kind.AND);
  }

  private Optional<Ast.Expr> equalityExpr() {
    return leftAssociative(this::cmpExpr, Ast.Token.Kind.EQUALS, Ast.Token.Kind.NOT_EQUALS);
  }

  private Optional<Ast.Expr> cmpExpr() {
    return leftAssociative(
        this::addExpr,
        Ast.Token.Kind.LESS,
        Ast.Token.Kind.LESS_EQUALS,
        Ast.Token.Kind.GREATER,
        Ast.Token.Kind.GREATER_EQUALS);
  }

  private Optional<Ast.Expr> addExpr() {
    return leftAssociative(this::mulExpr, Ast.Token.Kind.PLUS, Ast.Token.Kind.MINUS);
  }

  private Optional<Ast.Expr> mulExpr() {
    return leftAssociative(
        this::unaryExpr, Ast.Token.Kind.SLASH, Ast.Token.Kind.ASTERISK, Ast.Token.Kind.PERCENT);
  }

  private Optional<Ast.Expr> unaryExpr() {
    if (peek().getKind() == Ast.Token.Kind.EXCLAIM) {
      Ast.Token op = eat();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(op.getPosition());
      Ast.UnaryExpr.Builder unaryBuilder =
          builder.getUnaryBuilder().setPosition(op.getPosition()).setOperator(Ast.UnaryOp.NOT);
      arrayIndex().ifPresent(unaryBuilder::setExpr);
      return Optional.of(builder.build());
    } else if (peek().getKind() == Ast.Token.Kind.MINUS) {
      Ast.Token op = eat();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(op.getPosition());
      if (peek().getKind() == Ast.Token.Kind.INT_LIT) {
        int lit = 0;
        try {
          lit = Integer.parseInt("-" + eat().getText());
        } catch (NumberFormatException nfe) {
          errHere("Integer " + lit + " not valid");
        }
        builder.getValueBuilder().setPosition(builder.getPosition()).setIntLiteral(lit);
      } else {
        Ast.UnaryExpr.Builder unaryBuilder =
            builder.getUnaryBuilder().setPosition(op.getPosition()).setOperator(Ast.UnaryOp.NEGATE);
        arrayIndex().ifPresent(unaryBuilder::setExpr);
      }
      return Optional.of(builder.build());
    }
    return arrayIndex();
  }

  private Optional<Ast.Expr> arrayIndex() {
    Optional<Ast.Expr> e = parenthesized();
    while (e.isPresent() && peek().getKind() == Ast.Token.Kind.LBRACKET) {
      Ast.Expr lhs = e.get();
      eat();
      Optional<Ast.Expr> rhs = parenthesized();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder().setPosition(lhs.getPosition());
      Ast.BinaryExpr.Builder binaryBuilder =
          builder
              .getBinaryBuilder()
              .setPosition(lhs.getPosition())
              .setOperator(Ast.BinaryOp.ARRAY_ACCESS)
              .setLeft(lhs);
      rhs.ifPresent(binaryBuilder::setRight);
      e = Optional.of(builder.build());
      eat("Expected ]", Ast.Token.Kind.RBRACKET);
    }
    return e;
  }

  private Optional<Ast.Expr> parenthesized() {
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat();
      Optional<Ast.Expr> ret = expression();
      eat("Expected )", Ast.Token.Kind.RPAREN);
      return ret;
    }
    return refOrCall();
  }

  private Optional<Ast.Expr> refOrCall() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.Identifier name = identifier("Expected identifier").get();
      Ast.Expr.Builder expr = Ast.Expr.newBuilder().setPosition(name.getPosition());
      if (peek().getKind() == Ast.Token.Kind.LPAREN) {
        eat();
        expr.getCallBuilder().setPosition(expr.getPosition()).setFunction(name);
        while (peek().getKind() != Ast.Token.Kind.RPAREN) {
          Optional<Ast.Expr> param = expression();
          if (param.isEmpty()) {
            break;
          }
          expr.getCallBuilder().addParams(param.get());
          if (peek().getKind() != Ast.Token.Kind.COMMA) {
            break;
          }
          eat();
        }
        eat("Expected )", Ast.Token.Kind.RPAREN);
      } else {
        expr.setReference(name);
      }
      return Optional.of(expr.build());
    }
    return literal();
  }

  private Optional<Ast.Expr> literal() {
    Ast.Expr.Builder expr = Ast.Expr.newBuilder().setPosition(peek().getPosition());
    if (peek().getKind() == Ast.Token.Kind.TRUE) {
      eat();
      expr.getValueBuilder().setPosition(expr.getPosition()).setBoolLiteral(true);
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.FALSE) {
      eat();
      expr.getValueBuilder().setPosition(expr.getPosition()).setBoolLiteral(false);
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.INT_LIT) {
      int lit = 0;
      try {
        lit = Integer.parseInt(eat().getText());
      } catch (NumberFormatException nfe) {
        errHere("Integer " + lit + " not valid");
      }
      expr.getValueBuilder().setPosition(expr.getPosition()).setIntLiteral(lit);
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_CHAR_LIT) {
      errPeek("Unterminated character literal");
      eat();
      expr.getValueBuilder().setCharLiteral(0);
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_STRING_LIT) {
      errPeek("Unterminated string literal");
      eat();
      expr.getValueBuilder().setStringLiteral("");
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.CHAR_LIT) {
      Ast.Token token = eat();
      String content = token.getText().substring(1, token.getText().length() - 1);
      content = unescape(content);
      if (content.length() != 1) {
        errHere("Invalid character literal");
      }
      expr.getValueBuilder().setCharLiteral(content.charAt(0));
      return Optional.of(expr.build());
    } else if (peek().getKind() == Ast.Token.Kind.STRING_LIT) {
      Ast.Token token = eat();
      String content = token.getText().substring(1, token.getText().length() - 1);
      content = unescape(content);
      expr.getValueBuilder().setStringLiteral(content);
      return Optional.of(expr.build());
    }
    errPeek("Invalid expression start " + peek().getText());
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
        else if (esc == '\\') sb.append("\\");
        else {
          errHere("Invalid escape character \\" + esc);
        }
      }
    }
    return sb.toString();
  }

  private Optional<Ast.Type> type() {
    Optional<Ast.Identifier> nameOpt = identifier("Expected type name");
    if (nameOpt.isEmpty()) {
      return Optional.empty();
    }

    Ast.Type.Builder builder = Ast.Type.newBuilder();
    Ast.Identifier name = nameOpt.get();
    builder.setName(name.getName());
    builder.setPosition(name.getPosition());
    int dim = 0;
    while (peek().getKind() == Ast.Token.Kind.LBRACKET) {
      eat();
      eat("Expected closing ]", Ast.Token.Kind.RBRACKET);
      dim++;
    }
    builder.setDimension(dim);
    return Optional.of(builder.build());
  }

  private Optional<Ast.Identifier> identifier(String error) {
    Ast.Token name;
    if ((name = eat(error, Ast.Token.Kind.IDENTIFIER)) == null) {
      return Optional.empty();
    }
    return Optional.of(
        Ast.Identifier.newBuilder()
            .setPosition(name.getPosition())
            .setName(name.getText())
            .build());
  }

  private Ast.Token eat(String msg, Ast.Token.Kind kind) {
    Ast.Token t = peek();
    if (t.getKind() != kind) {
      errPeek(msg);
      return null;
    }
    eat();
    return t;
  }

  private void errAt(String msg, Ast.Token tok) {
    err.error(tok.getPosition(), msg);
  }

  private void errPeek(String msg) {
    errAt(msg, peek());
  }

  private void errHere(String msg) {
    errAt(msg, last);
  }

  private Ast.Token peek(int offset) {
    while (lookahead.size() <= offset) {
      lookahead.add(toks.next());
    }
    return lookahead.get(offset);
  }

  private Ast.Token peek() {
    return peek(0);
  }

  private Ast.Token eat() {
    if (lookahead.size() == 0) {
      return toks.next();
    }
    return last = lookahead.poll();
  }

  public static Parser create(Tokenizer toks, Errors err) {
    return new Parser(toks, err);
  }

  static class Lookahead<T> {
    private int head = 0;
    private int size = 0;
    private final Object[] queue = new Object[3];

    public void add(T tok) {
      if (size == queue.length) {
        throw new IllegalStateException("Circular buffer exceeded!");
      }
      int nx = (head + size) % queue.length;
      queue[nx] = tok;
      size++;
    }

    @SuppressWarnings("unchecked")
    public T get(int idx) {
      checkArgument(idx < size, "Index out of bounds");
      return (T) queue[(head + idx) % queue.length];
    }

    @SuppressWarnings("unchecked")
    public T poll() {
      checkArgument(size > 0, "Buffer is empty");
      T ret = (T) queue[head];
      head = (head + 1) % queue.length;
      size--;
      return ret;
    }

    public int size() {
      return size;
    }
  }
}
