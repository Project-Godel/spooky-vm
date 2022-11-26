package se.jsannemo.spooky.compiler.ir;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Preconditions;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;

public class IrTest {

  @Test
  public void testEmpty() {
    assertOk("");
  }

  @Test
  public void testGlobals() {
    assertOk("int x = 1;");

    assertErr("int x = false;", "type conversion");
  }

  @Test
  public void testExterns() {
    assertOk("extern void fisk()");
    assertOk("extern int fisk()");
    assertOk("extern void fisk(int x)");
    assertOk("extern void fisk(int x, bool y)");
  }

  @Test
  public void testFuncs() {
    assertOk("void fisk() {}");
    assertOk("int fisk() { return 1; }");
    assertOk("void fisk(int x) {}");
    assertOk("void fisk(int x, bool y) {}");
  }

  @Test
  public void testReturn() {
    assertOk("void x() { return; }");
    assertErr("void x() { return 1; }", "value from void");
    assertErr("int fisk() {}", "return");
    assertErr("int fisk() { return true; }", "wrong type");
  }

  @Test
  public void testWhile() {
    assertOk("void x() { while(true) {} }");
    assertOk("void x() { while(true) ; }");

    assertErr("void x() { while(1) ; }", "incorrect type");
  }

  @Test
  public void testFor() {
    assertOk("void x() { for(;;) {} }");
    assertOk("void x() { for(int x = 2; x < 5; x += 1){} }");
    assertOk("void x() { for(int x = 2;; x += 1){} }");
    assertOk("void x() { for(int x = 2; x < 5;){} }");
    assertOk("void x() { int x = 1; for(; x < 5;){} }");

    assertErr("void x() { for(; x < 5; x += 1){} }", "Undefined variable x");
  }

  @Test
  public void testIf() {
    assertOk("void x() { if(true) ; }");
    assertErr("void x() { if(1) ; }", "incorrect type");
  }

  @Test
  public void testExpressions() {
    // Assign
    assertOk(
        "void x(){"
            + "int a = 4; int b = 5; int c = 6; int d = 7; int e = 8; int f = 9; int g = 10;"
            + "a += b /= c *= d -= e %= f = g;"
            + "}");

    assertErr("void x() { 1 + true; }", "incorrect type");
    // TODO: assertOk("func x(){ a.b = c ;}");
    // TODO: assertOk("func x(){ a[b] = c ;}");

    assertOk("void x(){  true ? 1 : 2; }");
    assertErr("void x(){  2 ? 1 : 2; }", "incorrect type");
    assertErr("void x(){  true ? 1 : false; }", "incompatible types");

    // Binary operators
    assertOk(
        "void x() {"
            + "int a = 4; int b = 5; int c = 6; int d = 7; int e = 8; int f = 9; int g = 10;"
            + "(a + b - c / d * e % f) > 1;"
            + "1 < 2;"
            + "3 >= 4;"
            + "5 == 6 && 7 <= 8 || 9 != 0;"
            + "}");

    assertErr("void x() { 1 < true; }", "incorrect type");

    assertOk("void x() { !true; }");
    assertOk("void x() { int b = 1; -b; }");
    assertOk("void x() { int b = 1; -b * -b; }");
    assertOk("void x() { true && !!!false; }");
    assertOk("void x() { int b = 1; --b; }");
    assertOk("void x() { int b = 1; ++b; }");

    assertErr("void x() { !1; }", "incorrect type");
    assertErr("void x() { -false; }", "incorrect type");

    // LTR unary
    assertOk("void x() { int a = 1; a++; }");
    assertOk("void x() { int a = 1; a--; }");
    // TODO:
    // assertOk("void x() { a()[123]; }");
    // assertOk("void x() { a[123]; }");
    // assertOk("void x() { a[123][321]; }");
    // assertOk("void x() { a.b; }");
    // assertOk("void x() { a.b[123]; }");
    // assertOk("void x() { a().b; }");

    // Stress-tests
    // assertOk("void x(){ x + 5 ? a : b(!4, -3, 5 || a || x2 % c(65) && 123 - (a[b + c])); }");
  }

  @Test
  public void testFunctionCalls() {
    assertOk("void x() { x(); }");
  }

  @Test
  public void testLiterals() {
    // assertOk("func x(){ \"hej\"; }");
    // assertOk("func x(){ \"hej\\n\"; }");
    // assertOk("func x(){ \"\\\"\"; }");
    // assertOk("func x(){ \"\\n\\r\\t\\\"\\\\\\' \"; }");
    assertOk("void x(){ 'a'; }");
    assertOk("void x(){ '\\\\'; }");
    assertOk("void x(){ '\\''; }");
    assertOk("void x(){ 2147483647; }");
    assertOk("void x(){ -2147483648; }");
    assertOk("void x(){ true; }");
    assertOk("void x(){ false; }");
  }

  // TODO: reenable when structs work
  @Ignore
  public void testStructs() {
    assertOk("struct s {}");
    assertOk("struct s {x: Int;}");
    assertOk("struct s {x: Int; y: Int;}");
  }

  @Test
  public void testInits() {
    assertOk("void x(){ int x = 0; }");
  }

  // TODO: reenable when structs work
  @Ignore
  public void testStructInits() {
    // Structs
    assertOk("func x(){ x: Int = {}; }");
    assertOk("func x(){ x: Int = {x: y}; }");
    assertOk("func x(){ x: Int = {x: y, z: w}; }");
    assertOk("func x(){ x: Int = default; }");
    assertOk("struct hej{ fisk: Int[1]; } func blah { x: hej = {fisk: [5]}; }");
  }

  // TODO: reenable when arrays work
  @Ignore
  public void testArrayInits() {
    assertOk("func x(){ x: Int[] = []; }");
    assertOk("func x(){ x: Int[5] = [...]; }");
    assertOk("func x(){ x: Int[5] = default; }");
    assertOk("func x(){ x: Int[5] = [x, {blah: hej}, ...]; }");
    assertOk("func x(){ x: Int[5] = [x, ...default]; }");
    assertOk("func x(){ x: Int[7][8] = [[...]]; }");
    assertOk("func x(){ x: Int[7][8] = [[...]]; }");
    assertOk("func x(){ x: Int[9][11] = [[2, ...default],  [1, ...], ...]; }");
    assertOk("func x(){ x: Int[15][12] = [[...],  [1, {}, ...], ...default]; }");
  }

  private static void assertOk(String s) {
    Errors errors = new Errors();
    Program program = Parser.parse(Tokenizer.create(s), errors);
    Preconditions.checkArgument(errors.errors().isEmpty(), "Program did not parse correctly");

    ToIr.generate(program, errors);
    assertThat(errors.errors()).isEmpty();
  }

  private static void assertErr(String s, String msg) {
    Errors errors = new Errors();
    Program program = Parser.parse(Tokenizer.create(s), errors);
    Preconditions.checkArgument(errors.errors().isEmpty(), "Program did not parse correctly");

    ToIr.generate(program, errors);
    assertThat(errors.errors()).isNotEmpty();
    for (Errors.Error e : errors.errors()) {
      if (e.toString().contains(msg)) {
        return;
      }
    }
    Assert.fail("Found no matching error " + msg + " in " + errors.errors());
  }
}
