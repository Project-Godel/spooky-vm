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
    assertOk("x: Int = 1;");

    assertErr("x: Int = false;", "type conversion");
  }

  @Test
  public void testExterns() {
    assertOk("extern fisk()");
    assertOk("extern fisk() -> Int");
    assertOk("extern fisk(x: Int)");
    assertOk("extern fisk(x: Int, y: Boolean)");
  }

  @Test
  public void testFuncs() {
    assertOk("func fisk() {}");
    assertOk("func fisk() -> Int { return 1; }");
    assertOk("func fisk(x: Int) {}");
    assertOk("func fisk(x: Int, y: Boolean) {}");
  }

  @Test
  public void testReturn() {
    assertOk("func x() { return; }");
    assertErr("func x() { return 1; }", "value from void");
    assertErr("func fisk() -> Int {}", "return");
    assertErr("func fisk() -> Int { return true; }", "wrong type");
  }

  @Test
  public void testWhile() {
    assertOk("func x() { while(true) {} }");
    assertOk("func x() { while(true) ; }");

    assertErr("func x() { while(1) ; }", "incorrect type");
  }

  @Test
  public void testFor() {
    assertOk("func x() { for(;;) {} }");
    assertOk("func x() { for(x: Int = 2; x < 5; x += 1){} }");
    assertOk("func x() { for(x: Int = 2;; x += 1){} }");
    assertOk("func x() { for(x: Int = 2; x < 5;){} }");
    assertOk("func x() { x: Int = 1; for(; x < 5;){} }");

    assertErr("func x() { for(; x < 5; x += 1){} }", "Undefined variable x");
  }

  @Test
  public void testIf() {
    assertOk("func x() { if(true) ; }");
    assertErr("func x() { if(1) ; }", "incorrect type");
  }

  @Test
  public void testExpressions() {
    // Assign
    assertOk(
        """
            func x(){
              a: Int = 4; b: Int = 5; c: Int = 6; d: Int = 7; e: Int = 8; f: Int = 9; g: Int = 10;
              a += b /= c *= d -= e %= f = g;
            }
          """);

    assertErr("func x() { 1 + true; }", "incorrect type");
    // TODO: assertOk("func x(){ a.b = c ;}");
    // TODO: assertOk("func x(){ a[b] = c ;}");

    assertOk("func x(){  true ? 1 : 2; }");
    assertErr("func x(){  2 ? 1 : 2; }", "incorrect type");
    assertErr("func x(){  true ? 1 : false; }", "incompatible types");

    // Binary operators
    assertOk(
        """
            func x() {
              a: Int = 4; b: Int = 5; c: Int = 6; d: Int = 7; e: Int = 8; f: Int = 9; g: Int = 10;
              (a + b - c / d * e % f) > 1;
              1 < 2;
              3 >= 4;
              5 == 6 && 7 <= 8 || 9 != 0;
            }
             """);

    assertErr("func x() { 1 < true; }", "incorrect type");

    // TODO: RTL unary
    assertOk("func x() { !true; }");
    assertOk("func x() { b: Int = 1; -b; }");
    assertOk("func x() { b: Int = 1; -b * -b; }");
    assertOk("func x() { true && !!!false; }");
    assertOk("func x() { b: Int = 1; --b; }");
    assertOk("func x() { b: Int = 1; ++b; }");

    assertErr("func x() { !1; }", "incorrect type");
    assertErr("func x() { -false; }", "incorrect type");

    // LTR unary
    assertOk("func x() { a: Int = 1; a++; }");
    assertOk("func x() { a: Int = 1; a--; }");
    // TODO:
    // assertOk("func x() { a()[123]; }");
    // assertOk("func x() { a[123]; }");
    // assertOk("func x() { a[123][321]; }");
    // assertOk("func x() { a.b; }");
    // assertOk("func x() { a.b[123]; }");
    // assertOk("func x() { a().b; }");

    // Stress-tests
    // assertOk("func x(){ x + 5 ? a : b(!4, -3, 5 || a || x2 % c(65) && 123 - (a[b + c])); }");
  }

  @Test
  public void testFunctionCalls() {
    assertOk("func x() { x(); }");
  }

  @Test
  public void testLiterals() {
    // assertOk("func x(){ \"hej\"; }");
    // assertOk("func x(){ \"hej\\n\"; }");
    // assertOk("func x(){ \"\\\"\"; }");
    // assertOk("func x(){ \"\\n\\r\\t\\\"\\\\\\' \"; }");
    assertOk("func x(){ 'a'; }");
    assertOk("func x(){ '\\\\'; }");
    assertOk("func x(){ '\\''; }");
    assertOk("func x(){ 2147483647; }");
    assertOk("func x(){ -2147483648; }");
    assertOk("func x(){ true; }");
    assertOk("func x(){ false; }");
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
    assertOk("func x(){ x: Int = 0; }");
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
