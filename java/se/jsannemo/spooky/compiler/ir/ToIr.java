package se.jsannemo.spooky.compiler.ir;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Assignment;
import se.jsannemo.spooky.compiler.ast.BinaryExpr;
import se.jsannemo.spooky.compiler.ast.BinaryOp;
import se.jsannemo.spooky.compiler.ast.Conditional;
import se.jsannemo.spooky.compiler.ast.Expression;
import se.jsannemo.spooky.compiler.ast.Func;
import se.jsannemo.spooky.compiler.ast.FuncCall;
import se.jsannemo.spooky.compiler.ast.FuncDecl;
import se.jsannemo.spooky.compiler.ast.Identifier;
import se.jsannemo.spooky.compiler.ast.Loop;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.ast.SourceRange;
import se.jsannemo.spooky.compiler.ast.Statement;
import se.jsannemo.spooky.compiler.ast.Ternary;
import se.jsannemo.spooky.compiler.ast.UnaryExpr;
import se.jsannemo.spooky.compiler.ast.UnaryOp;
import se.jsannemo.spooky.compiler.ast.Value;
import se.jsannemo.spooky.compiler.ast.VarDecl;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrHalt;

public final class ToIr {

  private final Errors errors;
  private final IrContext ctx = new IrContext(new IrProgram());

  private ToIr(Errors errors) {
    this.errors = errors;
  }

  public static IrProgram generate(Program p, Errors errors) {
    return new ToIr(errors).generate(p);
  }

  /** Generates an IR representation of a program. */
  public IrProgram generate(Program p) {
    // Create root scope; will be used for e.g. global variables in the future.
    ctx.newScope();
    for (Func func : p.functions()) {
      funcDecl(func.decl(), false);
    }
    for (FuncDecl func : p.externs()) {
      funcDecl(func, true);
    }
    initDecl(p.globals());
    for (Func func : p.functions()) {
      function(func);
    }
    ctx.popScope();
    Preconditions.checkState(ctx.scope == null);
    return ctx.program;
  }

  private void initDecl(List<VarDecl> globals) {
    // Create special __init__ function which will run before main.
    IrFunction function = new IrFunction();
    function.returnSignature = IrType.VOID;
    ctx.program.functions.put("__init__", function);

    // Allocate stack space and create init code for all globals.
    // Falling through this function is okay, since main start right after.
    ctx.function = function;
    for (VarDecl global : globals) {
      varDecl(global, true);
    }
    ctx.function = null;
  }

  private void funcDecl(FuncDecl declaration, boolean extern) {
    IrFunction function = new IrFunction();
    function.returnSignature =
        declaration.returnType().map(IrType::fromTypeName).orElse(IrType.VOID);
    function.extern = extern;
    ctx.program.functions.put(declaration.name().text(), function);

    for (FuncDecl.FuncParam p : declaration.params()) {
      function.paramSignature.add(IrType.fromTypeName(p.type()));
    }
  }

  private void function(Func value) {
    String funcName = value.decl().name().text();
    ctx.function = ctx.program.functions.get(funcName);
    Preconditions.checkArgument(ctx.function != null, "Func has not been declared");

    // Root scope for function; only includes the parameters.
    IrContext.Scope cur = ctx.newScope();
    int offset = 0;
    for (FuncDecl.FuncParam param : value.decl().params().reverse()) {
      String pName = param.name().text();
      if (cur.vals.containsKey(pName)) {
        errors.error(param.pos(), "Parameter " + pName + " already defined.");
      }
      IrType type = IrType.fromTypeName(param.type());
      offset -= type.memSize();
      IrValue var = IrValue.ofTypeAndAddress(type, IrAddr.relSp(offset));
      cur.addVal(pName, var);
    }
    offset -= IrType.INT.memSize();
    ctx.function.retAddress = offset;
    offset -= ctx.function.returnSignature.memSize();
    ctx.function.retValue = offset;

    Statement body = value.body();
    // Main is special; automatically halts after execution rather than returning.
    if (funcName.equals("main")) {
      ctx.function.isMain = true;
      if (!ctx.function.returnSignature.equals(IrType.VOID)) {
        errors.error(value.decl().pos(), "Main function has non-void return type");
      }
    }
    statement(body);
    Optional<Statement> lastStatement = body.block().statements().reverse().stream().findFirst();
    if (lastStatement.isEmpty() || lastStatement.get().kind() != Statement.Kind.RETURN_VALUE) {
      if (ctx.function.returnSignature.equals(IrType.VOID)) {
        returns(null, Optional.empty());
      } else {
        errors.error(value.decl().pos(), "Last statement of non-void function must be return");
      }
    }

    ctx.popScope();
    ctx.function = null;
  }

  private void statementList(List<Statement> body) {
    ctx.newScope();
    for (Statement stmt : body) {
      statement(stmt);
    }
    ctx.popScope();
  }

  private void statement(Statement stmt) {
    switch (stmt.kind()) {
      case BLOCK -> statementList(stmt.block().statements());
      case VAR_DECL -> varDecl(stmt.varDecl(), false);
      case LOOP -> loop(stmt.loop());
      case CONDITIONAL -> conditional(stmt.conditional());
      case EXPRESSION -> {
        // Make sure we don't leak stack after an expression statement
        IrContext.Scope cur = ctx.scope;
        int sp = cur.spOffset;
        expr(stmt.expression());
        cur.spOffset = sp;
      }
      case RETURN_VALUE -> returns(stmt.returnValue().pos(), stmt.returnValue().value());
    }
  }

  private void varDecl(VarDecl varDecl, boolean isGlobal) {
    String varName = varDecl.name().text();
    IrContext.Scope cur = ctx.scope;
    Optional<IrValue> res = cur.resolve(varName);
    if (res.isPresent()) {
      errors.error(varDecl.name().pos(), "Variable " + varName + " already declared.");
    }

    IrType type = IrType.fromTypeName(varDecl.type());
    // Globals are not relative current stack-pointer, but start of stack.
    IrAddr varAddr =
        isGlobal
            ? IrAddr.absStack(cur.spOffset + IrAddr.NEXT_STACK.absStack())
            : IrAddr.relSp(cur.spOffset);
    IrValue var = IrValue.ofTypeAndAddress(type, varAddr);
    cur.addVal(varName, var);
    IrType valueType = expr(varDecl.init());
    if (!type.equals(valueType)) {
      errors.error(varDecl.init().pos(), "Invalid type conversion: " + valueType + " to " + type);
    }
    // Note: no SP movement here, since expr() will already have reserved memory for the variable.
  }

  private void returns(SourceRange retpos, Optional<Expression> ret) {
    boolean wantValue = !ctx.function.returnSignature.equals(IrType.VOID);
    if (!wantValue && ret.isPresent()) {
      errors.error(retpos, "Returning a value from void function");
      return;
    }
    if (wantValue && ret.isEmpty()) {
      errors.error(retpos, "Returning without value for non-void function");
      return;
    }
    int pos = ctx.scope.spOffset;
    if (ret.isPresent()) {
      IrType type = expr(ret.get());
      if (!type.equals(ctx.function.returnSignature)) {
        errors.error(retpos, "Return value has the wrong type");
      }
    }
    if (ctx.function.isMain) {
      ctx.function.newStatement(IrHalt.of());
      return;
    }
    if (ret.isPresent()) {
      ctx.function.newStatement(
          IrStatement.IrCopy.fromTo(IrAddr.relSp(pos), IrAddr.relSp(ctx.function.retValue)));
    }
    ctx.function.newStatement(IrStatement.IrJmpAdr.of(IrAddr.relSp(ctx.function.retAddress)));
  }

  private void loop(Loop loop) {
    ctx.newScope();
    IrStatement.IrLabel check = ctx.function.newLabel();
    IrStatement.IrLabel end = ctx.function.newLabel();

    // Init
    loop.init().ifPresent(this::statement);

    // Check
    ctx.function.newStatement(check);
    loop.condition()
        .ifPresent(
            cond -> {
              int condAddr = evalType(cond, IrType.BOOL);
              ctx.function.newStatement(IrStatement.IrJmpZero.of(end, IrAddr.relSp(condAddr)));
              ctx.scope.spOffset = condAddr;
            });

    // Iteration
    statement(loop.body());
    loop.increment().ifPresent(this::statement);
    ctx.function.newStatement(IrStatement.IrJmpZero.of(check, IrAddr.CONST_ZERO));

    // Post
    ctx.function.newStatement(end);
    ctx.popScope();
  }

  private void conditional(Conditional conditional) {
    int condAddr = evalType(conditional.condition(), IrType.BOOL);
    IrStatement.IrLabel elseStart = ctx.function.newLabel();
    IrStatement.IrLabel elseEnd = ctx.function.newLabel();

    ctx.function.newStatement(IrStatement.IrJmpZero.of(elseStart, IrAddr.relSp(condAddr)));
    statement(conditional.body());
    if (conditional.elseBody().isPresent()) {
      ctx.function.newStatement(IrStatement.IrJmp.of(elseEnd));
    }
    ctx.function.newStatement(elseStart);
    if (conditional.elseBody().isPresent()) {
      statement(conditional.elseBody().get());
      ctx.function.newStatement(elseEnd);
    }
  }

  private IrType expr(Expression expression) {
    return switch (expression.kind()) {
      case VALUE -> value(expression.value());
      case BINARY -> binary(expression.binary());
      case REFERENCE -> reference(expression.reference());
      case CALL -> functionCall(expression.call());
      case ASSIGNMENT -> assign(expression.assignment());
      case CONDITIONAL -> conditionalExpr(expression.conditional());
      case UNARY -> unary(expression.unary());
        // TODO
      case ARRAY, SELECT -> throw new UnsupportedOperationException("Not implemented");
    };
  }

  private IrType unary(UnaryExpr unary) {
    switch (unary.op()) {
      case NEGATE -> {
        int sp = evalType(unary.operand(), IrType.INT);
        ctx.function.newStatement(
            IrStatement.IrSub.forTermsAndTarget(
                IrAddr.CONST_ZERO, IrAddr.relSp(sp), IrAddr.relSp(sp)));
        return IrType.INT;
      }
      case NOT -> {
        int sp = evalType(unary.operand(), IrType.BOOL);
        ctx.function.newStatement(
            IrStatement.IrSub.forTermsAndTarget(
                IrAddr.CONST_ONE, IrAddr.relSp(sp), IrAddr.relSp(sp)));
        return IrType.BOOL;
      }
      case POSTFIX_INCREMENT, POSTFIX_DECREMENT, PREFIX_DECREMENT, PREFIX_INCREMENT -> {
        Expression reference = unary.operand();
        if (reference.kind() != Expression.Kind.REFERENCE) {
          errors.error(reference.pos(), "LHS of assignment is not variable");
          return IrType.ERROR;
        }
        String varName = reference.reference().text();
        Optional<IrValue> varOpt = ctx.scope.resolve(varName);
        if (varOpt.isEmpty()) {
          errors.error(reference.pos(), "Undefined variable " + varName);
          return IrType.ERROR;
        }
        IrValue var = varOpt.get();

        IrStatement stmt =
            unary.op() == UnaryOp.POSTFIX_INCREMENT || unary.op() == UnaryOp.PREFIX_INCREMENT
                ? IrStatement.IrAdd.forTermsAndTarget(
                    var.address(), IrAddr.CONST_ONE, var.address())
                : IrStatement.IrSub.forTermsAndTarget(
                    var.address(), IrAddr.CONST_ONE, var.address());

        if (unary.op() == UnaryOp.POSTFIX_INCREMENT || unary.op() == UnaryOp.POSTFIX_DECREMENT) {
          ctx.function.newStatement(
              IrStatement.IrCopy.fromTo(var.address(), IrAddr.relSp(ctx.scope.spOffset)));
          ctx.function.newStatement(stmt);
        } else {
          ctx.function.newStatement(stmt);
          ctx.function.newStatement(
              IrStatement.IrCopy.fromTo(var.address(), IrAddr.relSp(ctx.scope.spOffset)));
        }
        ctx.scope.spOffset += var.type().memSize();
      }
    }
    return IrType.ERROR;
  }

  private IrType conditionalExpr(Ternary conditional) {
    int address = ctx.scope.spOffset;
    int condAddr = evalType(conditional.cond(), IrType.BOOL);
    IrStatement.IrLabel elseStart = ctx.function.newLabel();
    IrStatement.IrLabel elseEnd = ctx.function.newLabel();

    ctx.function.newStatement(IrStatement.IrJmpZero.of(elseStart, IrAddr.relSp(condAddr)));
    ctx.scope.spOffset = address;
    IrType leftType = expr(conditional.left());
    ctx.function.newStatement(IrStatement.IrJmp.of(elseEnd));
    ctx.function.newStatement(elseStart);
    ctx.scope.spOffset = address;
    IrType rightType = expr(conditional.right());
    ctx.function.newStatement(elseEnd);

    if (!leftType.equals(rightType) && !IrType.isError(leftType) && !IrType.isError(rightType)) {
      errors.error(conditional.right().pos(), "ternary expression values have incompatible types");
      return IrType.ERROR;
    }
    return leftType;
  }

  private IrType value(Value value) {
    IrContext.Scope cur = ctx.scope;
    return switch (value.kind()) {
      case INT_LIT -> {
        ctx.function.newStatement(
            IrStatement.IrStore.of(IrAddr.relSp(cur.spOffset), value.intLit().value()));
        cur.spOffset += IrType.INT.memSize();
        yield IrType.INT;
      }
      case CHAR_LIT -> {
        ctx.function.newStatement(
            IrStatement.IrStore.of(IrAddr.relSp(cur.spOffset), value.charLit().value()));
        cur.spOffset += IrType.INT.memSize();
        yield IrType.CHAR;
      }
      case BOOL_LIT -> {
        ctx.function.newStatement(
            IrStatement.IrStore.of(IrAddr.relSp(cur.spOffset), value.boolLit().value() ? 1 : 0));
        cur.spOffset += IrType.BOOL.memSize();
        yield IrType.BOOL;
      }
      case STRING_LIT -> throw new UnsupportedOperationException("Unimplemented");
    };
  }

  private IrType functionCall(FuncCall functionCall) {
    Identifier method = functionCall.function();
    String mName = method.text();
    IrFunction func = ctx.program.functions.get(mName);
    if (func == null) {
      errors.error(method.pos(), "Func " + mName + " is undefined");
      return IrType.ERROR;
    }

    IrStatement.IrLabel retLabel = ctx.function.newLabel();
    // Reserve return value space.
    ctx.scope.spOffset += func.returnSignature.memSize();
    int retValSp = ctx.scope.spOffset;
    // Reserve return pointer space.
    if (!func.extern) {
      ctx.function.newStatement(
          IrStatement.IrStoreLabel.of(IrAddr.relSp(ctx.scope.spOffset), retLabel));
      ctx.scope.spOffset += IrType.INT.memSize();
    }
    if (functionCall.params().size() != func.paramSignature.size()) {
      errors.error(
          functionCall.pos(),
          "Wrong number of parameters to call function " + mName + " (lex Carl)");
    }
    // Push parameters.
    for (int i = 0; i < functionCall.params().size(); i++) {
      Expression param = functionCall.params().get(i);
      IrType type = expr(param);
      if (!type.equals(func.paramSignature.get(i))) {
        errors.error(param.pos(), "Mismatched parameter type");
      }
    }
    if (func.extern) {
      ctx.function.newStatement(IrStatement.IrExtern.of(mName, ctx.scope.spOffset));
    } else {
      ctx.function.newStatement(IrStatement.IrCall.of(mName, ctx.scope.spOffset, retLabel));
    }
    ctx.scope.spOffset = retValSp;
    return func.returnSignature;
  }

  private IrType reference(Identifier reference) {
    IrContext.Scope cur = ctx.scope;
    String refName = reference.text();
    Optional<IrValue> resolveOpt = cur.resolve(refName);
    if (resolveOpt.isEmpty()) {
      errors.error(reference.pos(), "Undefined variable " + refName);
      return IrType.ERROR;
    }
    IrValue ref = resolveOpt.get();
    ctx.function.newStatement(IrStatement.IrCopy.fromTo(ref.address(), IrAddr.relSp(cur.spOffset)));
    cur.spOffset += ref.type().memSize();
    return ref.type();
  }

  private IrType binary(BinaryExpr binary) {
    return switch (binary.op()) {
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> arithmetic(
          binary.op(), binary.left(), binary.right());
      case LESS_THAN, GREATER_THAN, LESS_EQUALS, GREATER_EQUALS, EQUALS, NOT_EQUALS -> comparison(
          binary.op(), binary.left(), binary.right());
      case OR, AND -> logical(binary.op(), binary.left(), binary.right());
      case ARRAY_ACCESS -> throw new UnsupportedOperationException("Unimplemented");
    };
  }

  private IrType assign(Assignment assignment) {
    Expression reference = assignment.reference();
    Expression value = assignment.value();
    IrContext.Scope cur = ctx.scope;
    if (reference.kind() != Expression.Kind.REFERENCE) {
      errors.error(reference.pos(), "LHS of assignment is not variable");
      return IrType.ERROR;
    }
    String refName = reference.reference().text();
    Optional<IrValue> refOpt = cur.resolve(refName);
    if (refOpt.isEmpty()) {
      errors.error(reference.pos(), "Undefined variable " + refName);
      return IrType.ERROR;
    }
    IrValue ref = refOpt.get();
    int sp = evalType(value, ref.type());
    if (assignment.compound().isPresent()) {
      switch (assignment.compound().get()) {
        case ADD -> ctx.function.newStatement(
            IrStatement.IrAdd.forTermsAndTarget(IrAddr.relSp(sp), ref.address(), ref.address()));
        case SUBTRACT -> ctx.function.newStatement(
            IrStatement.IrSub.forTermsAndTarget(IrAddr.relSp(sp), ref.address(), ref.address()));
        case DIVIDE -> ctx.function.newStatement(
            IrStatement.IrDiv.forTermsAndTarget(IrAddr.relSp(sp), ref.address(), ref.address()));
        case MULTIPLY -> ctx.function.newStatement(
            IrStatement.IrMul.forTermsAndTarget(IrAddr.relSp(sp), ref.address(), ref.address()));
        case MODULO -> ctx.function.newStatement(
            IrStatement.IrMod.forTermsAndTarget(IrAddr.relSp(sp), ref.address(), ref.address()));
      }
    } else {
      ctx.function.newStatement(IrStatement.IrCopy.fromTo(IrAddr.relSp(sp), ref.address()));
    }
    cur.spOffset = sp;
    return ref.type();
  }

  private IrType arithmetic(BinaryOp op, Expression left, Expression right) {
    IrContext.Scope cur = ctx.scope;
    int addr1 = evalType(left, IrType.INT);
    int addr2 = evalType(right, IrType.INT);
    switch (op) {
      case ADD -> ctx.function.newStatement(
          IrStatement.IrAdd.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
      case SUBTRACT -> ctx.function.newStatement(
          IrStatement.IrSub.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
      case MULTIPLY -> ctx.function.newStatement(
          IrStatement.IrMul.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
      case DIVIDE -> ctx.function.newStatement(
          IrStatement.IrDiv.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
      case MODULO -> ctx.function.newStatement(
          IrStatement.IrMod.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
    }
    cur.spOffset = addr1 + IrType.INT.memSize();
    return IrType.INT;
  }

  private IrType logical(BinaryOp op, Expression left, Expression right) {
    IrContext.Scope cur = ctx.scope;
    int origOffset = cur.spOffset;
    IrStatement.IrLabel shortcircuit = ctx.function.newLabel();
    int addr1 = evalType(left, IrType.BOOL);
    if (op == BinaryOp.OR) {
      ctx.function.newStatement(IrStatement.IrJmpNZero.of(shortcircuit, IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.AND) {
      ctx.function.newStatement(IrStatement.IrJmpZero.of(shortcircuit, IrAddr.relSp(addr1)));
    } else {
      throw new AssertionError("Unexpected logical op?");
    }
    cur.spOffset = origOffset;
    addr1 = evalType(right, IrType.BOOL);
    ctx.function.newStatement(shortcircuit);
    cur.spOffset = addr1 + IrType.BOOL.memSize();
    return IrType.BOOL;
  }

  private IrType comparison(BinaryOp op, Expression left, Expression right) {
    IrContext.Scope cur = ctx.scope;
    int addr1 = evalType(left, IrType.INT);
    int addr2 = evalType(right, IrType.INT);
    if (op == BinaryOp.LESS_THAN) {
      ctx.function.newStatement(
          IrStatement.IrLessThan.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.GREATER_THAN) {
      ctx.function.newStatement(
          IrStatement.IrLessThan.forTermsAndTarget(
              IrAddr.relSp(addr2), IrAddr.relSp(addr1), IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.LESS_EQUALS) {
      ctx.function.newStatement(
          IrStatement.IrLessEquals.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.GREATER_EQUALS) {
      ctx.function.newStatement(
          IrStatement.IrLessEquals.forTermsAndTarget(
              IrAddr.relSp(addr2), IrAddr.relSp(addr1), IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.EQUALS) {
      ctx.function.newStatement(
          IrStatement.IrEquals.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
    } else if (op == BinaryOp.NOT_EQUALS) {
      ctx.function.newStatement(
          IrStatement.IrNotEquals.forTermsAndTarget(
              IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
    } else {
      throw new AssertionError("Unexpected arithmetic op?");
    }
    cur.spOffset = addr1 + IrType.BOOL.memSize();
    return IrType.BOOL;
  }

  private int evalType(Expression e, IrType expected) {
    int sp = ctx.scope.spOffset;
    IrType type = expr(e);
    if (!type.equals(expected) && !type.equals(IrType.ERROR)) {
      errors.error(e.pos(), "Expression has incorrect type " + type + ", expected " + expected);
    }
    return sp;
  }

  private static class IrContext {
    private final IrProgram program;
    private IrFunction function;
    private Scope scope;

    IrContext(IrProgram program) {
      this.program = program;
    }

    public Scope newScope() {
      return scope = new Scope(scope);
    }

    public void popScope() {
      scope = scope.parent;
    }

    static class Scope {
      private final HashMap<String, IrValue> vals = new HashMap<>();
      private final Scope parent;
      int spOffset;

      public Scope(Scope parent) {
        this.parent = parent;
        this.spOffset = parent == null ? 0 : parent.spOffset;
      }

      public Optional<IrValue> resolve(String name) {
        if (vals.containsKey(name)) {
          return Optional.of(vals.get(name));
        }
        if (parent != null) {
          return parent.resolve(name);
        }
        return Optional.empty();
      }

      public void addVal(String pName, IrValue var) {
        vals.put(pName, var);
      }
    }
  }
}
