package se.jsannemo.spooky.compiler.parser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Empty;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.util.CircularQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
  private final Ast.Program.Builder program = Ast.Program.newBuilder();
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
    Ast.StructDecl.Builder struct = Ast.StructDecl.newBuilder();
    eat(struct.getPositionBuilder());
    Optional<Ast.Identifier> name = identifier("Expected struct name", struct.getPositionBuilder());
    name.ifPresent(struct::setName);
    if (peek().getKind() == Ast.Token.Kind.LBRACE) {
      eat(struct.getPositionBuilder());
      while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
        Ast.StructField.Builder field = struct.addFieldsBuilder();
        field.setName(identifier("Expected struct field name", field.getPositionBuilder()).get());
        if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, field.getPositionBuilder()) == null) {
          eatLineUntil(last, STATEMENT_FAILURE_ENDS);
          continue;
        }
        Optional<Ast.Type> type = type(field.getPositionBuilder());
        type.ifPresent(field::setType);
        finishLine();
        appendPos(struct.getPositionBuilder(), field.getPosition());
      }
      if (eatIfExpected("Expected }", Ast.Token.Kind.RBRACE, struct.getPositionBuilder()) == null) {
        eatUntil(Ast.Token.Kind.RBRACE);
        if (peek().getKind() == Ast.Token.Kind.RBRACE) {
          eat(null);
        }
      }
    } else if (name.isPresent()) {
      errPeek("Expected {");
    }
    return Optional.of(struct.build());
  }

  private Optional<Ast.FuncDecl> decl(Ast.Token kw) {
    Ast.FuncDecl.Builder decl = Ast.FuncDecl.newBuilder();
    if (kw != null) {
      appendPos(decl.getPositionBuilder(), kw.getPosition());
    }
    Optional<Ast.Identifier> name = identifier("Expected function name", decl.getPositionBuilder());
    name.ifPresent(decl::setName);
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat(decl.getPositionBuilder());
      parameterList(decl);
      if (eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, decl.getPositionBuilder()) == null) {
        eatLine(last);
      }
    }
    if (peek().getKind() == Ast.Token.Kind.ARROW) {
      eat(decl.getPositionBuilder());
      type(decl.getPositionBuilder()).ifPresent(decl::setReturnType);
    }
    return Optional.of(decl.build());
  }

  private void parameterList(Ast.FuncDecl.Builder builder) {
    while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.FuncParam.Builder param = builder.addParamsBuilder();
      param.setName(identifier("Expected parameter name", param.getPositionBuilder()).get());
      eatIfExpected("Expected :", Ast.Token.Kind.COLON, param.getPositionBuilder());
      Optional<Ast.Type> type = type(param.getPositionBuilder());
      type.ifPresent(param::setType);
      if (peek().getKind() == Ast.Token.Kind.COMMA) {
        eat(param.getPositionBuilder());
      }
      appendPos(builder.getPositionBuilder(), param.getPosition());
    }
  }

  private Optional<Ast.FuncDecl> extern() {
    return decl(eatIfExpected("Expected extern", Ast.Token.Kind.EXTERN, null));
  }

  private Optional<Ast.Func> func() {
    Ast.Func.Builder func = Ast.Func.newBuilder();
    Optional<Ast.FuncDecl> maybeDecl =
        decl(eatIfExpected("Expected 'func'", Ast.Token.Kind.FUNC, null));
    if (maybeDecl.isEmpty()) {
      return Optional.empty();
    }
    Ast.FuncDecl decl = maybeDecl.get();
    appendPos(func.getPositionBuilder(), decl.getPosition());
    func.setDecl(decl);
    Optional<Ast.Statement> body = block();
    body.ifPresent(
        b -> {
          func.setBody(b);
          appendPos(func.getPositionBuilder(), b.getPosition());
        });
    return Optional.of(func.build());
  }

  private Optional<Ast.Statement> block() {
    Ast.Statement.Builder statement = Ast.Statement.newBuilder();
    Ast.Block.Builder block = statement.getBlockBuilder();
    Ast.Token lbrace =
        eatIfExpected("Expected block", Ast.Token.Kind.LBRACE, block.getPositionBuilder());
    if (lbrace == null) {
      return Optional.empty();
    }
    while (true) {
      Ast.Token nx = peek();
      if (nx.getKind() == Ast.Token.Kind.RBRACE) {
        eat(block.getPositionBuilder());
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
      appendPos(block.getPositionBuilder(), st.get().getPosition());
      block.addBody(st.get());
    }
    return Optional.of(statement.setPosition(block.getPositionBuilder()).build());
  }

  private Optional<Ast.Statement> statement() {
    Ast.Token.Kind nx = peek().getKind();
    if (nx == Ast.Token.Kind.SEMICOLON) {
      Ast.Statement.Builder st = Ast.Statement.newBuilder();
      Ast.Block.Builder block = st.getBlockBuilder();
      eat(block.getPositionBuilder());
      st.setPosition(block.getPositionBuilder());
      return Optional.of(st.build());
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
    Ast.Statement.Builder stmt = Ast.Statement.newBuilder();
    Ast.VarDecl.Builder declBuilder = stmt.getDeclBuilder();
    Optional<Ast.Identifier> name =
        identifier("Expected variable name", declBuilder.getPositionBuilder());
    if (name.isEmpty()) {
      return Optional.empty();
    }
    declBuilder.setName(name.get());
    if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, declBuilder.getPositionBuilder())
        != null) {
      type(declBuilder.getPositionBuilder()).ifPresent(declBuilder::setType);
    }
    if (eatIfExpected("Expected =", Ast.Token.Kind.ASSIGN, declBuilder.getPositionBuilder())
        != null) {
      initVal()
          .ifPresent(
              ex -> {
                declBuilder.setInit(ex);
                appendPos(declBuilder.getPositionBuilder(), ex.getPosition());
              });
    }
    return Optional.of(stmt.setPosition(declBuilder.getPosition()).build());
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
    Ast.Expr.Builder expr = Ast.Expr.newBuilder();
    Ast.DefaultInit.Builder def = expr.getDefaultInitBuilder();
    eatIfExpected("Expected {", Ast.Token.Kind.DEFAULT, def.getPositionBuilder());
    expr.setDefaultInit(def).setPosition(def.getPosition());
    return expr.build();
  }

  private Ast.Expr structInit() {
    Ast.Expr.Builder expr = Ast.Expr.newBuilder();
    Ast.StructLit.Builder struct = expr.getStructBuilder();
    eatIfExpected("Expected {", Ast.Token.Kind.LBRACE, struct.getPositionBuilder());
    while (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      boolean err = false;
      boolean done = false;
      Ast.FieldInit.Builder field = struct.addValuesBuilder();
      field.setField(identifier("Expected identifier", field.getPosBuilder()).get());
      if (eatIfExpected("Expected :", Ast.Token.Kind.COLON, field.getPosBuilder()) != null) {
        Optional<Ast.Expr> value = initVal();
        if (value.isPresent()) {
          Ast.Expr e = value.get();
          field.setValue(e);
          appendPos(field.getPosBuilder(), e.getPosition());
          if (peek().getKind() == Ast.Token.Kind.COMMA) {
            eat(struct.getPositionBuilder());
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
      if (done) {
        break;
      }
    }
    if (eatIfExpected("Expected }", Ast.Token.Kind.RBRACE, struct.getPositionBuilder()) == null) {
      eatUntil(Ast.Token.Kind.RBRACE);
      if (peek().getKind() == Ast.Token.Kind.RBRACE) {
        eat(null);
      }
    }
    expr.setPosition(struct.getPosition());
    return expr.build();
  }

  private Ast.Expr arrayInit() {
    Ast.Expr.Builder expr = Ast.Expr.newBuilder();
    Ast.ArrayLit.Builder array = expr.getArrayBuilder();
    while (peek().getKind() == Ast.Token.Kind.LBRACKET) {
      Ast.ArrayLit.Dimension.Builder dim = array.addDimensionsBuilder();
      eat(dim.getPositionBuilder());
      if (peek().getKind() == Ast.Token.Kind.RBRACKET) {
        dim.setInferred(Empty.getDefaultInstance());
      } else {
        Optional<Ast.Expr> dimension = expression();
        dimension.ifPresent(
            ex -> {
              dim.setExpression(ex);
              appendPos(dim.getPositionBuilder(), ex.getPosition());
            });
      }
      if (eatIfExpected("Expected ]", Ast.Token.Kind.RBRACKET, dim.getPositionBuilder()) == null) {
        eatLineUntil(last, ImmutableSet.of(Ast.Token.Kind.SEMICOLON));
      }
      appendPos(array.getPositionBuilder(), dim.getPosition());
    }
    arrayValues(array);
    return expr.setPosition(array.getPosition()).build();
  }

  private void arrayValues(Ast.ArrayLit.Builder array) {
    if (peek().getKind() == Ast.Token.Kind.LBRACE) {
      eat(array.getPositionBuilder());
      while (true) {
        boolean isFill = peek().getKind() == Ast.Token.Kind.ELLIPSIS;
        Ast.Expr.Builder nextValue;
        if (isFill) {
          eat(array.getPositionBuilder());
          array.setShouldFill(true);
          if (peek().getKind() == Ast.Token.Kind.RBRACE) {
            break;
          }
          nextValue = array.getFillBuilder();
        } else {
          nextValue = array.addValuesBuilder();
        }
        if (!arrayValue(array, nextValue) || isFill) {
          break;
        }
      }
      eatIfExpected("Expected }", Ast.Token.Kind.RBRACE, array.getPositionBuilder());
    } else if (peek().getKind() == Ast.Token.Kind.DEFAULT) {
      Ast.Expr fill = defaultInit();
      array.setFill(fill);
      appendPos(array.getPositionBuilder(), fill.getPosition());
    }
  }

  private boolean arrayValue(Ast.ArrayLit.Builder array, Ast.Expr.Builder valueBuilder) {
    // This is a multidimensional array; any {}-delimited values that is used in the initializer is
    // another array with one fewer dimension.
    if (peek().getKind() == Ast.Token.Kind.LBRACE && array.getDimensionsCount() > 1) {
      Ast.ArrayLit.Builder subarray = valueBuilder.getArrayBuilder();
      subarray.addAllDimensions(array.getDimensionsList().subList(1, array.getDimensionsCount()));
      arrayValues(subarray);
      valueBuilder.setPosition(subarray.getPosition());
      appendPos(array.getPositionBuilder(), valueBuilder.getPosition());
    } else {
      Optional<Ast.Expr> val = initVal();
      val.ifPresent(
          v -> {
            valueBuilder.mergeFrom(v);
            appendPos(array.getPositionBuilder(), v.getPosition());
          });
    }
    if (peek().getKind() != Ast.Token.Kind.COMMA) {
      return false;
    }
    eat(array.getPositionBuilder());
    return true;
  }

  private Optional<Ast.Statement> returnStmt() {
    Ast.Statement.Builder stmt = Ast.Statement.newBuilder();
    Ast.ReturnValue.Builder returnBuilder = stmt.getReturnValueBuilder();
    Ast.Token retTok =
        eatIfExpected("Expected return", Ast.Token.Kind.RETURN, returnBuilder.getPositionBuilder());
    if (retTok == null) {
      return Optional.empty();
    }
    if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
      expression()
          .ifPresent(
              expr -> {
                returnBuilder.setValue(expr);
                appendPos(returnBuilder.getPositionBuilder(), expr.getPosition());
              });
    }
    finishLine();
    return Optional.of(stmt.setPosition(returnBuilder.getPosition()).build());
  }

  private Optional<Ast.Statement> whileLoop() {
    Ast.Statement.Builder stmt = Ast.Statement.newBuilder();
    Ast.Loop.Builder loop = stmt.getLoopBuilder();
    if (eatIfExpected("Expected while", Ast.Token.Kind.WHILE, loop.getPositionBuilder()) == null) {
      return Optional.empty();
    }
    if (peek().getKind() == Ast.Token.Kind.LPAREN) {
      eat(loop.getPositionBuilder());
      expression()
          .ifPresent(
              expr -> {
                loop.setCondition(expr);
                appendPos(loop.getPositionBuilder(), expr.getPosition());
              });
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, loop.getPositionBuilder());
    }
    statement()
        .ifPresent(
            s -> {
              loop.setBody(s);
              appendPos(loop.getPositionBuilder(), s.getPosition());
            });
    return Optional.of(stmt.setPosition(loop.getPosition()).build());
  }

  private Optional<Ast.Statement> forLoop() {
    Ast.Statement.Builder stmt = Ast.Statement.newBuilder();
    Ast.Loop.Builder loop = stmt.getLoopBuilder();
    if (eatIfExpected("Expected for", Ast.Token.Kind.FOR, loop.getPositionBuilder()) == null) {
      return Optional.empty();
    }
    if (eatIfExpected("Expected (", Ast.Token.Kind.LPAREN, loop.getPositionBuilder()) != null) {
      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        Optional<Ast.Statement> init = simpleStatement();
        if (init.isPresent()) {
          Ast.Statement s = init.get();
          loop.setInit(s);
          appendPos(loop.getPositionBuilder(), s.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected ;", Ast.Token.Kind.SEMICOLON, loop.getPositionBuilder());

      if (peek().getKind() != Ast.Token.Kind.SEMICOLON) {
        Optional<Ast.Expr> cond = expression();
        if (cond.isPresent()) {
          Ast.Expr e = cond.get();
          loop.setCondition(e);
          appendPos(loop.getPositionBuilder(), e.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected ;", Ast.Token.Kind.SEMICOLON, loop.getPositionBuilder());

      if (peek().getKind() != Ast.Token.Kind.RPAREN) {
        Optional<Ast.Statement> inc = exprStatement();
        if (inc.isPresent()) {
          Ast.Statement s = inc.get();
          loop.setIncrement(s);
          appendPos(loop.getPositionBuilder(), s.getPosition());
        } else {
          eatLineUntil(last, COND_FAILURE_ENDS);
        }
      }
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, loop.getPositionBuilder());
    } else {
      eatLineUntil(last, STATEMENT_FAILURE_ENDS);
    }
    statement()
        .ifPresent(
            s -> {
              loop.setBody(s);
              appendPos(loop.getPositionBuilder(), s.getPosition());
            });
    return Optional.of(stmt.build());
  }

  private Optional<Ast.Statement> simpleStatement() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER
        && peek(1).getKind() == Ast.Token.Kind.COLON) {
      return varDecl();
    }
    return exprStatement();
  }

  private Optional<Ast.Statement> conditional() {
    Ast.Statement.Builder stmt = Ast.Statement.newBuilder();
    Ast.Conditional.Builder cond = stmt.getConditionalBuilder();
    if (eatIfExpected("Expected if", Ast.Token.Kind.IF, cond.getPositionBuilder()) == null) {
      return Optional.empty();
    }
    if (eatIfExpected("Expected (", Ast.Token.Kind.LPAREN, cond.getPositionBuilder()) != null) {
      expression()
          .ifPresent(
              e -> {
                cond.setCondition(e);
                appendPos(cond.getPositionBuilder(), e.getPosition());
              });
      eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, cond.getPositionBuilder());
    }
    statement()
        .ifPresent(
            s -> {
              cond.setBody(s);
              appendPos(cond.getPositionBuilder(), s.getPosition());
            });
    if (peek().getKind() == Ast.Token.Kind.ELSE) {
      eat(cond.getPositionBuilder());
      statement()
          .ifPresent(
              s -> {
                cond.setElseBody(s);
                appendPos(cond.getPositionBuilder(), s.getPosition());
              });
    }
    return Optional.of(stmt.build());
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
      Ast.Expr.Builder expr = Ast.Expr.newBuilder();
      Ast.Assignment.Builder assign = expr.getAssignmentBuilder();

      Ast.Identifier name = identifier("Expected identifier", assign.getPositionBuilder()).get();
      assign.setVariable(name);

      Ast.Token opTok = eat(assign.getPositionBuilder());
      Ast.BinaryOp op = assignments.get(opTok.getKind());
      Optional<Ast.Expr> value = assignment();
      value.ifPresent(
          v -> {
            assign.setValue(v);
            appendPos(assign.getPositionBuilder(), v.getPosition());
          });
      assign.setCompound(op);
      return Optional.of(expr.build());
    }
    return ternary();
  }

  private Optional<Ast.Expr> ternary() {
    Optional<Ast.Expr> e = orExpr();
    if (e.isPresent() && peek().getKind() == Ast.Token.Kind.QUESTION) {
      Ast.Expr cond = e.get();
      Ast.Expr.Builder builder = Ast.Expr.newBuilder();
      Ast.Ternary.Builder conditionalBuilder =
          builder.getConditionalBuilder().setPosition(cond.getPosition()).setCond(cond);
      eat(conditionalBuilder.getPositionBuilder());

      Optional<Ast.Expr> e1 = ternary();
      e1.ifPresent(
          ex -> {
            conditionalBuilder.setLeft(ex);
            appendPos(conditionalBuilder.getPositionBuilder(), ex.getPosition());
          });

      eatIfExpected("Expected :", Ast.Token.Kind.COLON, conditionalBuilder.getPositionBuilder());

      Optional<Ast.Expr> e2 = ternary();
      e2.ifPresent(
          ex -> {
            conditionalBuilder.setRight(ex);
            appendPos(conditionalBuilder.getPositionBuilder(), ex.getPosition());
          });

      builder.setPosition(cond.getPosition());
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
      Ast.Expr lhs = e.get();
      Ast.Expr.Builder expr = Ast.Expr.newBuilder();
      Ast.BinaryExpr.Builder binary =
          expr.getBinaryBuilder().setLeft(lhs).setPosition(lhs.getPosition());

      Ast.Token.Kind opKind = eat(binary.getPositionBuilder()).getKind();
      binary.setOperator(binOps.get(opKind));

      Optional<Ast.Expr> rhs = next.get();
      rhs.ifPresent(
          ex -> {
            binary.setRight(ex);
            appendPos(binary.getPositionBuilder(), ex.getPosition());
          });
      expr.setPosition(binary.getPosition());
      e = Optional.of(expr.build());
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
      Ast.Expr.Builder expr = Ast.Expr.newBuilder();
      Ast.Value.Builder value = expr.getValueBuilder();
      eat(value.getPositionBuilder());
      String text = eat(value.getPositionBuilder()).getText();
      int lit = 0;
      try {
        lit = Integer.parseInt("-" + text);
      } catch (NumberFormatException nfe) {
        errLast("Integer " + text + " out of range");
      }
      value.setIntLiteral(lit);
      expr.setPosition(value.getPosition());
      return Optional.of(expr.build());
    } else if (unaryOps.containsKey(peek().getKind())) {
      Ast.Expr.Builder expr = Ast.Expr.newBuilder();
      Ast.UnaryExpr.Builder unary =
          expr.getUnaryBuilder().setOperator(unaryOps.get(peek().getKind()));
      eat(unary.getPositionBuilder());
      unaryExpr()
          .ifPresent(
              ex -> {
                unary.setExpr(ex);
                appendPos(unary.getPositionBuilder(), ex.getPosition());
              });
      expr.setPosition(unary.getPosition());
      return Optional.of(expr.build());
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
    Ast.Expr.Builder newE = Ast.Expr.newBuilder();
    Ast.BinaryExpr.Builder arrayAccess =
        newE.getBinaryBuilder()
            .setPosition(lhs.getPosition())
            .setOperator(Ast.BinaryOp.ARRAY_ACCESS)
            .setLeft(lhs);
    eat(arrayAccess.getPositionBuilder());
    Optional<Ast.Expr> rhs = expression();
    rhs.ifPresent(
        ex -> {
          arrayAccess.setRight(ex);
          appendPos(arrayAccess.getPositionBuilder(), ex.getPosition());
        });
    eatIfExpected("Expected ]", Ast.Token.Kind.RBRACKET, arrayAccess.getPositionBuilder());
    newE.setPosition(arrayAccess.getPosition());
    return newE.build();
  }

  private Ast.Expr funcCall(Optional<Ast.Expr> lhs) {
    Ast.Expr.Builder expr = Ast.Expr.newBuilder();
    Ast.FuncCall.Builder call = expr.getCallBuilder();
    lhs.ifPresent(
        ex -> {
          call.setCalledOn(ex);
          appendPos(call.getPositionBuilder(), ex.getPosition());
        });
    Ast.Identifier funcName = identifier("Expected identifier", call.getPositionBuilder()).get();
    eat(call.getPositionBuilder()); // The following LPAREN
    call.setFunction(funcName);
    while (peek().getKind() != Ast.Token.Kind.RPAREN) {
      Optional<Ast.Expr> param = expression();
      if (param.isEmpty()) {
        break;
      }
      Ast.Expr val = param.get();
      appendPos(call.getPositionBuilder(), val.getPosition());
      call.addParams(val);
      if (peek().getKind() != Ast.Token.Kind.COMMA) {
        break;
      }
      eat(call.getPositionBuilder());
    }
    eatIfExpected("Expected )", Ast.Token.Kind.RPAREN, call.getPositionBuilder());
    return expr.setPosition(call.getPosition()).build();
  }

  private Ast.Expr select(Ast.Expr lhs) {
    Ast.Expr.Builder newE = Ast.Expr.newBuilder();
    Ast.Select.Builder select =
        newE.getSelectBuilder().setPosition(lhs.getPosition()).setCalledOn(lhs);
    select.setField(identifier("Expected identifier", select.getPositionBuilder()).get());
    newE.setPosition(select.getPosition());
    return newE.build();
  }

  private Optional<Ast.Expr> reference() {
    if (peek().getKind() == Ast.Token.Kind.IDENTIFIER) {
      Ast.Expr.Builder expr = Ast.Expr.newBuilder();
      expr.setReference(identifier("Expected identifier", expr.getPositionBuilder()).get());
      return Optional.of(expr.build());
    }
    errPeek("Invalid expression start " + peek().getText());
    return Optional.empty();
  }

  private Optional<Ast.Expr> literal() {
    Ast.Expr.Builder expr = Ast.Expr.newBuilder();
    Ast.Value.Builder value = expr.getValueBuilder();
    if (peek().getKind() == Ast.Token.Kind.TRUE) {
      eat(value.getPositionBuilder());
      value.setBoolLiteral(true);
    } else if (peek().getKind() == Ast.Token.Kind.FALSE) {
      eat(value.getPositionBuilder());
      value.setBoolLiteral(false);
    } else if (peek().getKind() == Ast.Token.Kind.INT_LIT) {
      int lit = 0;
      String text = eat(value.getPositionBuilder()).getText();
      try {
        lit = Integer.parseInt(text);
      } catch (NumberFormatException nfe) {
        errLast("Integer " + text + " out of range");
      }
      value.setIntLiteral(lit);
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_CHAR_LIT) {
      errPeek("Unterminated character literal");
      eat(value.getPositionBuilder());
      value.setCharLiteral(0);
    } else if (peek().getKind() == Ast.Token.Kind.UNTERMINATED_STRING_LIT) {
      errPeek("Unterminated string literal");
      eat(value.getPositionBuilder());
      value.setStringLiteral("");
    } else if (peek().getKind() == Ast.Token.Kind.CHAR_LIT) {
      String content = eat(value.getPositionBuilder()).getText();
      content = content.substring(1, content.length() - 1); // Strip ''
      content = unescape(content);
      if (content.length() != 1) {
        errLast("Invalid character literal");
      }
      if (!content.isEmpty()) {
        value.setCharLiteral(content.charAt(0));
      }
    } else if (peek().getKind() == Ast.Token.Kind.STRING_LIT) {
      String content = eat(value.getPositionBuilder()).getText();
      content = content.substring(1, content.length() - 1); // Strip ""
      content = unescape(content);
      value.setStringLiteral(content);
    } else {
      return parenthesized();
    }
    return Optional.of(expr.setPosition(value.getPosition()).build());
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
    Ast.Type.Builder builder = Ast.Type.newBuilder();
    Optional<Ast.Identifier> nameOpt =
        identifier("Expected type name", builder.getPositionBuilder());
    if (nameOpt.isEmpty()) {
      return Optional.empty();
    }
    builder.setName(nameOpt.get().getName());
    int dim = 0;
    while (peek().getKind() == Ast.Token.Kind.LBRACKET) {
      eat(builder.getPositionBuilder());
      eatIfExpected("Expected closing ]", Ast.Token.Kind.RBRACKET, builder.getPositionBuilder());
      dim++;
    }
    appendPos(pos, builder.getPosition());
    return Optional.of(builder.setDimension(dim).build());
  }

  private Optional<Ast.Identifier> identifier(String error, Ast.Pos.Builder pos) {
    Ast.Identifier.Builder builder = Ast.Identifier.newBuilder();
    Ast.Token name = eatIfExpected(error, Ast.Token.Kind.IDENTIFIER, builder.getPositionBuilder());
    if (name == null) {
      return Optional.empty();
    } else {
      builder.setName(name.getText());
    }
    appendPos(pos, builder.getPosition());
    return Optional.of(builder.build());
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
    if (begin.getLine() == 0) {
      begin.setLine(end.getLine());
      begin.setCol(end.getCol());
      begin.setOffset(end.getOffset());
    }
    begin.setEndCol(end.getEndCol()).setEndLine(end.getEndLine()).setEndOffset(end.getEndOffset());
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
