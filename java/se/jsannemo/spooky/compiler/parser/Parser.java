package se.jsannemo.spooky.compiler.parser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.util.CircularQueue;

/**
 * Parser of Spooky code. Tokenization (and thus input to the parser) is set up in another class,
 * {@link Tokenizer}.
 */
public final class Parser {

  private static final ImmutableSet<Ast.Token.Kind> STATEMENT_FAILURE_ENDS =
      ImmutableSet.of(Ast.Token.Kind.SEMICOLON, Ast.Token.Kind.RBRACE, Ast.Token.Kind.LBRACE);
  private static final ImmutableSet<Ast.Token.Kind> COND_FAILURE_ENDS =
      ImmutableSet.of(
          Ast.Token.Kind.SEMICOLON,
          Ast.Token.Kind.RBRACE,
          Ast.Token.Kind.LBRACE,
          Ast.Token.Kind.RPAREN);
  private static final ImmutableSet<Ast.Token.Kind> EXPRESSION_FAILURE_ENDS =
      ImmutableSet.of(
          Ast.Token.Kind.SEMICOLON,
          Ast.Token.Kind.COMMA,
          Ast.Token.Kind.RBRACE,
          Ast.Token.Kind.LBRACE,
          Ast.Token.Kind.RBRACKET,
          Ast.Token.Kind.RPAREN);

  private final Tokenizer toks;
  private final Errors err;
  private final CircularQueue<Ast.Token> lookahead = CircularQueue.withCapacity(3);
  private final Ast.Program.Builder program = Ast.Program.newBuilder().setValid(true);
  // The last token that was eat()en.
  private Ast.Token last;

  private Parser(Tokenizer toks, Errors err) {
    this.toks = toks;
    this.err = err;
  }

  private Ast.Program parse() {
    while (peek().getKind() != Ast.Token.Kind.EOF) {
      topLevel(program);
    }
    return program.build();
  }

  private void topLevel(Ast.Program.Builder program) {
    boolean errored = false;
    while (true) {
      Ast.Token.Kind nx = peek().getKind();
      if (nx == Ast.Token.Kind.EOF) {
        return;
      } else if (nx == Ast.Token.Kind.FUNC) {
        func().ifPresent(program::addFunctions);
        continue;
      } else if (nx == Ast.Token.Kind.EXTERN) {
        extern().ifPresent(program::addExterns);
        continue;
      } else if (nx == Ast.Token.Kind.STRUCT) {
        struct().ifPresent(program::addStructs);
        continue;
      } else if (nx == Ast.Token.Kind.IDENTIFIER) {
        if (peek(1).getKind() == Ast.Token.Kind.COLON) {
          Optional<Ast.Statement> var = varDecl();
          var.ifPresent(statement -> program.addGlobals(statement.getDecl()));
          finishLine();
          continue;
        }
      }
      // We only want to error out for the first invalid top-level token.
      if (!errored) {
        errPeek("Expected func, extern or variable declaration.");
        errored = true;
      }
      eat(null);
    }
  }

  private Optional<Ast.StructDecl> struct() {
    Ast.Pos.Builder pos = Ast.Pos.newBuilder();
    Ast.StructDecl.Builder struct = Ast.StructDecl.newBuilder();
    eat(pos);
    Optional<Ast.Identifier> name = identifier("Expected struct name", pos);
    name.ifPresent(struct::setName);
    if (peek().getKind() == Ast.Token.Kind.LBRACE) {
      eat(pos);
      while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
        Ast.Pos.Builder fieldPos = Ast.Pos.newBuilder();
        Ast.StructField.Builder field = Ast.StructField.newBuilder();
        field.setName(identifier("Expected struct field name", fieldPos).get());
        if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, fieldPos) == null) {
          eatLineUntil(last, STATEMENT_FAILURE_ENDS);
          continue;
        }
        Optional<Ast.Type> type = type(fieldPos);
        type.ifPresent(field::setType);
        finishLine();
        field.setPosition(fieldPos);
        struct.addFields(field);
        appendPos(pos, field.getPosition());
      }
      if (eatIfExpected("Expected }", Ast.Token.Kind.RBRACE, pos) == null) {
        eatUntil(Ast.Token.Kind.RBRACE);
        if (peek().getKind() == Ast.Token.Kind.RBRACE) {
          eat(null);
        }
      }
    } else if (name.isPresent()) {
      errPeek("Expected {");
    }
    return Optional.of(struct.setPosition(pos).build());
  }

  private Optional<Ast.FuncDecl> decl(Ast.Token kw) {
    Ast.Pos.Builder declPos = Ast.Pos.newBuilder();
    Ast.FuncDecl.Builder decl = Ast.FuncDecl.newBuilder();
    if (kw != null) {
      appendPos(declPos, kw.getPosition());
    }
    Optional<Ast.Identifier> name = identifier("Expected function name", declPos);
    name.ifPresent(decl::setName);
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat(declPos);
      parameterList(decl);
      if (eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, declPos) == null) {
        eatLine(last);
      }
    }
    if (peek().getKind() == Ast.Token.Kind.ARROW) {
      eat(declPos);
      type(declPos).ifPresent(decl::setReturnType);
    }
    decl.setPosition(declPos);
    return Optional.of(decl.build());
  }

  private void parameterList(Ast.FuncDecl.Builder builder) {
    while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.Pos.Builder paramPos = Ast.Pos.newBuilder();
      Ast.FuncParam.Builder param = Ast.FuncParam.newBuilder();
      param.setName(identifier("Expected parameter name", paramPos).get());
      eatIfExpected("Expected :", Ast.Token.Kind.COLON, paramPos);
      Optional<Ast.Type> type = type(paramPos);
      type.ifPresent(param::setType);
      if (peek().getKind() == Ast.Token.Kind.COMMA) {
        eat(paramPos);
      }
      param.setPosition(paramPos);
      builder.addParams(param);
      builder.setPosition(mergePos(builder.getPosition(), param.getPosition()));
    }
  }

  private Optional<Ast.FuncDecl> extern() {
    return decl(eatIfExpected("Expected extern", Ast.Token.Kind.EXTERN, null));
  }

  private Optional<Ast.Func> func() {
    Optional<Ast.FuncDecl> maybeDecl =
        decl(eatIfExpected("Expected 'func'", Ast.Token.Kind.FUNC, null));
    if (!maybeDecl.isPresent()) {
      return Optional.empty();
    }
    Ast.FuncDecl decl = maybeDecl.get();
    Ast.Pos.Builder funcPos = decl.getPosition().toBuilder();
    ;
    Ast.Func.Builder func = Ast.Func.newBuilder().setDecl(decl);
    Optional<Ast.Statement> body = block();
    body.ifPresent(
        b -> {
          func.setBody(b);
          appendPos(funcPos, b.getPosition());
        });
    return Optional.of(func.setPosition(funcPos).build());
  }

  private Optional<Ast.Statement> block() {
    Ast.Pos.Builder blockPos = Ast.Pos.newBuilder();
    Ast.Block.Builder block = Ast.Block.newBuilder();
    Ast.Token lbrace = eatIfExpected("Expected block", Ast.Token.Kind.LBRACE, blockPos);
    if (lbrace == null) {
      return Optional.empty();
    }
    while (true) {
      Ast.Token nx = peek();
      if (nx.getKind() == Ast.Token.Kind.RBRACE) {
        eat(blockPos);
        break;
      }
      if (nx.getKind() == Ast.Token.Kind.EOF) {
        errAt("Unterminated block", lbrace);
        break;
      }
      Optional<Ast.Statement> st = statement();
      if (!st.isPresent()) {
        break;
      }
      appendPos(blockPos, st.get().getPosition());
      block.addBody(st.get());
    }
    block.setPosition(blockPos);
    return Optional.of(
        Ast.Statement.newBuilder().setBlock(block).setPosition(block.getPosition()).build());
  }

  private Optional<Ast.Statement> statement() {
    Ast.Token.Kind nx = peek().getKind();
    if (nx == Ast.Token.Kind.SEMICOLON) {
      Ast.Pos.Builder pos = Ast.Pos.newBuilder();
      eat(pos);
      return Optional.of(
          Ast.Statement.newBuilder()
              .setBlock(Ast.Block.newBuilder().setPosition(pos))
              .setPosition(pos)
              .build());
    } else if (nx == Ast.Token.Kind.IF) {
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
      finishLine();
      return statement;
    } else {
      Optional<Ast.Statement> statement = exprStatement();
      finishLine();
      return statement;
    }
  }

  private Optional<Ast.Statement> varDecl() {
    Ast.Pos.Builder declPos = Ast.Pos.newBuilder();
    Ast.VarDecl.Builder declBuilder = Ast.VarDecl.newBuilder();
    Optional<Ast.Identifier> name = identifier("Expected variable name", declPos);
    if (!name.isPresent()) {
      return Optional.empty();
    }
    declBuilder.setName(name.get());
    if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, declPos) != null) {
      type(declPos).ifPresent(declBuilder::setType);
    }
    if (eatIfExpected("Expected =", Ast.Token.Kind.ASSIGN, declPos) != null) {
      initVal()
          .ifPresent(
              ex -> {
                declBuilder.setInit(ex);
                appendPos(declPos, ex.getPosition());
              });
    }
    declBuilder.setPosition(declPos);
    return Optional.of(
        Ast.Statement.newBuilder().setDecl(declBuilder).setPosition(declPos).build());
  }

  private Optional<Ast.Expr> initVal() {
    if (peek().getKind() == Ast.Token.Kind.DEFAULT) {
      return Optional.of(defaultInit());
    }
    if (peek().getKind() == Ast.Token.Kind.LBRACE) {
      return Optional.of(structInit());
    }
    if (peek().getKind() == Ast.Token.Kind.LBRACKET) {
      return Optional.of(arrayInit());
    }
    return expression();
  }

  private Ast.Expr defaultInit() {
    Ast.Pos.Builder pos = Ast.Pos.newBuilder();
    eatIfExpected("Expected {", Ast.Token.Kind.DEFAULT, pos);
    return Ast.Expr.newBuilder()
        .setDefaultInit(Ast.DefaultInit.newBuilder().setPosition(pos))
        .setPosition(pos)
        .build();
  }

  private Ast.Expr structInit() {
    Ast.StructLit.Builder struct = Ast.StructLit.newBuilder();
    Ast.Pos.Builder structPos = Ast.Pos.newBuilder();
    eatIfExpected("Expected {", Ast.Token.Kind.LBRACE, structPos);
    while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      boolean err = false;
      boolean done = false;
      Ast.FieldInit.Builder field = Ast.FieldInit.newBuilder();
      Ast.Pos.Builder fieldPos = Ast.Pos.newBuilder();
      field.setField(identifier("Expected identifier", fieldPos).get());
      if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, fieldPos) != null) {
        Optional<Ast.Expr> value = initVal();
        if (value.isPresent()) {
          Ast.Expr e = value.get();
          field.setValue(e);
          appendPos(fieldPos, e.getPosition());
          if (peek().getKind() == Ast.Token.Kind.COMMA) {
            eat(structPos);
          } else {
            done = true;
          }
        } else {
          err = true;
        }
      } else {
        err = true;
      }
      if (err) {
        eatLineUntil(last, ImmutableSet.of(Ast.Token.Kind.COMMA, Ast.Token.Kind.RBRACE));
      }
      field.setPos(fieldPos);
      struct.addValues(field);
      if (done) {
        break;
      }
    }
    if (eatIfExpected("Expected }", Ast.Token.Kind.RBRACE, structPos) == null) {
      eatUntil(Ast.Token.Kind.RBRACE);
      if (peek().getKind() == Ast.Token.Kind.RBRACE) {
        eat(null);
      }
    }
    struct.setPosition(structPos);
    return Ast.Expr.newBuilder().setStruct(struct).setPosition(structPos).build();
  }

  private Ast.Expr arrayInit() {
    Ast.ArrayLit.Builder array = Ast.ArrayLit.newBuilder();
    Ast.Pos.Builder arrayPos = Ast.Pos.newBuilder();
    eatIfExpected("Expected [", Ast.Token.Kind.LBRACKET, arrayPos);
    while (peek().getKind() != Ast.Token.Kind.RBRACKET) {
      boolean isFill = peek().getKind() == Ast.Token.Kind.ELLIPSIS;
      if (isFill) {
        eat(arrayPos);
        array.setShouldFill(true);
        if (peek().getKind() == Ast.Token.Kind.RBRACKET) {
          break;
        }
      }
      Optional<Ast.Expr> val = initVal();
      if (val.isPresent()) {
        Ast.Expr initExpr = val.get();
        if (isFill) {
          array.setFill(initExpr);
        } else {
          array.addValues(initExpr);
        }
        appendPos(arrayPos, initExpr.getPosition());
      }
      if (!val.isPresent() || isFill) {
        break;
      }
      if (peek().getKind() != Ast.Token.Kind.COMMA) {
        break;
      }
      eat(arrayPos);
    }
    eatIfExpected("Expected ]", Ast.Token.Kind.RBRACKET, arrayPos);
    array.setPosition(arrayPos);
    return Ast.Expr.newBuilder().setArray(array).setPosition(arrayPos).build();
  }

  private Optional<Ast.Statement> returnStmt() {
    Ast.Pos.Builder returnPos = Ast.Pos.newBuilder();
    Ast.ReturnValue.Builder returnBuilder = Ast.ReturnValue.newBuilder();
    Ast.Token retTok = eatIfExpected("Expected return", Ast.Token.Kind.RETURN, returnPos);
    if (retTok == null) {
      return Optional.empty();
    }
    if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
      expression()
          .ifPresent(
              expr -> {
                returnBuilder.setValue(expr);
                appendPos(returnPos, expr.getPosition());
              });
    }
    finishLine();
    returnBuilder.setPosition(returnPos);
    return Optional.of(
        Ast.Statement.newBuilder().setReturnValue(returnBuilder).setPosition(returnPos).build());
  }

  private Optional<Ast.Statement> whileLoop() {
    Ast.Loop.Builder loop = Ast.Loop.newBuilder();
    Ast.Pos.Builder loopPos = Ast.Pos.newBuilder();
    if (eatIfExpected("Expected while", Ast.Token.Kind.WHILE, loopPos) == null) {
      return Optional.empty();
    }
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat(loopPos);
      expression()
          .ifPresent(
              expr -> {
                loop.setCondition(expr);
                appendPos(loopPos, expr.getPosition());
              });
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, loopPos);
    }
    statement()
        .ifPresent(
            s -> {
              loop.setBody(s);
              appendPos(loopPos, s.getPosition());
            });
    loop.setPosition(loopPos);
    return Optional.of(Ast.Statement.newBuilder().setLoop(loop).setPosition(loopPos).build());
  }

  private Optional<Ast.Statement> forLoop() {
    Ast.Loop.Builder loop = Ast.Loop.newBuilder();
    Ast.Pos.Builder loopPos = Ast.Pos.newBuilder();
    if (eatIfExpected("Expected for", Ast.Token.Kind.FOR, loopPos) == null) {
      return Optional.empty();
    }
    if (eatIfExpected("Expected (", Ast.Token.Kind.LPAREN, loopPos) != null) {
      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        Optional<Ast.Statement> init = simpleStatement();
        if (init.isPresent()) {
          Ast.Statement s = init.get();
          loop.setInit(s);
          appendPos(loopPos, s.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected ;", Ast.Token.Kind.SEMICOLON, loopPos);

      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        Optional<Ast.Expr> cond = expression();
        if (cond.isPresent()) {
          Ast.Expr e = cond.get();
          loop.setCondition(e);
          appendPos(loopPos, e.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected ;", Ast.Token.Kind.SEMICOLON, loopPos);

      if (peek().getKind() != Ast.Token.Kind.RPAREN) {
        Optional<Ast.Statement> inc = exprStatement();
        if (inc.isPresent()) {
          Ast.Statement s = inc.get();
          loop.setIncrement(s);
          appendPos(loopPos, s.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, loopPos);
    } else {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
    }
    statement()
        .ifPresent(
            s -> {
              loop.setBody(s);
              appendPos(loopPos, s.getPosition());
            });
    loop.setPosition(loopPos);
    return Optional.of(Ast.Statement.newBuilder().setLoop(loop).setPosition(loopPos).build());
  }

  private Optional<Ast.Statement> simpleStatement() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER
        && peek(1).getKind() == Ast.Token.Kind.COLON) {
      return varDecl();
    }
    return exprStatement();
  }

  private Optional<Ast.Statement> conditional() {
    Ast.Pos.Builder condPos = Ast.Pos.newBuilder();
    Ast.Conditional.Builder cond = Ast.Conditional.newBuilder();
    if (eatIfExpected("Expected if", Ast.Token.Kind.IF, condPos) == null) {
      return Optional.empty();
    }
    if (eatIfExpected("Expected (", Ast.Token.Kind.LPAREN, condPos) != null) {
      expression()
          .ifPresent(
              e -> {
                cond.setCondition(e);
                appendPos(condPos, e.getPosition());
              });
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, condPos);
    }
    statement()
        .ifPresent(
            s -> {
              cond.setBody(s);
              appendPos(condPos, s.getPosition());
            });
    if (peek().getKind() == Ast.Token.Kind.ELSE) {
      eat(condPos);
      statement()
          .ifPresent(
              s -> {
                cond.setElseBody(s);
                appendPos(condPos, s.getPosition());
              });
    }
    cond.setPosition(condPos);
    return Optional.of(
        Ast.Statement.newBuilder().setConditional(cond).setPosition(condPos).build());
  }

  private Optional<Ast.Statement> exprStatement() {
    Optional<Ast.Expr> maybeExpr = expression();
    if (!maybeExpr.isPresent()) {
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
    Optional<Ast.Expr> lhs = ternary();
    if (lhs.isPresent() && assignments.containsKey(peek().getKind())) {
      Ast.Assignment.Builder assign = Ast.Assignment.newBuilder().setReference(lhs.get());
      Ast.Pos.Builder assignPos = Ast.Pos.newBuilder();
      Ast.Token opTok = eat(assignPos);
      Optional<Ast.Expr> rhs = assignment();
      rhs.ifPresent(
          ex -> {
            appendPos(assignPos, ex.getPosition());
            assign.setValue(rhs.get());
          });
      Ast.BinaryOp op = assignments.get(opTok.getKind());
      assign.setCompound(op);
      assign.setPosition(assignPos);
      return Optional.of(
          Ast.Expr.newBuilder().setPosition(assignPos).setAssignment(assign).build());
    }
    return lhs;
  }

  private Optional<Ast.Expr> ternary() {
    Optional<Ast.Expr> e = orExpr();
    if (e.isPresent() && peek().getKind() == Ast.Token.Kind.QUESTION) {
      Ast.Expr cond = e.get();
      Ast.Pos.Builder ternPos = cond.getPosition().toBuilder();
      Ast.Ternary.Builder ternary = Ast.Ternary.newBuilder().setCond(cond);
      eat(ternPos);

      Optional<Ast.Expr> e1 = ternary();
      e1.ifPresent(
          ex -> {
            ternary.setLeft(ex);
            appendPos(ternPos, ex.getPosition());
          });

      eatIfExpected("Expected :", Ast.Token.Kind.COLON, ternPos);

      Optional<Ast.Expr> e2 = ternary();
      e2.ifPresent(
          ex -> {
            ternary.setRight(ex);
            appendPos(ternPos, ex.getPosition());
          });
      ternary.setPosition(ternPos);
      return Optional.of(
          Ast.Expr.newBuilder().setConditional(ternary).setPosition(ternPos).build());
    }
    return e;
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
      Ast.Expr lhs = e.get();
      Ast.Pos.Builder binPos = lhs.getPosition().toBuilder();
      Ast.BinaryExpr.Builder binary = Ast.BinaryExpr.newBuilder().setLeft(lhs);

      Ast.Token.Kind opKind = eat(binPos).getKind();
      binary.setOperator(binOps.get(opKind));

      Optional<Ast.Expr> rhs = next.get();
      rhs.ifPresent(
          ex -> {
            binary.setRight(ex);
            appendPos(binPos, ex.getPosition());
          });
      binary.setPosition(binPos);
      e = Optional.of(Ast.Expr.newBuilder().setBinary(binary).setPosition(binPos).build());
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

  private static final ImmutableMap<Ast.Token.Kind, Ast.UnaryOp> unaryOps =
      ImmutableMap.of(
          Ast.Token.Kind.MINUS, Ast.UnaryOp.NEGATE,
          Ast.Token.Kind.EXCLAIM, Ast.UnaryOp.NOT,
          Ast.Token.Kind.INCREMENT, Ast.UnaryOp.PREFIX_INCREMENT,
          Ast.Token.Kind.DECREMENT, Ast.UnaryOp.PREFIX_DECREMENT);

  private Optional<Ast.Expr> unaryExpr() {
    if (peek().getKind() == Ast.Token.Kind.MINUS && peek(1).getKind() == Ast.Token.Kind.INT_LIT) {
      // Was actually negative constant, not unary operator
      Ast.Value.Builder value = Ast.Value.newBuilder();
      Ast.Pos.Builder valPos = Ast.Pos.newBuilder();
      eat(valPos);
      String text = eat(valPos).getText();
      int lit = 0;
      try {
        lit = Integer.parseInt("-" + text);
      } catch (NumberFormatException nfe) {
        errLast("Integer " + text + " out of range");
      }
      value.setIntLiteral(lit).setPosition(valPos);
      return Optional.of(Ast.Expr.newBuilder().setValue(value).setPosition(valPos).build());
    } else if (unaryOps.containsKey(peek().getKind())) {
      Ast.UnaryExpr.Builder unary =
          Ast.UnaryExpr.newBuilder().setOperator(unaryOps.get(peek().getKind()));
      Ast.Pos.Builder unaryPos = Ast.Pos.newBuilder();
      eat(unaryPos);
      unaryExpr()
          .ifPresent(
              ex -> {
                unary.setExpr(ex);
                appendPos(unaryPos, ex.getPosition());
              });
      unary.setPosition(unaryPos);
      return Optional.of(Ast.Expr.newBuilder().setUnary(unary).setPosition(unaryPos).build());
    }
    return unary2Expr();
  }

  private Optional<Ast.Expr> unary2Expr() {
    Optional<Ast.Expr> e;
    if (peek().getKind() != Ast.Token.Kind.IDENTIFIER) {
      e = literal();
    } else if (peek(1).getKind() == Ast.Token.Kind.LPAREN) {
      e = Optional.of(funcCall(Optional.empty()));
    } else {
      e = reference();
    }
    while (true) {
      Ast.Token.Kind next = peek().getKind();
      if (next == Ast.Token.Kind.INCREMENT && e.isPresent()) {
        // TODO
        eat(null);
      } else if (next == Ast.Token.Kind.DECREMENT && e.isPresent()) {
        // TODO
        eat(null);
      } else if (next == Ast.Token.Kind.LBRACKET && e.isPresent()) {
        e = Optional.of(arrayIndex(e.get()));
      } else if (next == Ast.Token.Kind.DOT
          && peek(1).getKind() == Ast.Token.Kind.IDENTIFIER
          && e.isPresent()) {
        eat(null);
        if (peek(1).getKind() == Ast.Token.Kind.LPAREN) {
          e = Optional.of(funcCall(e));
        } else {
          e = Optional.of(select(e.get()));
        }
      } else if (next == Ast.Token.Kind.LPAREN) {
        errPeek("Expected function name");
        eatLineUntil(last, STATEMENT_FAILURE_ENDS);
        break;
      } else {
        break;
      }
    }
    return e;
  }

  private Ast.Expr arrayIndex(Ast.Expr lhs) {
    Ast.Pos.Builder accessPos = lhs.getPosition().toBuilder();
    Ast.BinaryExpr.Builder arrayAccess =
        Ast.BinaryExpr.newBuilder().setOperator(Ast.BinaryOp.ARRAY_ACCESS).setLeft(lhs);
    eat(accessPos);
    Optional<Ast.Expr> rhs = expression();
    rhs.ifPresent(
        ex -> {
          arrayAccess.setRight(ex);
          appendPos(accessPos, ex.getPosition());
        });
    eatIfExpected("Expected ]", Ast.Token.Kind.RBRACKET, accessPos);
    arrayAccess.setPosition(accessPos);
    return Ast.Expr.newBuilder().setBinary(arrayAccess).setPosition(accessPos).build();
  }

  private Ast.Expr funcCall(Optional<Ast.Expr> lhs) {
    Ast.FuncCall.Builder call = Ast.FuncCall.newBuilder();
    Ast.Pos.Builder callPos = Ast.Pos.newBuilder();
    lhs.ifPresent(
        ex -> {
          call.setCalledOn(ex);
          appendPos(callPos, ex.getPosition());
        });
    Ast.Identifier funcName = identifier("Expected identifier", callPos).get();
    eat(callPos); // The following LPAREN
    call.setFunction(funcName);
    while (peek().getKind() != Ast.Token.Kind.RPAREN) {
      Optional<Ast.Expr> param = expression();
      if (!param.isPresent()) {
        break;
      }
      Ast.Expr val = param.get();
      appendPos(callPos, val.getPosition());
      call.addParams(val);
      if (peek().getKind() != Ast.Token.Kind.COMMA) {
        break;
      }
      eat(callPos);
    }
    eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, callPos);
    call.setPosition(callPos);
    return Ast.Expr.newBuilder().setCall(call).setPosition(callPos).build();
  }

  private Ast.Expr select(Ast.Expr lhs) {
    Ast.Pos.Builder selectPos = lhs.getPosition().toBuilder();
    Ast.Select.Builder select = Ast.Select.newBuilder().setCalledOn(lhs);
    select.setField(identifier("Expected identifier", selectPos).get());
    return Ast.Expr.newBuilder().setSelect(select).setPosition(selectPos).build();
  }

  private Optional<Ast.Expr> reference() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.Identifier identifier = identifier("Expected identifier", null).get();
      return Optional.of(
          Ast.Expr.newBuilder()
              .setReference(identifier)
              .setPosition(identifier.getPosition())
              .build());
    }
    errPeek("Invalid expression start " + peek().getText());
    return Optional.empty();
  }

  private Optional<Ast.Expr> literal() {
    Ast.Value.Builder value = Ast.Value.newBuilder();
    Ast.Pos.Builder valuePos = Ast.Pos.newBuilder();
    if (peek().getKind() == Ast.Token.Kind.TRUE) {
      eat(valuePos);
      value.setBoolLiteral(true);
    } else if (peek().getKind() == Ast.Token.Kind.FALSE) {
      eat(valuePos);
      value.setBoolLiteral(false);
    } else if (peek().getKind() == Ast.Token.Kind.INT_LIT) {
      int lit = 0;
      String text = eat(valuePos).getText();
      try {
        lit = Integer.parseInt(text);
      } catch (NumberFormatException nfe) {
        errLast("Integer " + text + " out of range");
      }
      value.setIntLiteral(lit);
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_CHAR_LIT) {
      errPeek("Unterminated character literal");
      eat(valuePos);
      value.setCharLiteral(0);
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_STRING_LIT) {
      errPeek("Unterminated string literal");
      eat(valuePos);
      value.setStringLiteral("");
    } else if (peek().getKind() == Ast.Token.Kind.CHAR_LIT) {
      String content = eat(valuePos).getText();
      content = content.substring(1, content.length() - 1); // Strip ''
      content = unescape(content);
      if (content.length() != 1) {
        errLast("Invalid character literal");
      }
      if (!content.isEmpty()) {
        value.setCharLiteral(content.charAt(0));
      }
    } else if (peek().getKind() == Ast.Token.Kind.STRING_LIT) {
      String content = eat(valuePos).getText();
      content = content.substring(1, content.length() - 1); // Strip ""
      content = unescape(content);
      value.setStringLiteral(content);
    } else {
      return parenthesized();
    }
    value.setPosition(valuePos);
    return Optional.of(Ast.Expr.newBuilder().setValue(value).setPosition(valuePos).build());
  }

  private Optional<Ast.Expr> parenthesized() {
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat(null);
      Optional<Ast.Expr> ret = expression();
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, null);
      return ret;
    }
    errPeek("Unexpected " + peek().getText());
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

  private Optional<Ast.Type> type(Ast.Pos.Builder pos) {
    Ast.Type.Builder type = Ast.Type.newBuilder();
    Ast.Pos.Builder typePos = Ast.Pos.newBuilder();
    Optional<Ast.Identifier> nameOpt = identifier("Expected type name", typePos);
    if (!nameOpt.isPresent()) {
      return Optional.empty();
    }
    type.setName(nameOpt.get().getName());
    while (peek().getKind() == Ast.Token.Kind.LBRACKET) {
      System.out.println("Is array!");
      Ast.Token lbrack = eat(typePos);
      if (peek().getKind() == Ast.Token.Kind.RBRACKET) {
        eat(typePos);
        type.addDimensions(
            Ast.Type.ArrayDimension.newBuilder().setDimension(0).setPosition(lbrack.getPosition()));
        continue;
      }
      Optional<Ast.Expr> expression = expression();
      if (expression.isPresent()) {
        Ast.Value dimValue = expression.get().getValue();
        if (dimValue.getLiteralCase() != Ast.Value.LiteralCase.INT_LITERAL) {
          errAt("Array dimensions must be integer constants", lbrack);
        } else {
          type.addDimensions(
              Ast.Type.ArrayDimension.newBuilder()
                  .setDimension(dimValue.getIntLiteral())
                  .setPosition(expression.get().getPosition())
                  .build());
          appendPos(typePos, dimValue.getPosition());
        }
      } else {
        if (peek().getKind() != Ast.Token.Kind.RBRACKET) {
          eatLineUntil(
              last,
              ImmutableSet.of(
                  Ast.Token.Kind.RBRACKET, Ast.Token.Kind.EQUALS, Ast.Token.Kind.COMMA));
        }
      }
      eatIfExpected("Expected closing ]", Ast.Token.Kind.RBRACKET, typePos);
    }
    appendPos(pos, typePos.build());
    return Optional.of(type.setPosition(typePos).build());
  }

  private Optional<Ast.Identifier> identifier(String error, Ast.Pos.Builder pos) {
    Ast.Token name = eatIfExpected(error, Ast.Token.Kind.IDENTIFIER, null);
    if (name == null) {
      return Optional.empty();
    }
    appendPos(pos, name.getPosition());
    return Optional.of(
        Ast.Identifier.newBuilder()
            .setName(name.getText())
            .setPosition(name.getPosition())
            .build());
  }

  /**
   * Consumes the next token if it was of kind {@code kind}, otherwise adding an error for the
   * token.
   */
  private Ast.Token eatIfExpected(String errorMsg, Ast.Token.Kind kind, Ast.Pos.Builder pos) {
    Ast.Token t = peek();
    if (t.getKind() != kind) {
      errPeek(errorMsg);
      return null;
    }
    eat(pos);
    return t;
  }

  /**
   * When expecting a ; to finish a statement, consume the remainder of the line if there was no ;
   */
  private void finishLine() {
    if (eatIfExpected("Expected ;", Ast.Token.Kind.SEMICOLON, null) == null) {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
      if (peek().getKind() == Ast.Token.Kind.SEMICOLON) {
        eat(null);
      }
    }
  }

  /** Eats all tokens on the same line as that of {@code tok}. */
  private void eatLine(Ast.Token tok) {
    eatLineUntil(tok, Collections.EMPTY_SET);
  }

  /**
   * Eats all tokens on the same line as that of {@code tok}, or until {@code kind is encountered}.
   */
  private void eatLineUntil(Ast.Token tok, Set<Ast.Token.Kind> kinds) {
    while (peek().getKind() != Ast.Token.Kind.EOF
        && !kinds.contains(peek().getKind())
        && peek().getPosition().getLine() == tok.getPosition().getLine()) {
      eat(null);
    }
  }

  private void eatUntil(Ast.Token.Kind kind) {
    while (peek().getKind() != Ast.Token.Kind.EOF && peek().getKind() != kind) {
      eat(null);
    }
  }

  private void errAt(String msg, Ast.Token tok) {
    err.error(tok.getPosition(), msg);
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
  private Ast.Token peek(int offset) {
    while (lookahead.size() <= offset) {
      lookahead.add(toks.next());
    }
    return lookahead.get(offset);
  }

  /** Returns the next token of the stream. */
  private Ast.Token peek() {
    return peek(0);
  }

  /**
   * Returns and consumes the next token of the stream, updating the end of the provided {@code pos}
   * to include the token.
   */
  private Ast.Token eat(Ast.Pos.Builder pos) {
    Ast.Token ret = last = lookahead.empty() ? toks.next() : lookahead.poll();
    if (pos != null) {
      appendPos(pos, ret.getPosition());
    }
    return ret;
  }

  /**
   * Updates the position {@code begin} with the end of the position {@code end}. If {@code begin}
   * is unset, the beginning is also updated to that of {@code end}.
   */
  private static void appendPos(Ast.Pos.Builder begin, Ast.Pos end) {
    if (begin == null) {
      return;
    }
    if (begin.getLine() == 0) {
      begin.setLine(end.getLine());
      begin.setCol(end.getCol());
      begin.setOffset(end.getOffset());
    }
    begin.setEndCol(end.getEndCol()).setEndLine(end.getEndLine()).setEndOffset(end.getEndOffset());
  }

  /**
   * Merges two positions in the same way that {@link #appendPos(Ast.Pos.Builder, Ast.Pos)} does.
   */
  private static Ast.Pos mergePos(Ast.Pos start, Ast.Pos end) {
    Ast.Pos.Builder begin = start.toBuilder();
    if (begin.getLine() == 0) {
      begin.setLine(end.getLine());
      begin.setCol(end.getCol());
      begin.setOffset(end.getOffset());
    }
    return begin
        .setEndCol(end.getEndCol())
        .setEndLine(end.getEndLine())
        .setEndOffset(end.getEndOffset())
        .build();
  }

  /**
   * Parses a Spooky program using the given token source. The returned {@link Ast.Program} is only
   * valid if no errors were added; however, it may still be passed to the {@link
   * se.jsannemo.spooky.compiler.typecheck.TypeChecker} even if there were syntax errors. In this
   * case, a best-effort type-checking will be performed.
   *
   * <p>The provided {@link Tokenizer} is always fully exhausted after a call to {@link
   * #parse(Tokenizer, Errors)}.
   */
  public static Ast.Program parse(Tokenizer toks, Errors err) {
    return new Parser(toks, err).parse();
  }
}
