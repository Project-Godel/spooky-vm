package se.jsannemo.spooky.compiler.ir;

import com.google.common.base.Preconditions;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public final class ToIr {

    private ToIr() {}

    /** Generates an IR representation of a program. */
    public static IrProgram generate(Ast.Program p) throws ValidationException {
        IrContext ctx = new IrContext(new IrProgram());
        // Create root scope; will be used for e.g. global variables in the future.
        ctx.newScope();
        for (Ast.Func func : p.getFunctionsList()) {
            funcDecl(func.getDecl(), false, ctx);
        }
        for (Ast.FuncDecl func : p.getExternsList()) {
            funcDecl(func, true, ctx);
        }
        initDecl(p.getGlobalsList(), ctx);
        for (Ast.Func func : p.getFunctionsList()) {
            function(func, ctx);
        }
        ctx.popScope();
        Preconditions.checkState(ctx.scope == null);
        return ctx.program;
    }

    private static void initDecl(List<Ast.VarDecl> globals, IrContext ctx) throws ValidationException {
        // Create special __init__ function which will run before main.
        IrFunction function = new IrFunction();
        function.returnSignature = IrTypes.VOID;
        ctx.program.functions.put("__init__", function);


        // Allocate stack space and create init code for all globals.
        // Falling through this function is okay, since main start right after.
        ctx.function = function;
        for (Ast.VarDecl global : globals) {
          varDecl(global, ctx, true);
        }
        ctx.function = null;
  }

    private static void funcDecl(Ast.FuncDecl declaration, boolean extern, IrContext ctx) throws ValidationException {
        IrFunction function = new IrFunction();
        function.returnSignature = declaration.hasReturnType() ? IrTypes.fromAst(declaration.getReturnType()) : IrTypes.VOID;
        function.extern = extern;
        ctx.program.functions.put(declaration.getName().getName(), function);

        for (Ast.FuncParam p : declaration.getParamsList()) {
            function.paramSignature.add(IrTypes.fromAst(p.getType()));
        }
    }

    private static void function(Ast.Func value, IrContext ctx) throws ValidationException {
        String funcName = value.getDecl().getName().getName();
        ctx.function = ctx.program.functions.get(funcName);
        Preconditions.checkArgument(ctx.function != null, "Function has not been declared");

        // Root scope for function; only includes the parameters.
        IrContext.Scope cur = ctx.newScope();
        int offset = 0;
        List<Ast.FuncParam> paramsList = value.getDecl().getParamsList();
        for (int i = paramsList.size() - 1; i >= 0; i--) {
            Ast.FuncParam param = paramsList.get(i);
            String pName = param.getName().getName();
            if (cur.vals.containsKey(pName)) {
                throw new ValidationException("Parameter " + pName + " already defined.", param.getPosition());
            }
            Ir.Type type = IrTypes.fromAst(param.getType());
            offset -= IrTypes.memSize(type);
            Ir.Value var = Ir.Value.newBuilder().setType(type).setAddress(IrAddrs.relSp(offset)).build();
            cur.addVal(pName, var);
        }
        offset -= IrTypes.memSize(IrTypes.INT);
        ctx.function.retAddress = offset;
        offset -= IrTypes.memSize(ctx.function.returnSignature);
        ctx.function.retValue = offset;

        ArrayList<Ast.Statement> body = new ArrayList<>(value.getBody().getBlock().getBodyList());
        // Main is special; automatically halts after execution rather than returning.
        if (funcName.equals("main")) {
          ctx.function.isMain = true;
          if (!ctx.function.returnSignature.equals(IrTypes.VOID)) {
              throw new ValidationException("Main function has non-void return type");
          }
        }
        // TODO: yikes!
        if (body.isEmpty() || !body.get(body.size() - 1).hasReturnValue()) {
          if (ctx.function.returnSignature.equals(IrTypes.VOID)) {
              body.add(Ast.Statement.newBuilder().setReturnValue(Ast.ReturnValue.newBuilder().build()).build());
          } else {
            throw new ValidationException("Non-void function reached end of function without return");
          }
        }
        statementList(body, ctx);

        ctx.popScope();
        ctx.function = null;
    }

    private static void statementList(List<Ast.Statement> body, IrContext ctx) throws ValidationException {
        ctx.newScope();
        for (Ast.Statement stmt : body) {
            statement(stmt, ctx);
        }
        ctx.popScope();
    }

    private static void statement(Ast.Statement stmt, IrContext ctx) throws ValidationException {
        switch (stmt.getStatementCase()) {
            case DECL:
                varDecl(stmt.getDecl(), ctx, false);
                break;
            case LOOP:
                loop(stmt.getLoop(), ctx);
                break;
            case CONDITIONAL:
                conditional(stmt.getConditional(), ctx);
                break;
            case EXPRESSION:
                // Make sure we don't leak stack after an expression statement
                IrContext.Scope cur = ctx.scope;
                int sp = cur.spOffset;
                expr(stmt.getExpression(), ctx);
                cur.spOffset = sp;
                break;
            case RETURNVALUE:
                returns(stmt.getReturnValue(), ctx);
                break;
            case BLOCK:
                statementList(stmt.getBlock().getBodyList(), ctx);
                break;
            default:
                throw new IllegalArgumentException("Invalid statement " + stmt.getStatementCase());
        }
    }

    private static void varDecl(Ast.VarDecl varDecl, IrContext ctx, boolean isGlobal) throws ValidationException {
        String varName = varDecl.getName().getName();
        IrContext.Scope cur = ctx.scope;
        Optional<Ir.Value> res = cur.resolve(varName);
        if (res.isPresent()) {
            throw new ValidationException("Variable " + varName + " already declared.", varDecl.getPosition());
        }

        Ir.Type type = IrTypes.fromAst(varDecl.getType());
        // Globals are not relative current stack-pointer, but start of stack.
        Ir.Addr varAddr = isGlobal ? IrAddrs.absStack(cur.spOffset + Conventions.NEXT_STACK.getAbs()) : IrAddrs.relSp(cur.spOffset);
        Ir.Value var = Ir.Value.newBuilder().setType(type).setAddress(varAddr).build();
        cur.addVal(varName, var);
        Ir.Type valueType = expr(varDecl.getInit(), ctx);
        // TODO
        if (!type.equals(valueType)) {
            throw new ValidationException("Invalid type conversion: " + valueType + " to " + type, varDecl.getInit().getPosition());
        }
        // Note: no SP movement here, since expr() will already have reserved memory for the variable.
    }

    private static void returns(Ast.ReturnValue ret, IrContext ctx) throws ValidationException {
        boolean wantValue = !ctx.function.returnSignature.equals(IrTypes.VOID);
        if (wantValue != ret.hasValue()) {
            throw new ValidationException("Return value has the wrong type");
        }
        int pos = ctx.scope.spOffset;
        if (ret.hasValue()) {
            Ir.Type type = expr(ret.getValue(), ctx);
            if (!type.equals(ctx.function.returnSignature)) {
                throw new ValidationException("Return value has the wrong type");
            }
        }
        if (ctx.function.isMain) {
            ctx.function.newStatement().setHalt(Ir.Halt.getDefaultInstance());
            return;
        }
        if (ret.hasValue()) {
            ctx.function.newStatement().setCopy(Ir.Copy.newBuilder()
                            .setFrom(IrAddrs.relSp(pos)).setTo(IrAddrs.relSp(ctx.function.retValue)));
        }
        ctx.function.newStatement()
                .getJmpAddrBuilder().setAddr(IrAddrs.relSp(ctx.function.retAddress));
    }

    private static void loop(Ast.Loop loop, IrContext ctx) throws ValidationException {
        ctx.newScope();
        Ir.Label check = ctx.function.newLabel();
        Ir.Label end = ctx.function.newLabel();

        // Init
        if (loop.hasInit()) {
            statement(loop.getInit(), ctx);
        }

        // Check
        ctx.function.newStatement().setLabel(check);
        int condAddr = evalType(loop.getCondition(), IrTypes.BOOL, ctx);
        ctx.function.newStatement()
                .getJmpZeroBuilder()
                .setLabel(end)
                .setFlag(IrAddrs.relSp(condAddr));
        ctx.scope.spOffset = condAddr;

        // Iteration
        statement(loop.getBody(), ctx);
        if (loop.hasIncrement()) {
            statement(loop.getIncrement(), ctx);
        }
        ctx.function.newStatement().getJmpBuilder().setLabel(check);

        // Post
        ctx.function.newStatement().setLabel(end);
        ctx.popScope();
    }

    private static void conditional(Ast.Conditional conditional, IrContext ctx) throws ValidationException {
        Ir.Addr condAddr = IrAddrs.relSp(ctx.scope.spOffset);
        Ir.Type cond = expr(conditional.getCondition(), ctx);
        if (!cond.equals(IrTypes.BOOL)) {
            throw new ValidationException("If-statement condition is not a boolean.", conditional.getPosition());
        }
        Ir.Label elseStart = ctx.function.newLabel();
        Ir.Label elseEnd = ctx.function.newLabel();

        ctx.function.newStatement().getJmpZeroBuilder()
                .setFlag(condAddr)
                .setLabel(elseStart);
        statement(conditional.getBody(), ctx);
        if (conditional.hasElseBody()) {
            ctx.function.newStatement().getJmpBuilder().setLabel(elseEnd);
        }
        ctx.function.newStatement().setLabel(elseStart);
        if (conditional.hasElseBody()) {
            statement(conditional.getElseBody(), ctx);
            ctx.function.newStatement().setLabel(elseEnd);
        }
    }

    private static Ir.Type expr(Ast.Expr expression, IrContext ctx) throws ValidationException {
        switch (expression.getExprCase()) {
            case VALUE:
                return value(expression.getValue(), ctx);
            case BINARY:
                return binary(expression.getBinary(), ctx);
            case REFERENCE:
                return reference(expression.getReference(), ctx);
            case CALL:
                return functionCall(expression.getCall(), ctx);
            case ASSIGNMENT:
                return assign(expression.getAssignment(), ctx);
            case CONDITIONAL:
                throw new IllegalArgumentException("Ternary conditions are not implemented");
            case UNARY:
                throw new IllegalArgumentException("Unary operations are not implemented");
            default:
                throw new IllegalArgumentException();
        }
    }

    private static Ir.Type value(Ast.Value value, IrContext ctx) {
        IrContext.Scope cur = ctx.scope;
        switch (value.getLiteralCase()) {
            case CHAR_LITERAL:
                ctx.function.newStatement().getStoreBuilder()
                        .setAddr(IrAddrs.relSp(cur.spOffset))
                        .setValue(value.getCharLiteral());
                cur.spOffset += IrTypes.memSize(IrTypes.CHAR);
                return IrTypes.CHAR;
            case BOOL_LITERAL:
                ctx.function.newStatement().getStoreBuilder()
                        .setAddr(IrAddrs.relSp(cur.spOffset))
                        .setValue(value.getBoolLiteral() ? 1 : 0);
                cur.spOffset += IrTypes.memSize(IrTypes.BOOL);
                return IrTypes.BOOL;
            case INT_LITERAL:
                ctx.function.newStatement().getStoreBuilder()
                        .setAddr(IrAddrs.relSp(cur.spOffset))
                        .setValue(value.getIntLiteral());
                cur.spOffset += IrTypes.memSize(IrTypes.INT);
                return IrTypes.INT;
            default:
                throw new IllegalArgumentException("Unsupported literal type");
        }
    }

    private static Ir.Type functionCall(Ast.FuncCall functionCall, IrContext ctx) throws ValidationException {
        Ast.Identifier method = functionCall.getFunction();
        String mName = method.getName();
        IrFunction func = ctx.program.functions.get(mName);
        if (func == null) {
            throw new ValidationException("Function " + mName + " is undefined");
        }

        Ir.Label retLabel = ctx.function.newLabel();
        // Reserve return value space.
        ctx.scope.spOffset += IrTypes.memSize(func.returnSignature);
        int retValSp = ctx.scope.spOffset;
        // Reserve return pointer space.
        if (!func.extern) {
            ctx.function.newStatement().getStoreLabelBuilder()
                    .setAddr(IrAddrs.relSp(ctx.scope.spOffset))
                    .setLabel(retLabel);
            ctx.scope.spOffset += IrTypes.memSize(IrTypes.INT);
        }
        if (functionCall.getParamsCount() != func.paramSignature.size()) {
            throw new ValidationException("Wrong number of parameters to call function " + mName + " (lex Carl)");
        }
        // Push parameters.
        for (int i = 0; i < functionCall.getParamsCount(); i++) {
            Ir.Type type = expr(functionCall.getParams(i), ctx);
            if (!IrTypes.typeChecks(type, func.paramSignature.get(i))) {
                throw new ValidationException("Mismatched parameter type on call to " + mName);
            }
        }
        if (func.extern) {
            ctx.function.newStatement().getExternBuilder().setName(mName).setSpOffset(ctx.scope.spOffset);
        } else {
            ctx.function.newStatement().getCallBuilder().setName(mName).setSpOffset(ctx.scope.spOffset).setLabel(retLabel);
        }
        ctx.scope.spOffset = retValSp;
        return func.returnSignature;
    }

    private static Ir.Type reference(Ast.Identifier reference, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        String refName = reference.getName();
        Ir.Value ref = cur.resolve(refName).orElseThrow(() -> new ValidationException("Undefined variable " + reference.getName(), reference.getPosition()));
        ctx.function.newStatement().getCopyBuilder().setFrom(ref.getAddress()).setTo(IrAddrs.relSp(cur.spOffset));
        cur.spOffset += IrTypes.memSize(ref.getType());
        return ref.getType();
    }

    private static Ir.Type binary(Ast.BinaryExpr binary, IrContext ctx) throws ValidationException {
        switch (binary.getOperator()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
                return arithmetic(binary.getOperator(), binary.getLeft(), binary.getRight(), ctx);
            case LESS_THAN:
            case GREATER_THAN:
            case LESS_EQUALS:
            case GREATER_EQUALS:
            case EQUALS:
            case NOT_EQUALS:
                return comparison(binary.getOperator(), binary.getLeft(), binary.getRight(), ctx);
            case OR:
            case AND:
                return logical(binary.getOperator(), binary.getLeft(), binary.getRight(), ctx);
            case ARRAY_ACCESS:
                throw new UnsupportedOperationException("Unimplemented");
            default:
                throw new IllegalArgumentException();
        }
    }

    private static Ir.Type assign(Ast.Assignment assign, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        String refName = assign.getVariable().getName();
        Ir.Value ref = cur.resolve(refName).orElseThrow(() ->
            new ValidationException("Variable " + refName + " does not exist", assign.getPosition()));
        int sp = evalType(assign.getValue(), ref.getType(), ctx);
        ctx.function.newStatement().getCopyBuilder().setFrom(IrAddrs.relSp(sp)).setTo(ref.getAddress());
        cur.spOffset = sp;
        return ref.getType();
    }

    private static Ir.Type arithmetic(Ast.BinaryOp op, Ast.Expr left, Ast.Expr right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        int addr1 = evalType(left, IrTypes.INT, ctx);
        int addr2 = evalType(right, IrTypes.INT, ctx);
        if (op == Ast.BinaryOp.ADD) {
            ctx.function.newStatement().getAddBuilder().setA(IrAddrs.relSp(addr1)).setB(IrAddrs.relSp(addr2)).setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.SUBTRACT) {
            ctx.function.newStatement().getSubBuilder().setA(IrAddrs.relSp(addr1)).setB(IrAddrs.relSp(addr2)).setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.MULTIPLY) {
            ctx.function.newStatement().getMulBuilder().setA(IrAddrs.relSp(addr1)).setB(IrAddrs.relSp(addr2)).setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.DIVIDE) {
            ctx.function.newStatement().getDivBuilder().setA(IrAddrs.relSp(addr1)).setB(IrAddrs.relSp(addr2)).setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.MODULO) {
            ctx.function.newStatement().getModBuilder().setA(IrAddrs.relSp(addr1)).setB(IrAddrs.relSp(addr2)).setResult(IrAddrs.relSp(addr1));
        } else {
            throw new AssertionError("Unexpected arithmetic op?");
        }
        cur.spOffset = addr1 + IrTypes.memSize(IrTypes.INT);
        return IrTypes.INT;
    }

    private static Ir.Type logical(Ast.BinaryOp op, Ast.Expr left, Ast.Expr right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        int origOffset = cur.spOffset;
        Ir.Label shortcircuit = ctx.function.newLabel();
        int addr1 = evalType(left, IrTypes.BOOL, ctx);
        if (op == Ast.BinaryOp.OR) {
            ctx.function.newStatement().getJmpNzeroBuilder().setLabel(shortcircuit).setFlag(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.AND) {
            ctx.function.newStatement().getJmpZeroBuilder().setLabel(shortcircuit).setFlag(IrAddrs.relSp(addr1));
        } else {
            throw new AssertionError("Unexpected logical op?");
        }
        cur.spOffset = origOffset;
        addr1 = evalType(right, IrTypes.BOOL, ctx);
        ctx.function.newStatement().setLabel(shortcircuit);
        cur.spOffset = addr1 + IrTypes.memSize(IrTypes.BOOL);
        return IrTypes.BOOL;
    }

    private static Ir.Type comparison(Ast.BinaryOp op, Ast.Expr left, Ast.Expr right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        int addr1 = evalType(left, IrTypes.INT, ctx);
        int addr2 = evalType(right, IrTypes.INT, ctx);
        if (op == Ast.BinaryOp.LESS_THAN) {
            ctx.function.newStatement().getLessThanBuilder()
                    .setA(IrAddrs.relSp(addr1))
                    .setB(IrAddrs.relSp(addr2))
                    .setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.GREATER_THAN) {
            ctx.function.newStatement().getLessThanBuilder()
                    .setA(IrAddrs.relSp(addr2))
                    .setB(IrAddrs.relSp(addr1))
                    .setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.LESS_EQUALS) {
            ctx.function.newStatement().getLessEqualsBuilder()
                    .setA(IrAddrs.relSp(addr1))
                    .setB(IrAddrs.relSp(addr2))
                    .setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.GREATER_EQUALS) {
            ctx.function.newStatement().getLessEqualsBuilder()
                    .setA(IrAddrs.relSp(addr2))
                    .setB(IrAddrs.relSp(addr1))
                    .setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.EQUALS) {
            ctx.function.newStatement().getEqualsBuilder()
                    .setA(IrAddrs.relSp(addr2))
                    .setB(IrAddrs.relSp(addr1))
                    .setResult(IrAddrs.relSp(addr1));
        } else if (op == Ast.BinaryOp.NOT_EQUALS) {
            ctx.function.newStatement().getNotEqualsBuilder()
                    .setA(IrAddrs.relSp(addr2))
                    .setB(IrAddrs.relSp(addr1))
                    .setResult(IrAddrs.relSp(addr1));
        } else {
            throw new AssertionError("Unexpected arithmetic op?");
        }
        cur.spOffset = addr1 + IrTypes.memSize(IrTypes.BOOL);
        return IrTypes.BOOL;
    }

    private static int evalType(Ast.Expr e, Ir.Type expected, IrContext ctx) throws ValidationException {
        int sp = ctx.scope.spOffset;
        Ir.Type type = expr(e, ctx);
        if (!type.equals(expected)) {
            throw new ValidationException("Expression has incorrect type " + type + ", expected " + expected, e.getPosition());
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
            private final HashMap<String, Ir.Value> vals = new HashMap<>();
            private final Scope parent;
            int spOffset;

            public Scope(Scope parent) {
                this.parent = parent;
                this.spOffset = parent == null ? 0 : parent.spOffset;
            }

            public Optional<Ir.Value> resolve(String name) {
                if (vals.containsKey(name)) {
                    return Optional.of(vals.get(name));
                }
                if (parent != null) {
                    return parent.resolve(name);
                }
                return Optional.empty();
            }

            public void addVal(String pName, Ir.Value var) {
                vals.put(pName, var);
            }
        }
    }

}

