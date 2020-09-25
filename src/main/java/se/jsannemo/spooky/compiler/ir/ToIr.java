package se.jsannemo.spooky.compiler.ir;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import se.jsannemo.spooky.compiler.Token;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.compiler.ast.BinaryExpression;
import se.jsannemo.spooky.compiler.ast.BinaryOperator;
import se.jsannemo.spooky.compiler.ast.Conditional;
import se.jsannemo.spooky.compiler.ast.Expression;
import se.jsannemo.spooky.compiler.ast.Function;
import se.jsannemo.spooky.compiler.ast.FunctionCall;
import se.jsannemo.spooky.compiler.ast.FunctionDecl;
import se.jsannemo.spooky.compiler.ast.FunctionParam;
import se.jsannemo.spooky.compiler.ast.Identifier;
import se.jsannemo.spooky.compiler.ast.Loop;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.ast.Statement;
import se.jsannemo.spooky.compiler.ast.Statement.StatementKind;
import se.jsannemo.spooky.compiler.ast.StatementList;
import se.jsannemo.spooky.compiler.ast.VarDecl;
import se.jsannemo.spooky.compiler.codegen.Conventions;
import se.jsannemo.spooky.compiler.ir.IrStatement.IrHalt;

public final class ToIr {

    private ToIr() {}

    /** Generates an IR representation of a program. */
    public static IrProgram generate(Program p) throws ValidationException {
        IrContext ctx = new IrContext(new IrProgram());
        // Create root scope; will be used for e.g. global variables in the future.
        ctx.newScope();
        for (Function func : p.functions()) {
            funcDecl(func.declaration(), false, ctx);
        }
        for (FunctionDecl func : p.externs()) {
            funcDecl(func, true, ctx);
        }
        for (Function func : p.functions()) {
            function(func, ctx);
        }
        ctx.popScope();
        Preconditions.checkState(ctx.scope == null);
        return ctx.program;
    }

    private static void funcDecl(FunctionDecl declaration, boolean extern, IrContext ctx) throws ValidationException {
        IrFunction function = new IrFunction();
        function.returnSignature = IrType.fromTypeName(declaration.returnType());
        function.extern = extern;
        ctx.program.functions.put(declaration.name().name(), function);

        for (FunctionParam p : declaration.params()) {
            function.paramSignature.add(IrType.fromTypeName(p.type()));
        }
    }

    private static void function(Function value, IrContext ctx) throws ValidationException {
        String funcName = value.declaration().name().name();
        ctx.function = ctx.program.functions.get(funcName);
        Preconditions.checkArgument(ctx.function != null, "Function has not been declared");

        // Root scope for function; only includes the parameters.
        IrContext.Scope cur = ctx.newScope();
        int offset = 0; // Offset -1 is reserved for return address
        for (FunctionParam param : value.declaration().params()) {
            String pName = param.name().name();
            if (cur.vals.containsKey(pName)) {
                throw new ValidationException("Parameter " + pName + " already defined.", param.name().token());
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

        StatementList body = value.body();
        ArrayList<Statement> stmt = new ArrayList<>(body.statements());
        // Main is special; automatically halts after execution rather than returning.
        if (funcName.equals("main")) {
          ctx.function.isMain = true;
          if (!ctx.function.returnSignature.equals(IrType.VOID)) {
              throw new ValidationException("Main function has non-void return type");
          }
        }
        Statement last = stmt.get(stmt.size() - 1);
        if (last.kind() != StatementKind.RETURNS) {
          if (ctx.function.returnSignature.equals(IrType.VOID)) {
              stmt.add(Statement.returns(Optional.empty()));
          } else {
            throw new ValidationException("Non-void function reached end of function without return");
          }
        }
        statementList(stmt, ctx);


        ctx.popScope();
        ctx.function = null;
    }

    private static void statementList(List<Statement> body, IrContext ctx) throws ValidationException {
        ctx.newScope();
        for (Statement stmt : body) {
            statement(stmt, ctx);
        }
        ctx.popScope();
    }

    private static void statement(Statement stmt, IrContext ctx) throws ValidationException {
        switch (stmt.kind()) {
            case VAR_DECL -> varDecl(stmt.varDecl(), ctx);
            case LOOP -> loop(stmt.loop(), ctx);
            case CONDITIONAL -> conditional(stmt.conditional(), ctx);
            case EXPRESSION -> {
                // Make sure we don't leak stack after an expression statement
                IrContext.Scope cur = ctx.scope;
                int sp = cur.spOffset;
                expr(stmt.expression(), ctx);
                cur.spOffset = sp;
            }
            case RETURNS -> returns(stmt.returns(), ctx);
            case HALT -> ctx.function.newStatement(IrStatement.IrHalt.of());
        }
    }

    private static void varDecl(VarDecl varDecl, IrContext ctx) throws ValidationException {
        Token declToken = varDecl.name().token();
        String varName = varDecl.name().name();
        IrContext.Scope cur = ctx.scope;
        Optional<IrValue> res = cur.resolve(varName);
        if (res.isPresent()) {
            throw new ValidationException("Variable " + varName + " already declared.", declToken);
        }

        IrType type = IrType.fromTypeName(varDecl.type());
        IrValue var = IrValue.ofTypeAndAddress(type, IrAddr.relSp(cur.spOffset));
        cur.addVal(varName, var);
        IrType valueType = expr(varDecl.value(), ctx);
        if (!type.equals(valueType)) {
            throw new ValidationException("Invalid type conversion: " + valueType + " to " + type, declToken);
        }
        // Note: no SP movement here, since expr() will already have reserved memory for the variable.
    }

    private static void returns(Optional<Expression> ret, IrContext ctx) throws ValidationException {
      if (ctx.function.isMain) {
          ctx.function.newStatement(IrHalt.of());
          return;
      }
        int pos = ctx.scope.spOffset;
        if (ret.isPresent()) {
            IrType type = expr(ret.get(), ctx);
            if (!type.equals(ctx.function.returnSignature)) {
                throw new ValidationException("Return value has the wrong type");
            }
        } else if (!ctx.function.returnSignature.equals(IrType.VOID)) {
            throw new ValidationException("Return value has the wrong type");
        }
        ctx.function.newStatement(IrStatement.IrCopy.fromTo(IrAddr.relSp(pos), IrAddr.relSp(ctx.function.retValue)));
        ctx.function.newStatement(IrStatement.IrJmpAdr.of(IrAddr.relSp(ctx.function.retAddress)));
    }

    private static void loop(Loop loop, IrContext ctx) throws ValidationException {
        ctx.newScope();
        IrStatement.IrLabel check = ctx.function.newLabel();
        IrStatement.IrLabel end = ctx.function.newLabel();

        // Init
        statement(loop.initialize(), ctx);

        // Check
        ctx.function.newStatement(check);
        int condAddr = evalType(loop.condition(), IrType.INT, ctx);
        ctx.function.newStatement(IrStatement.IrJmpZero.of(end, IrAddr.relSp(condAddr)));
        ctx.scope.spOffset = condAddr;

        // Iteration
        statementList(loop.body().statements(), ctx);
        statement(loop.increment(), ctx);
        ctx.function.newStatement(IrStatement.IrJmpZero.of(check, Conventions.CONST_ZERO));

        // Post
        ctx.function.newStatement(end);
        ctx.popScope();
    }

    private static void conditional(Conditional conditional, IrContext ctx) throws ValidationException {
        IrAddr condAddr = IrAddr.relSp(ctx.scope.spOffset);
        IrType cond = expr(conditional.condition(), ctx);
        if (!cond.equals(IrType.BOOL)) {
            throw new ValidationException("If-statement condition is not a boolean.", conditional.token());
        }
        IrStatement.IrLabel label = ctx.function.newLabel();
        ctx.function.newStatement(IrStatement.IrJmpZero.of(label, condAddr));
        statementList(conditional.body().statements(), ctx);
        ctx.function.newStatement(label);
    }

    private static IrType expr(Expression expression, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        return switch (expression.kind()) {
            case INT_LITERAL -> {
                ctx.function.newStatement(IrStatement.IrStore.of(IrAddr.relSp(cur.spOffset), expression.intLiteral()));
                cur.spOffset += IrType.INT.memSize();
                yield IrType.INT;
            }
            case BINARY -> binary(expression.binary(), ctx);
            case REFERENCE -> reference(expression.reference(), ctx);
            case FUNCTION_CALL -> functionCall(expression.functionCall(), ctx);
            case STRING_LITERAL -> throw new UnsupportedOperationException("Unimplemented");
        };
    }

    private static IrType functionCall(FunctionCall functionCall, IrContext ctx) throws ValidationException {
        Expression method = functionCall.function();
        if (method.kind() != Expression.ExpressionKind.REFERENCE) {
            throw new ValidationException("Expression result not callable");
        }
        String mName = method.reference().name();
        IrFunction func = ctx.program.functions.get(mName);
        if (func == null) {
            throw new ValidationException("Function " + mName + " is undefined");
        }

        IrStatement.IrLabel retLabel = ctx.function.newLabel();
        // Reserve return value space.
        ctx.scope.spOffset += func.returnSignature.memSize();
        int retValSp = ctx.scope.spOffset;
        // Reserve return pointer space.
        if (!func.extern) {
            ctx.function.newStatement(IrStatement.IrStoreLabel.of(IrAddr.relSp(ctx.scope.spOffset), retLabel));
            ctx.scope.spOffset += IrType.INT.memSize();
        }
        if (functionCall.params().size() != func.paramSignature.size()) {
            throw new ValidationException("Wrong number of parameters to call function " + mName + " (lex Carl)");
        }
        // Push parameters.
        for (int i = 0; i < functionCall.params().size(); i++) {
            IrType type = expr(functionCall.params().get(i), ctx);
            if (!type.equals(func.paramSignature.get(i))) {
                throw new ValidationException("Mismatched parameter type on call to " + mName);
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

    private static IrType reference(Identifier reference, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        String refName = reference.name();
        IrValue ref = cur.resolve(refName).orElseThrow(() -> new ValidationException("Undefined variable " + reference.name(), reference.token()));
        ctx.function.newStatement(IrStatement.IrCopy.fromTo(ref.address(), IrAddr.relSp(cur.spOffset)));
        cur.spOffset += ref.type().memSize();
        return ref.type();
    }

    private static IrType binary(BinaryExpression binary, IrContext ctx) throws ValidationException {
        return switch (binary.operator()) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> arithmetic(binary.operator(), binary.left(), binary.right(), ctx);
            case LESS_THAN, GREATER_THAN, LESS_EQUALS, GREATER_EQUALS, EQUALS -> comparison(binary.operator(), binary.left(), binary.right(), ctx);
            case ASSIGN -> assign(binary.left(), binary.right(), ctx);
            case ARRAY_ACCESS -> throw new UnsupportedOperationException("Unimplemented");
        };
    }

    private static IrType assign(Expression left, Expression right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        if (left.kind() != Expression.ExpressionKind.REFERENCE) {
            throw new ValidationException("LHS of assignment is not variable");
        }
        String refName = left.reference().name();
        IrValue ref = cur.resolve(refName).orElseThrow(() ->
            new ValidationException("Variable " + refName + " does not exist", left.reference().token()));
        int sp = evalType(right, ref.type(), ctx);
        ctx.function.newStatement(IrStatement.IrCopy.fromTo(IrAddr.relSp(sp), ref.address()));
        cur.spOffset = sp;
        return ref.type();
    }

    private static IrType arithmetic(BinaryOperator op, Expression left, Expression right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        int addr1 = evalType(left, IrType.INT, ctx);
        int addr2 = evalType(right, IrType.INT, ctx);
        if (op == BinaryOperator.ADD) {
            ctx.function.newStatement(IrStatement.IrAdd.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.SUBTRACT) {
            ctx.function.newStatement(IrStatement.IrSub.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.MULTIPLY) {
            ctx.function.newStatement(IrStatement.IrMul.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.DIVIDE) {
            ctx.function.newStatement(IrStatement.IrDiv.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.MODULO) {
            ctx.function.newStatement(IrStatement.IrMod.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else {
            throw new AssertionError("Unexpected arithmetic op?");
        }
        cur.spOffset = addr1 + IrType.INT.memSize();
        return IrType.INT;
    }

    private static IrType comparison(BinaryOperator op, Expression left, Expression right, IrContext ctx) throws ValidationException {
        IrContext.Scope cur = ctx.scope;
        int addr1 = evalType(left, IrType.INT, ctx);
        int addr2 = evalType(right, IrType.INT, ctx);
        if (op == BinaryOperator.LESS_THAN) {
            ctx.function.newStatement(IrStatement.IrLessThan.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.GREATER_THAN) {
            ctx.function.newStatement(IrStatement.IrLessThan.forTermsAndTarget(IrAddr.relSp(addr2), IrAddr.relSp(addr1), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.LESS_EQUALS) {
            ctx.function.newStatement(IrStatement.IrLessEquals.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.GREATER_EQUALS) {
            ctx.function.newStatement(IrStatement.IrLessEquals.forTermsAndTarget(IrAddr.relSp(addr2), IrAddr.relSp(addr1), IrAddr.relSp(addr1)));
        } else if (op == BinaryOperator.EQUALS) {
            ctx.function.newStatement(IrStatement.IrEquals.forTermsAndTarget(IrAddr.relSp(addr1), IrAddr.relSp(addr2), IrAddr.relSp(addr1)));
        } else {
            throw new AssertionError("Unexpected arithmetic op?");
        }
        cur.spOffset = addr1 + IrType.BOOL.memSize();
        return IrType.BOOL;
    }

    private static int evalType(Expression e, IrType expected, IrContext ctx) throws ValidationException {
        int sp = ctx.scope.spOffset;
        IrType type = expr(e, ctx);
        if (!type.equals(expected)) {
            throw new ValidationException("Expression has incorrect type " + type + ", expected " + expected);
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

