package se.jsannemo.spooky.compiler.parser;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;

public class ParserTest {

  @Test
  public void testEmpty() {
    assertOk("");
  }

  @Test
  public void testGlobals() {
    assertErr("x: = 1;", "type");
    assertErr("x = 1;", "expected fun");
    assertErr("x: = 1;", "type");
    assertErr("1: = 1;", "expected func");
    assertErr(": Int = 1;", "expected func");
    assertErr("x: Int 1;", "expected =");
    assertErr("x: Int = 1", "expected ;");
    assertErr("x: Int = ;", "unexpected ;");
    assertOk("x: Int = 1;");
  }

  @Test
  public void testExterns() {
    assertErr("extern", "function name");
    assertErr("extern fisk(x)", "expected :");
    assertErr("extern fisk(x:)", "type");
    assertErr("extern fisk(: Int)", "expected )");
    assertErr("extern fisk(x Int)", "expected :");
    assertErr("extern fisk", "expected (");
    assertOk("extern fisk()");
    assertOk("extern fisk() -> Int");
    assertOk("extern fisk(x: Int)");
    assertOk("extern fisk(x: Int, y: Boolean)");
  }

  @Test
  public void testFuncs() {
    assertErr("func {}", "function name");
    assertErr("func (){}", "function name");
    assertErr("func fisk(x) {}", "expected :");
    assertErr("func fisk(x:) {}", "type");
    assertErr("func fisk(: Int) {}", "expected )");
    assertErr("func fisk(x Int) {}", "expected :");
    assertErr("func fisk()\nx: Int = 1;", "expected block");
    assertOk("func fisk() {}");
    assertOk("func fisk() -> Int {}");
    assertOk("func fisk(x: Int) {}");
    assertOk("func fisk(x: Int, y: Boolean) {}");
  }

  @Test
  public void testBlocks() {
    assertErr("func x() {", "Unterminated");
    assertOk("func x() {}");
    assertOk("func x() { x = 1; }");
    assertOk("func x() { x = 1; y = 1; }");
  }

  @Test
  public void testReturn() {
    assertOk("func x() { return; }");
    assertOk("func x() { return 1; }");
    assertErr("func x() { return 1 }", "expected ;");
    assertErr("func x() { return }", "unexpected }");
  }

  @Test
  public void testWhile() {
    assertOk("func x() { while(true) {} }");
    assertOk("func x() { while(true) ; }");
    assertOk("func x() { while(true) x; }");
    assertOk("func x() { while(true) { x; } }");
    assertErr("func x() { while {} }", "expected (");
    assertErr("func x() { while(true) }", "unexpected }");
    assertErr("func x() { while() {} }", "unexpected )");
    assertErr("func x() { while( {} }", "expected )");
    assertErr("func x() { while) {} }", "expected (");
  }

  @Test
  public void testFor() {
    assertOk("func x() { for(;;) {} }");
    assertOk("func x() { for(;;) ; }");
    assertOk("func x() { for(;;) x; }");
    assertOk("func x() { for(x: Int = 2; x < 5; x += 1){} }");
    assertOk("func x() { for(x: Int = 2;; x += 1){} }");
    assertOk("func x() { for(; x < 5; x += 1){} }");
    assertOk("func x() { for(x: Int = 2; x < 5;){} }");

    assertErr("func x() { for(x: Int = 2; x < 5){} }", "expected ;");
    assertErr("func x() { for(return x;;){} }", "unexpected return");
    assertErr("func x() { for(;return x;) {} }", "unexpected return");
    assertErr("func x() { for(;;return x;) {} }", "unexpected return");
    assertErr("func x() { for ;;) {} }", "expected (");
    assertErr("func x() { for(;; {} }", "expected )");
    assertErr("func x() { for(;;)  }", "unexpected }");
  }

  @Test
  public void testIf() {
    assertOk("func x() { if(true) {} }");
    assertOk("func x() { if(true) ; }");
    assertOk("func x() { if(true) x; }");

    assertErr("func x() { if(true) }", "unexpected }");
    assertErr("func x() { if() {} }", "unexpected )");
    assertErr("func x() { if true) {} }", "expected (");
    assertErr("func x() { if (true {} }", "expected )");
  }

  @Test
  public void testExpressions() {
    // Assign
    assertOk(
        """
                  func x(){ a: Int = 4; b: Int = 5; c: Int = 6; d: Int = 7; e: Int = 8; f: Int = 9; g: Int = 10;
                  a += b /= c *= d -= e %= f = g;}
              """);
    assertOk("func x(){ a.b = c ;}");
    // TODO: assertOk("func x(){ a[b] = c ;}");

    // Ternary
    assertOk("func x(){ a ? b : c ? d : e ? f : g;}");
    assertOk("func x(){ a ? b ? c ? d : e : f : g;}");

    // Binary operators
    assertOk("func x() { a + b - c / d * e % f > 1 < 2 >= 3 <= 4 == 5 && 6 || 7 != 8; }");

    // RTL unary
    assertOk("func x() { -b; }");
    assertOk("func x() { -c * -b; }");
    assertOk("func x() { ++a; }");
    assertOk("func x() { --a; }");
    assertOk("func x() { a && !!!b; }");

    // LTR unary
    assertOk("func x() { a; }");
    assertOk("func x() { a(1, 2, 3); }");
    assertOk("func x() { a++; }");
    assertOk("func x() { a--; }");

    // TODO:
    // assertOk("func x() { a()[123]; }");
    // assertOk("func x() { a[123]; }");
    // assertOk("func x() { a[123][321]; }");
    assertOk("func x() { a.b; }");
    assertOk("func x() { b(); }");
    // TODO: assertOk("func x() { a.b[123]; }");
    assertOk("func x() { a().b; }");
    assertOk("func x() { b(); }");
    assertErr("func x() { a()(); }", "function name");
    assertErr("func x() { 'a'(); }", "function name");
    // TODO: assertErr("func x() { a[]; }", "unexpected ]");
    assertErr("func x() { a(; }", "unexpected ;");
    assertErr("func x() { a); }", "expected ;");

    // Parenthesized
    assertErr("func x() { ((a + b) & (c + d)); }", "");

    // Stress-tests
    // assertOk("func x(){ x + 5 ? a : b(!4, -3, 5 || a || x2 % c(65) && 123 - (a[b + c])); }");
  }

  @Test
  public void testLiterals() {
    assertOk("func x(){ \"hej\"; }");
    assertOk("func x(){ \"hej\\n\"; }");
    assertOk("func x(){ \"\\\"\"; }");
    assertOk("func x(){ \"\\n\\r\\t\\\"\\\\\\' \"; }");
    assertOk("func x(){ 'a'; }");
    assertOk("func x(){ '\\\\'; }");
    assertOk("func x(){ '\\''; }");
    assertOk("func x(){ 2147483647; }");
    assertOk("func x(){ -2147483648; }");
    assertOk("func x(){ true; }");
    assertOk("func x(){ false; }");

    assertErr("func x(){ \"hej\\\"; }", "Unterminated");
    assertErr("func x(){ \"; }", "Unterminated");
    assertErr("func x(){ '\\'; }", "Unterminated");
    assertErr("func x(){ '; }", "Unterminated");
    assertErr("func x(){ \"\\!\"; }", "escape");
    assertErr("func x(){ '\\!'; }", "escape");
    assertErr("func x(){ 2147483648; }", "range");
    assertErr("func x(){ -2147483649; }", "range");
  }

  // TODO: reenable when structs work
  @Ignore
  public void testStructs() {
    assertOk("struct s {}");
    assertOk("struct s {x: Int;}");
    assertOk("struct s {x: Int; y: Int;}");
    assertErr("struct s {x: Int a;}", "expected ;");
    assertErr("struct s {x: Int}", "expected ;");
    assertErr("struct s {x: Int;", "expected }");
    assertErr("struct {x: Int;}", "expected struct name");
    assertErr("struct s", "expected {");
  }

  @Test
  public void testInits() {
    assertOk("func x(){ x: Int = 0; }");
    assertErr("func x(){ x: Int = ; }", "unexpected ;");
    assertErr("func x(){ x: Int = 0 }", "expected ;");
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

    assertErr("func x(){ x = default; }", "unexpected default");
    assertErr("func x(){ x = {}; }", "unexpected {");
    assertErr("func x(){ x: Int = {x}; }", "expected :");
    assertErr("func x(){ x: Int = {x:}; }", "unexpected }");
    assertErr("func x(){ x: Int = {:y}; }", "expected }");
    assertErr("func x(){ x: Int = {x:y w:z}; }", "expected }");
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

    assertErr("func x(){ x: Int[5] = {default...}; }", "expected }");
    assertErr("func x(){ x: In)t[5] = {{...}}; }", "expected }");
  }

  @Test
  public void testFailing() {
    assertErr("func main { for (i: Int = 0; i << 5; i++) ; }", "expected (");
  }

  private static void assertOk(String s) {
    Errors parseErr = new Errors();
    Parser.parse(Tokenizer.create(s), parseErr);
    assertThat(parseErr.errors()).isEmpty();
  }

  private static void assertErr(String s, String msg) {
    Errors parseErr = new Errors();
    Parser.parse(Tokenizer.create(s), parseErr);
    assertThat(parseErr.errors()).isNotEmpty();
    for (Errors.Error e : parseErr.errors()) {
      if (e.toString().contains(msg)) {
        return;
      }
    }
    Assert.fail("Found no matching error " + msg + " in " + parseErr.errors());
  }
}
