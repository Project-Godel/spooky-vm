package se.jsannemo.spooky.compiler.typecheck;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.Prog;
import se.jsannemo.spooky.compiler.ast.Ast;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/** A type-checker for Spooky {@link se.jsannemo.spooky.compiler.ast.Ast.Program}s. */
public final class TypeChecker {

  private final boolean permissive;
  private final Errors err;

  private final HashMap<String, FuncData> externs = new HashMap<>();
  final HashMap<String, StructData> structs = new HashMap<>();
  final HashMap<Integer, String> structNames = new HashMap<>();
  // Functions do not need any extra information regarding typechecking, so we only keep the index
  // of the function in the programBuilder.functions list.
  private final HashMap<String, Integer> funcIdx = new HashMap<>();
  private final Prog.Program.Builder programBuilder = Prog.Program.newBuilder();
  private Scope scope = Scope.root();

  /**
   * Initializes a new type-checker.
   *
   * @param permissive whether the type checker should do a best-effort type check on a broken AST.
   *     Otherwise, an {@link IllegalArgumentException} will be thrown for syntactically invalid
   *     syntax trees.
   */
  private TypeChecker(boolean permissive, Errors err) {
    this.permissive = permissive;
    this.err = err;
  }

  private Prog.Program checkProgram(Ast.Program program) {
    // Structs must be registered first, since function parameters and return types may refer to
    // them.
    registerStructs(programBuilder, program.getStructsList());

    // Next, functions must be registered first, since global variables may reference them during
    // initialization.
    program.getExternsList().forEach(this::registerExtern);
    registerFunctions(programBuilder, program.getFunctionsList());

    // Next, globals must be registered since function expressions can reference them.
    createGlobalInit(programBuilder, program.getGlobalsList());

    // Only after
    checkFunctions(programBuilder, program.getFunctionsList());
    return programBuilder.build();
  }

  private void registerStructs(
      Prog.Program.Builder programBuilder, List<Ast.StructDecl> structsList) {
    ImmutableList.Builder<Ast.StructDecl> dedupBuilder = ImmutableList.builder();
    // First register all structs to be able to find them when checking fields.
    for (Ast.StructDecl struct : structsList) {
      if (syntaxError(struct.getName().getName().isEmpty())) {
        continue;
      }
      String name = struct.getName().getName();
      if (structs.containsKey(name)) {
        err.error(struct.getPosition(), "Struct already declared");
      } else {
        structs.put(name, new StructData());
        dedupBuilder.add(struct);
      }
    }
    ImmutableList<Ast.StructDecl> deduplicated = dedupBuilder.build();
    Optional<List<Ast.StructDecl>> maybeOrdering = Structs.topologicalOrder(deduplicated, err);
    // If we could not find a topological ordering, fall-back to random ordering; this is only used
    // as a guarantee for code generation if the program is valid.
    List<Ast.StructDecl> ordering = maybeOrdering.orElse(deduplicated);
    for (Ast.StructDecl decl : ordering) {
      String name = decl.getName().getName();
      StructData data = structs.get(name);
      data.type = Types.struct(data.index);
      data.index = programBuilder.getStructsCount();
      data.name = name;
      structNames.put(data.index, name);
      Prog.Struct.Builder struct = programBuilder.addStructsBuilder();
      for (Ast.StructField field : decl.getFieldsList()) {
        if (syntaxError(field.getType().getName().isEmpty())) {
          continue;
        }
        Prog.Type t = Types.resolve(field.getType(), this);
        if (t == Types.ERROR) {
          err.error(field.getType().getPosition(), "Unknown type");
        }
        data.fields.put(field.getName().getName(), struct.getFieldsCount());
        struct.addFields(t);
      }
    }
  }

  private void registerExtern(Ast.FuncDecl decl) {
    String name = decl.getName().getName();
    if (syntaxError(name.isEmpty())) {
      return;
    }
    if (externs.containsKey(name) || funcIdx.containsKey(name)) {
      err.error(decl.getPosition(), "A function with this name is already defined");
      return;
    }
    Prog.Type returnType = decl.hasReturnType() ? resolveType(decl.getReturnType()) : Types.VOID;
    List<Prog.Type> params =
        decl.getParamsList().stream()
            .map(p -> resolveType(p.getType()))
            .collect(Collectors.toUnmodifiableList());
    externs.put(name, new FuncData(returnType, params));
  }

  private List<Ast.Func> registerFunctions(Prog.Program.Builder builder, List<Ast.Func> decls) {
    ImmutableList.Builder<Ast.Func> funcsToCheck = ImmutableList.builder();
    for (Ast.Func func : decls) {
      Ast.FuncDecl decl = func.getDecl();
      String name = decl.getName().getName();
      if (syntaxError(name.isEmpty())) {
        continue;
      }
      if (externs.containsKey(name) || funcIdx.containsKey(name)) {
        err.error(decl.getPosition(), "Function with this name is already declared.");
        continue;
      }
      funcIdx.put(name, builder.getFunctionsCount());
      Prog.Type type = decl.hasReturnType() ? resolveType(decl.getReturnType()) : Types.VOID;
      List<Prog.Type> params =
          decl.getParamsList().stream()
              .map(p -> resolveType(p.getType()))
              .collect(Collectors.toUnmodifiableList());
      builder.addFunctionsBuilder().setReturnType(type).addAllParams(params);
      funcsToCheck.add(func);
    }
    return funcsToCheck.build();
  }

  private void checkFunctions(Prog.Program.Builder builder, List<Ast.Func> functions) {
    for (Ast.Func func : functions) {
      Prog.Func.Builder funcBuilder =
          builder.getFunctionsBuilder(funcIdx.get(func.getDecl().getName().getName()));
      checkFunction(funcBuilder, func);
    }
  }

  private void checkFunction(Prog.Func.Builder funcBuilder, Ast.Func func) {
    scope = scope.push(funcBuilder);
    List<Ast.FuncParam> params = func.getDecl().getParamsList();
    // Register function parameters
    for (int i = 0; i < params.size(); i++) {
      Ast.FuncParam p = params.get(i);
      syntaxError(p.getName().getName().isEmpty() || p.getType().getName().isEmpty());
      scope.put(p.getName().getName(), Prog.VarRef.newBuilder().setFunctionParam(i).build());
      // Note: types have already been added to the Func.Builder during registration.
    }
    checkStatement(func.getBody());
    scope = scope.pop();
  }

  private void checkStatement(Ast.Statement body) {
    switch (body.getStatementCase()) {
      case BLOCK:
        // Only if our body is not a block do we push a new scope.
        scope = scope.push(scope.scope.addBodyBuilder().getBlockBuilder());
        body.getBlock().getBodyList().forEach(this::checkStatement);
        scope = scope.pop();
        break;
      case CONDITIONAL:
        checkConditional(body.getConditional());
        break;
      case LOOP:
        checkLoop(body.getLoop());
        break;
      case DECL:
        checkDecl(body.getDecl());
        break;
      case EXPRESSION:
        checkExpr(body.getExpression());
        break;
      case RETURNVALUE:
        checkReturn(body.getReturnValue());
        break;
      case STATEMENT_NOT_SET:
        throw new IllegalArgumentException();
    }
  }

  private void checkDecl(Ast.VarDecl decl) {
    checkArgument(decl.hasInit(), decl.hasName());
    String name = decl.getName().getName();
    if (scope.collides(name)) {
      err.error(decl.getPosition(), "Variable with this name already defined in same scope.");
      return;
    }
    Prog.Expr value = expr(decl.getInit());
    Prog.VarRef varRef =
        Prog.VarRef.newBuilder().setFunctionIndex(scope.function.getVariablesCount()).build();
    if (!expectType(value.getType(), resolveType(decl.getType()), decl.getInit().getPosition())) {
      return;
    }
    scope.put(name, varRef);
    scope.function.addVariables(value.getType());
    scope
        .scope
        .addBodyBuilder()
        .getExpressionBuilder()
        .getAssignmentBuilder()
        .setVariable(varRef)
        .setValue(value);
  }

  private void checkLoop(Ast.Loop loop) {
    // We need a new scope before the body since the initializer has its own scope.
    scope = scope.push(scope.scope.addBodyBuilder().getBlockBuilder());
    checkArgument(loop.hasBody());
    // Check init first to generate it before the loop statement.
    if (loop.hasInit()) {
      checkStatement(loop.getInit());
    }

    Prog.Loop.Builder loopBuilder = scope.scope.addBodyBuilder().getLoopBuilder();
    if (loop.hasCondition()) {
      Prog.Expr cond = expr(loop.getCondition());
      if (expectType(cond.getType(), Types.BOOLEAN, loop.getCondition().getPosition())) {
        return;
      }
      loopBuilder.setCondition(cond);
    } else {
      loopBuilder.setCondition(
          Prog.Expr.newBuilder()
              .setType(Types.BOOLEAN)
              .setConstant(Prog.Constant.newBuilder().setBoolConst(true)));
    }

    scope = scope.push(loopBuilder.getBodyBuilder());
    checkStatement(loop.getBody());
    scope = scope.pop();

    scope = scope.pop();
  }

  private void checkConditional(Ast.Conditional conditional) {
    checkArgument(conditional.hasCondition() && conditional.hasBody());
    Prog.Expr cond = expr(conditional.getCondition());
    if (expectType(cond.getType(), Types.BOOLEAN, conditional.getPosition())) {
      return;
    }
    Prog.Conditional.Builder condBuilder =
        scope.scope.addBodyBuilder().getConditionalBuilder().setCondition(cond);
    boolean bodyIsBlock = conditional.getBody().hasBlock();
    if (!bodyIsBlock) {
      scope = scope.push(condBuilder.getBodyBuilder());
    }
    checkStatement(conditional.getBody());
    if (!bodyIsBlock) {
      scope = scope.pop();
    }
    if (conditional.hasElseBody()) {
      Ast.Statement elseBody = conditional.getElseBody();
      boolean elseBodyIsBlock = elseBody.hasBlock();
      if (!elseBodyIsBlock) {
        scope = scope.push(condBuilder.getElseBodyBuilder());
      }
      checkStatement(conditional.getElseBody());
      if (!elseBodyIsBlock) {
        scope = scope.pop();
      }
    }
  }

  private void checkReturn(Ast.ReturnValue returnValue) {
    Prog.Returns.Builder returnBuilder =
        scope.function.getBodyBuilder().addBodyBuilder().getReturnValueBuilder();
    if (returnValue.hasValue()) {
      if (scope.function.getReturnType().equals(Types.VOID)) {
        err.error(returnValue.getValue().getPosition(), "Return with value in void function");
      } else {
        returnBuilder.setReturnValue(expr(returnValue.getValue()));
      }
    } else if (!scope.function.getReturnType().equals(Types.VOID)) {
      err.error(returnValue.getValue().getPosition(), "Return missing value");
    }
  }

  private void checkExpr(Ast.Expr expression) {
    scope.function.getBodyBuilder().addBodyBuilder().setExpression(expr(expression));
  }

  private void createGlobalInit(Prog.Program.Builder builder, List<Ast.VarDecl> globalsList) {
    Prog.Func.Builder func = builder.addFunctionsBuilder();
    func.setReturnType(Types.VOID);
    Prog.Scope.Builder body = func.getBodyBuilder();

    // Register globals one at a time after they are initialized, to at least avoid direct
    // references to uninitialized globals. It may still be possible since functions called can
    // still refer to uninitialized globals.
    for (int i = 0; i < globalsList.size(); i++) {
      Ast.VarDecl decl = globalsList.get(i);
      checkArgument(decl.hasName(), "Global must have name");
      String varName = decl.getName().getName();
      if (scope.collides(varName)) {
        err.error(decl.getPosition(), "Global variable with this name already exists");
        continue;
      }
      Prog.Type type = resolveType(decl.getType());
      Prog.VarRef varRef = Prog.VarRef.newBuilder().setGlobalIndex(i).build();
      Prog.Expr value = expr(decl.getInit());
      if (!expectType(value.getType(), type, decl.getInit().getPosition())) {
        err.error(
            decl.getInit().getPosition(),
            "Expression has mismatched type " + Types.asString(value.getType(), this));
        continue;
      }
      body.addBodyBuilder()
          .getExpressionBuilder()
          .setType(type)
          .getAssignmentBuilder()
          .setVariable(varRef)
          .setValue(value);
      builder.addGlobals(type);
      scope.put(varName, varRef);
    }
  }

  private Prog.Expr expr(Ast.Expr expr) {
    switch (expr.getExprCase()) {
      case BINARY:
        return checkBinary(expr.getBinary());
      case VALUE:
        return checkValue(expr.getValue());
      case CALL:
        return checkCall(expr.getCall());
      case REFERENCE:
        return checkReference(expr.getReference());
      case CONDITIONAL:
        return checkTernary(expr.getConditional());
      case ASSIGNMENT:
        return checkAssignment(expr.getAssignment());
      case UNARY:
        return checkUnary(expr.getUnary());
      case EXPR_NOT_SET:
        throw new IllegalArgumentException();
    }
    throw new IllegalArgumentException();
  }

  private static ImmutableMap<Ast.BinaryOp, Prog.BinaryOp> OP_CONV =
      ImmutableMap.<Ast.BinaryOp, Prog.BinaryOp>builder()
          .put(Ast.BinaryOp.LESS_THAN, Prog.BinaryOp.LESS_THAN)
          .put(Ast.BinaryOp.GREATER_THAN, Prog.BinaryOp.GREATER_THAN)
          .put(Ast.BinaryOp.LESS_EQUALS, Prog.BinaryOp.LESS_EQUALS)
          .put(Ast.BinaryOp.GREATER_EQUALS, Prog.BinaryOp.GREATER_EQUALS)
          .put(Ast.BinaryOp.EQUALS, Prog.BinaryOp.EQUALS)
          .put(Ast.BinaryOp.NOT_EQUALS, Prog.BinaryOp.NOT_EQUALS)
          .put(Ast.BinaryOp.ARRAY_ACCESS, Prog.BinaryOp.ARRAY_ACCESS)
          .put(Ast.BinaryOp.ADD, Prog.BinaryOp.ADD)
          .put(Ast.BinaryOp.SUBTRACT, Prog.BinaryOp.SUBTRACT)
          .put(Ast.BinaryOp.MULTIPLY, Prog.BinaryOp.MULTIPLY)
          .put(Ast.BinaryOp.DIVIDE, Prog.BinaryOp.DIVIDE)
          .put(Ast.BinaryOp.MODULO, Prog.BinaryOp.MODULO)
          .put(Ast.BinaryOp.AND, Prog.BinaryOp.AND)
          .put(Ast.BinaryOp.OR, Prog.BinaryOp.OR)
          .build();

  private Prog.Expr checkBinary(Ast.BinaryExpr binary) {
    Prog.Expr left = expr(binary.getLeft());
    Prog.Expr right = expr(binary.getRight());
    Prog.Expr.Builder e = Prog.Expr.newBuilder().setType(Types.ERROR);
    e.getBinaryBuilder()
        .setLeft(left)
        .setRight(right)
        .setOperator(OP_CONV.get(binary.getOperator()));
    boolean errors = left.getType().equals(Types.ERROR) || right.getType().equals(Types.ERROR);
    switch (binary.getOperator()) {
      case LESS_THAN:
      case GREATER_THAN:
      case LESS_EQUALS:
      case GREATER_EQUALS:
      case EQUALS:
      case NOT_EQUALS:
        e.setType(Types.BOOLEAN);
        if (left.getType().equals(Types.INT)) {
          expectType(right.getType(), Types.INT, binary.getRight().getPosition());
        } else if (left.getType().equals(Types.BOOLEAN)) {
          expectType(right.getType(), Types.BOOLEAN, binary.getRight().getPosition());
        } else if (left.getType().equals(Types.CHAR)) {
          expectType(right.getType(), Types.CHAR, binary.getRight().getPosition());
        } else {
          e.setType(Types.ERROR);
        }
        break;
      case ARRAY_ACCESS:
        Prog.Type ltype = left.getType();
        if (!ltype.equals(Types.ERROR) && !ltype.hasArray()) {
          err.error(binary.getPosition(), "Indexing on non-array value");
        } else if (expectType(right.getType(), Types.INT, binary.getRight().getPosition())) {
          e.setType(ltype.getArray());
        }
        break;
      case ADD:
      case SUBTRACT:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        if (expectType(left.getType(), Types.INT, binary.getLeft().getPosition())
            && expectType(right.getType(), Types.INT, binary.getRight().getPosition())
            && !errors) {
          e.setType(Types.INT);
        }
        break;
      case AND:
      case OR:
        if (expectType(left.getType(), Types.BOOLEAN, binary.getLeft().getPosition())
            && expectType(right.getType(), Types.BOOLEAN, binary.getRight().getPosition())
            && !errors) {
          e.setType(Types.BOOLEAN);
        }
        break;
      default:
        throw new IllegalArgumentException();
    }
    return e.build();
  }

  private Prog.Expr checkCall(Ast.FuncCall call) {
    checkArgument(call.hasFunction());
    String name = call.getFunction().getName();
    List<Prog.Expr> paramVals =
        call.getParamsList().stream().map(this::expr).collect(Collectors.toUnmodifiableList());
    if (externs.containsKey(name)) {
      return checkExtern(call, externs.get(name), paramVals);
    } else if (funcIdx.containsKey(name)) {
      return checkFunc(call, funcIdx.get(name), paramVals);
    } else {
      return Prog.Expr.newBuilder().setType(Types.ERROR).build();
    }
  }

  private Prog.Expr checkFunc(Ast.FuncCall call, int pos, List<Prog.Expr> paramVals) {
    Prog.Func.Builder called = programBuilder.getFunctionsBuilder(pos);
    List<Prog.Type> callTypes =
        paramVals.stream().map(Prog.Expr::getType).collect(Collectors.toList());
    int functionParams = called.getParamsCount();
    int callParams = callTypes.size();
    if (callParams != functionParams) {
      err.error(
          call.getPosition(), "Got " + callParams + " parameters, expected " + functionParams);
    } else {
      for (int i = 0; i < paramVals.size(); i++) {
        expectType(callTypes.get(i), called.getParams(i), call.getParams(i).getPosition());
      }
    }
    Prog.Expr.Builder builder = Prog.Expr.newBuilder().setType(called.getReturnType());
    builder.getCallBuilder().setFunction(pos).addAllParams(paramVals);
    return builder.build();
  }

  private Prog.Expr checkExtern(Ast.FuncCall call, FuncData funcData, List<Prog.Expr> paramVals) {
    List<Prog.Type> callTypes =
        paramVals.stream().map(Prog.Expr::getType).collect(Collectors.toList());
    int functionParams = funcData.argTypes.size();
    int callParams = callTypes.size();
    if (callParams != functionParams) {
      err.error(
          call.getPosition(), "Got " + callParams + " parameters, expected " + functionParams);
    } else {
      for (int i = 0; i < paramVals.size(); i++) {
        expectType(callTypes.get(i), funcData.argTypes.get(i), call.getParams(i).getPosition());
      }
    }
    Prog.Expr.Builder builder = Prog.Expr.newBuilder().setType(funcData.returnType);
    builder.getExternBuilder().setName(call.getFunction().getName()).addAllParams(paramVals);
    return builder.build();
  }

  private Prog.Expr checkAssignment(Ast.Assignment assignment) {
    Prog.Expr.Builder e = Prog.Expr.newBuilder();
    checkArgument(assignment.hasVariable() && assignment.hasValue());
    Optional<Prog.VarRef> resolve = scope.resolve(assignment.getVariable().getName());
    if (resolve.isEmpty()) {
      err.error(assignment.getPosition(), "No such variable exists");
      e.setType(Types.ERROR);
    } else {
      e.setType(toExpr(resolve.get()).getType());
      e.getAssignmentBuilder().setVariable(resolve.get());
    }

    Prog.Expr val = expr(assignment.getValue());
    if (!expectType(val.getType(), e.getType(), assignment.getValue().getPosition())) {
      e.setType(Types.ERROR);
    }
    if (val.getType().equals(Types.ERROR)) {
      e.setType(Types.ERROR);
    }
    return e.build();
  }

  private Prog.Expr checkReference(Ast.Identifier reference) {
    Optional<Prog.VarRef> resolve = scope.resolve(reference.getName());
    if (resolve.isEmpty()) {
      err.error(reference.getPosition(), "No such variable");
      return Prog.Expr.newBuilder().setType(Types.ERROR).build();
    }
    Prog.VarRef ref = resolve.get();
    return toExpr(ref);
  }

  private Prog.Expr toExpr(Prog.VarRef ref) {
    Prog.Expr.Builder builder = Prog.Expr.newBuilder().setReference(ref);
    switch (ref.getRefCase()) {
      case FUNCTION_INDEX:
        builder.setType(scope.function.getVariables(ref.getFunctionIndex()));
        break;
      case FUNCTION_PARAM:
        builder.setType(scope.function.getParams(ref.getFunctionParam()));
        break;
      case GLOBAL_INDEX:
        builder.setType(programBuilder.getGlobals(ref.getGlobalIndex()));
        break;
      default:
        throw new IllegalArgumentException();
    }
    return builder.build();
  }

  private Prog.Expr checkTernary(Ast.Ternary conditional) {
    checkArgument(conditional.hasCond() && conditional.hasLeft() && conditional.hasRight());
    Prog.Expr cond = expr(conditional.getCond());
    expectType(cond.getType(), Types.BOOLEAN, conditional.getCond().getPosition());
    Prog.Expr left = expr(conditional.getLeft());
    Prog.Expr right = expr(conditional.getRight());
    Prog.Type type = left.getType();
    if (!expectType(right.getType(), left.getType(), conditional.getRight().getPosition())) {
      type = Types.ERROR;
    } else if (right.getType().equals(Types.ERROR)) {
      type = Types.ERROR;
    }
    return Prog.Expr.newBuilder()
        .setType(type)
        .setConditional(Prog.Ternary.newBuilder().setCond(cond).setLeft(left).setRight(right))
        .build();
  }

  private Prog.Expr checkValue(Ast.Value value) {
    Prog.Expr.Builder b = Prog.Expr.newBuilder();
    Prog.Constant.Builder builder = b.getConstantBuilder();
    switch (value.getLiteralCase()) {
      case BOOL_LITERAL:
        builder.setBoolConst(value.getBoolLiteral());
        b.setType(Types.BOOLEAN);
        break;
      case INT_LITERAL:
        builder.setIntConst(value.getIntLiteral());
        b.setType(Types.INT);
        break;
      case STRING_LITERAL:
        builder.setStringConst(value.getStringLiteral());
        b.setType(Types.STRING);
        break;
      case CHAR_LITERAL:
        builder.setCharConst(value.getCharLiteral());
        b.setType(Types.CHAR);
        break;
      default:
        throw new IllegalArgumentException();
    }
    return b.build();
  }

  private Prog.Expr checkUnary(Ast.UnaryExpr unary) {
    Prog.Expr val = expr(unary.getExpr());
    Prog.Expr.Builder e = Prog.Expr.newBuilder().setType(val.getType());
    Prog.UnaryExpr.Builder unaryBuilder = e.getUnaryBuilder().setExpr(val);
    if (unary.getOperator() == Ast.UnaryOp.NOT) {
      expectType(val.getType(), Types.BOOLEAN, unary.getExpr().getPosition());
      unaryBuilder.setOperator(Prog.UnaryOp.NOT);
    } else if (unary.getOperator() == Ast.UnaryOp.NEGATE) {
      expectType(val.getType(), Types.INT, unary.getExpr().getPosition());
      unaryBuilder.setOperator(Prog.UnaryOp.NEGATE);
    } else {
      throw new IllegalArgumentException();
    }
    return e.build();
  }

  private Prog.Type resolveType(Ast.Type type) {
    Optional<Prog.Type> builtin = Types.builtin(type);
    if (builtin.isPresent()) {
      return builtin.get();
    } else {
      err.error(type.getPosition(), "Unknown type");
      return Types.ERROR;
    }
  }

  private boolean expectType(Prog.Type has, Prog.Type expected, Ast.Pos pos) {
    // Error type checks with anything to avoid error messages where generated.
    if (has.equals(Types.ERROR) || expected.equals(Types.ERROR)) {
      return true;
    }
    if (!has.equals(expected)) {
      err.error(
          pos,
          "Expected type "
              + Types.asString(expected, this)
              + " but was "
              + Types.asString(has, this));
      return false;
    }
    return true;
  }

  private boolean syntaxError(boolean isError) {
    if (!permissive && isError) {
      throw new IllegalArgumentException();
    }
    return isError;
  }

  public static Prog.Program typeCheck(Ast.Program program, Errors err) {
    return new TypeChecker(!program.getValid(), err).checkProgram(program);
  }

  private static class Scope {
    private final Scope parent;
    private final HashMap<String, Prog.VarRef> vars = new HashMap<>();
    public Prog.Func.Builder function;
    public Prog.Scope.Builder scope;

    private Scope(Scope parent, Prog.Func.Builder function, Prog.Scope.Builder scope) {
      this.parent = parent;
      this.function = function;
      this.scope = scope;
    }

    public Optional<Prog.VarRef> resolve(String name) {
      if (vars.containsKey(name)) {
        return Optional.of(vars.get(name));
      }
      if (parent != null) {
        return parent.resolve(name);
      }
      return Optional.empty();
    }

    public boolean collides(String name) {
      return vars.containsKey(name);
    }

    public void put(String name, Prog.VarRef ref) {
      checkArgument(!vars.containsKey(name));
      vars.put(name, ref);
    }

    @CheckReturnValue
    public Scope push(Prog.Scope.Builder scope) {
      return new Scope(this, function, scope);
    }

    @CheckReturnValue
    public Scope push(Prog.Func.Builder function) {
      return new Scope(this, function, function.getBodyBuilder());
    }

    @CheckReturnValue
    public Scope pop() {
      return this.parent;
    }

    @CheckReturnValue
    static Scope root() {
      return new Scope(null, null, null);
    }
  }

  private static class FuncData {
    Prog.Type returnType;
    List<Prog.Type> argTypes;

    public FuncData(Prog.Type returnType, List<Prog.Type> argTypes) {
      this.returnType = returnType;
      this.argTypes = argTypes;
    }
  }

  static class StructData {
    HashMap<String, Integer> fields = new HashMap<>(); // The indices of the field in programBuilder
    int index; // The index of this struct in the programBuilder
    String name;
    Prog.Type type;
  }
}
