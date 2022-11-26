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
    assertOk("int x = 1;");

    assertErr("x = 1;", "expected func");
    assertErr("int = 1;", "expected func");
    assertErr("int x 1;", "expected func");
    assertErr("int x = 1", "expected ;");
    assertErr("int x = ;", "unexpected ;");
  }

  @Test
  public void testExterns() {
    assertOk("extern void fisk()");
    assertOk("extern int fisk()");
    assertOk("extern void fisk(int x)");
    assertOk("extern void fisk(int x, bool y)");

    assertErr("extern", "expected type");
    assertErr("extern fisk()", "expected type");
    assertErr("extern void fisk(x)", "expected )");
    assertErr("extern void fisk(int)", "expected parameter");
    assertErr("extern void", "expected (");
  }

  @Test
  public void testFuncs() {
    assertOk("void fisk() {}");
    assertOk("int fisk() {}");
    assertOk("void fisk(int x) {}");
    assertOk("void fisk(int x, bool y) {}");

    assertErr("void {}", "expected function");
    assertErr("void (){}", "expected function");
    assertErr("void fisk(x) {}", "expected )");
    assertErr("void fisk(int) {}", "expected parameter");
    assertErr("void fisk(x y) {}", "expected )");
    assertErr("int fisk()\nx: Int = 1;", "expected block");
  }

  @Test
  public void testBlocks() {
    assertOk("void x() {}");
    assertOk("void x() { x = 1; }");
    assertOk("void x() { x = 1; y = 1; }");

    assertErr("void x() {", "Unterminated");
  }

  @Test
  public void testReturn() {
    assertOk("void x() { return; }");
    assertOk("void x() { return 1; }");

    assertErr("void x() { return 1 }", "expected ;");
    assertErr("void x() { return }", "unexpected }");
  }

  @Test
  public void testWhile() {
    assertOk("void x() { while(true) {} }");
    assertOk("void x() { while(true) ; }");
    assertOk("void x() { while(true) x; }");
    assertOk("void x() { while(true) { x; } }");

    assertErr("void x() { while {} }", "expected (");
    assertErr("void x() { while(true) }", "unexpected }");
    assertErr("void x() { while() {} }", "unexpected )");
    assertErr("void x() { while( {} }", "expected )");
    assertErr("void x() { while) {} }", "expected (");
  }

  @Test
  public void testFor() {
    assertOk("void x() { for(;;) {} }");
    assertOk("void x() { for(;;) ; }");
    assertOk("void x() { for(;;) x; }");
    assertOk("void x() { for(int x = 2; x < 5; x += 1){} }");
    assertOk("void x() { for(int x = 2;; x += 1){} }");
    assertOk("void x() { for(; x < 5; x += 1){} }");
    assertOk("void x() { for(int x = 2; x < 5;){} }");

    assertErr("void x() { for(int x = 2; x < 5){} }", "expected ;");
    assertErr("void x() { for(return x;;){} }", "unexpected return");
    assertErr("void x() { for(;return x;) {} }", "unexpected return");
    assertErr("void x() { for(;;return x;) {} }", "unexpected return");
    assertErr("void x() { for ;;) {} }", "expected (");
    assertErr("void x() { for(;; {} }", "expected )");
    assertErr("void x() { for(;;)  }", "unexpected }");
  }

  @Test
  public void testIf() {
    assertOk("void x() { if(true) {} }");
    assertOk("void x() { if(true) ; }");
    assertOk("void x() { if(true) x; }");

    assertErr("void x() { if(true) }", "unexpected }");
    assertErr("void x() { if() {} }", "unexpected )");
    assertErr("void x() { if true) {} }", "expected (");
    assertErr("void x() { if (true {} }", "expected )");
  }

  @Test
  public void testExpressions() {
    // Assign
    assertOk(
        "void x(){ int a = 4; int b = 5; int c = 6; int d = 7; int e = 8; int f = 9; int g = 10;"
            + "a += b /= c *= d -= e %= f = g;}");
    // TODO assertOk("void x(){ a.b = c ;}");
    // assertOk("void x(){ a[b] = c ;}");

    // Ternary
    assertOk("void x(){ a ? b : c ? d : e ? f : g;}");
    assertOk("void x(){ a ? b ? c ? d : e : f : g;}");

    // Binary operators
    assertOk("void x() { a + b - c / d * e % f > 1 < 2 >= 3 <= 4 == 5 && 6 || 7 != 8; }");

    // RTL unary
    assertOk("void x() { -b; }");
    assertOk("void x() { -c * -b; }");
    assertOk("void x() { ++a; }");
    assertOk("void x() { --a; }");
    assertOk("void x() { a && !!!b; }");

    // LTR unary
    assertOk("void x() { a; }");
    assertOk("void x() { a(1, 2, 3); }");
    assertOk("void x() { a++; }");
    assertOk("void x() { a--; }");

    // TODO:
    // assertOk("func x() { a()[123]; }");
    // assertOk("func x() { a[123]; }");
    // assertOk("func x() { a[123][321]; }");
    // assertOk("void x() { a.b; }");
    assertOk("void x() { b(); }");
    // TODO: assertOk("func x() { a.b[123]; }");
    // assertOk("void x() { a().b; }");
    assertOk("void x() { b(); }");
    assertErr("void x() { a()(); }", "function name");
    assertErr("void x() { 'a'(); }", "function name");
    // assertErr("void x() { a[]; }", "unexpected ]");
    assertErr("void x() { a(; }", "unexpected ;");
    assertErr("void x() { a); }", "expected ;");

    // Parenthesized
    assertErr("void x() { ((a + b) & (c + d)); }", "");

    // Stress-tests
    // assertOk("func x(){ x + 5 ? a : b(!4, -3, 5 || a || x2 % c(65) && 123 - (a[b + c])); }");
  }

  @Test
  public void testCharLiterals() {
    assertOk("void x(){ 'a'; }");
    assertOk("void x(){ '\\\\'; }");
    assertOk("void x(){ '\\''; }");

    assertErr("void x(){ '\\'; }", "Unterminated");
    assertErr("void x(){ '; }", "Unterminated");
    assertErr("void x(){ '\\!'; }", "escape");
  }

  @Test
  public void testBoolLiterals() {
    assertOk("void x(){ true; }");
    assertOk("void x(){ false; }");
  }

  @Test
  public void testIntLiterals() {
    assertOk("void x(){ 2147483647; }");
    assertOk("void x(){ -2147483648; }");
    assertErr("void x(){ 2147483648; }", "range");
    assertErr("void x(){ -2147483649; }", "range");
  }

  // TODO: enable when strings are supported
  @Ignore
  public void testStringLiterals() {
    assertOk("void x(){ \"hej\"; }");
    assertOk("void x(){ \"hej\\n\"; }");
    assertOk("void x(){ \"\\\"\"; }");
    assertOk("void x(){ \"\\n\\r\\t\\\"\\\\\\' \"; }");

    assertErr("void x(){ \"hej\\\"; }", "Unterminated");
    assertErr("void x(){ \"; }", "Unterminated");
    assertErr("void x(){ \"\\!\"; }", "escape");
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
    assertOk("void x(){ int x= 0; }");
    assertErr("void x(){ int x= ; }", "unexpected ;");
    assertErr("void x(){ int x= 0 }", "expected ;");
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
