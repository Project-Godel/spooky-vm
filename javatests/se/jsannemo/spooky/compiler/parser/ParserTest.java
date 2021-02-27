package se.jsannemo.spooky.compiler.parser;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth;
import com.google.common.truth.extensions.proto.ProtoTruth;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;

public class ParserTest {

  @Test
  public void testEmpty() {
    assertOk("");
  }

  @Test
  public void testGlobals() {
    assertErr("x: = 1;", "type");
    assertErr("x = 1;", "Expected fun");
    assertErr("x: = 1;", "type");
    assertErr("1: = 1;", "Expected func");
    assertErr(": Int = 1;", "Expected func");
    assertErr("x: Int 1;", "Expected =");
    assertErr("x: Int = 1", "Expected ;");
    assertErr("x: Int = ;", "Unexpected ;");
    assertOk("x: Int = 1;");
  }

  @Test
  public void testExterns() {
    assertErr("extern", "function name");
    assertErr("extern fisk(x)", "Expected :");
    assertErr("extern fisk(x:)", "type");
    assertErr("extern fisk(: Int)", "Expected )");
    assertErr("extern fisk(x Int)", "Expected :");
    assertOk("extern fisk");
    assertOk("extern fisk()");
    assertOk("extern fisk -> Int");
    assertOk("extern fisk() -> Int");
    assertOk("extern fisk(x: Int)");
    assertOk("extern fisk(x: Int, y: Boolean)");
  }

  @Test
  public void testFuncs() {
    assertErr("func {}", "function name");
    assertErr("func fisk(x) {}", "Expected :");
    assertErr("func fisk(x:) {}", "type");
    assertErr("func fisk(: Int) {}", "Expected )");
    assertErr("func fisk(x Int) {}", "Expected :");
    assertErr("func fisk\nx: Int = 1;", "Expected block");
    assertOk("func fisk() {}");
    assertOk("func fisk -> Int {}");
    assertOk("func fisk() -> Int {}");
    assertOk("func fisk(x: Int) {}");
    assertOk("func fisk(x: Int, y: Boolean) {}");
  }

  @Test
  public void testBlocks() {
    assertErr("func x {", "Unterminated");
    assertOk("func x {}");
    assertOk("func x { x = 1; }");
    assertOk("func x { x = 1; y = 1; }");
  }

  @Test
  public void testReturn() {
    assertOk("func x{ return; }");
    assertOk("func x{ return 1; }");
    assertErr("func x{ return 1 }", "Expected ;");
    assertErr("func x{ return }", "Expected ;");
  }

  @Test
  public void testWhile() {
    assertOk("func x{ while {} }");
    assertOk("func x{ while(true) {} }");
    assertOk("func x{ while(true) ; }");
    assertOk("func x{ while(true) x; }");
    assertOk("func x{ while(true) { x; } }");
    assertErr("func x{ while(true) }", "Unexpected }");
    assertErr("func x{ while() {} }", "Unexpected )");
    assertErr("func x{ while( {} }", "Expected )");
    assertErr("func x{ while) {} }", "Unexpected )");
  }

  @Test
  public void testFor() {
    assertOk("func x{ for(;;) {} }");
    assertOk("func x{ for(;;) ; }");
    assertOk("func x{ for(;;) x; }");
    assertOk("func x{ for(x: Int = 2; x < 5; x += 1){} }");
    assertOk("func x{ for(x: Int = 2;; x += 1){} }");
    assertOk("func x{ for(; x < 5; x += 1){} }");
    assertOk("func x{ for(x: Int = 2; x < 5;){} }");

    assertErr("func x{ for(x: Int = 2; x < 5){} }", "Expected ;");
    assertErr("func x{ for(return x;;){} }", "Unexpected return");
    assertErr("func x{ for(;return x;) {} }", "Unexpected return");
    assertErr("func x{ for(;;return x;) {} }", "Unexpected return");
    assertErr("func x{ for ;;) {} }", "Expected (");
    assertErr("func x{ for(;; {} }", "Expected )");
    assertErr("func x{ for(;;)  }", "Unexpected }");
  }

  @Test
  public void testIf() {
    assertOk("func x{ if(true) {} }");
    assertOk("func x{ if(true) ; }");
    assertOk("func x{ if(true) x; }");

    assertErr("func x{ if(true) }", "Unexpected }");
    assertErr("func x{ if() {} }", "Unexpected )");
    assertErr("func x{ if true) {} }", "Expected (");
    assertErr("func x{ if (true {} }", "Expected )");
  }

  @Test
  public void testExpressions() {
    // Assign
    assertOk("func x{ a += b /= c *= d -= e %= f = g;}");
    assertOk("func x{ a.b = c ;}");
    assertOk("func x{ a[b] = c ;}");

    // Ternary
    assertOk("func x{ a ? b : c ? d : e ? f : g;}");
    assertOk("func x{ a ? b ? c ? d : e : f : g;}");

    // Binary operators
    assertOk("func x { a + b - c / d * e % f > 1 < 2 >= 3 <= 4 == 5 && 6 || 7 != 8; }");

    // RTL unary
    assertOk("func x { !a; }");
    assertOk("func x { -b; }");
    assertOk("func x { -c * -b; }");
    assertOk("func x { -c * -++--b; }");
    assertOk("func x { a && !!!b; }");

    // LTR unary
    assertOk("func x { a; }");
    assertOk("func x { a(1, 2, 3); }");
    assertOk("func x { a()[123]; }");
    assertOk("func x { a[123]; }");
    assertOk("func x { a[123][321]; }");
    assertOk("func x { a++++; }");
    assertOk("func x { a++[123]; }");
    assertOk("func x { a.b; }");
    assertOk("func x { a.b(); }");
    assertOk("func x { a.b[123]; }");
    assertOk("func x { a().b; }");
    assertOk("func x { a().b(); }");
    assertErr("func x { a()(); }", "function name");
    assertErr("func x { 'a'(); }", "function name");
    assertErr("func x { a[]; }", "Unexpected ]");
    assertErr("func x { a(; }", "Expected )");
    assertErr("func x { a); }", "Expected ;");

    // Parenthesized
    assertErr("func x { ((a + b) & (c + d)); }", "");

    // Stress-tests
    assertOk("func x{ x + 5 ? a : b(!4, -3, 5 || a || x2 % c(65) && 123 - (a[b + c])); }");
  }

  @Test
  public void testLiterals() {
    assertOk("func x{ \"hej\"; }");
    assertOk("func x{ \"hej\\n\"; }");
    assertOk("func x{ \"\\\"\"; }");
    assertOk("func x{ \"\\n\\r\\t\\\"\\\\\\' \"; }");
    assertOk("func x{ 'a'; }");
    assertOk("func x{ '\\\\'; }");
    assertOk("func x{ '\\''; }");
    assertOk("func x{ 2147483647; }");
    assertOk("func x{ -2147483648; }");
    assertOk("func x{ true; }");
    assertOk("func x{ false; }");

    assertErr("func x{ \"hej\\\"; }", "Unterminated");
    assertErr("func x{ \"; }", "Unterminated");
    assertErr("func x{ '\\'; }", "Unterminated");
    assertErr("func x{ '; }", "Unterminated");
    assertErr("func x{ \"\\!\"; }", "escape");
    assertErr("func x{ '\\!'; }", "escape");
    assertErr("func x{ 2147483648; }", "range");
    assertErr("func x{ -2147483649; }", "range");
  }

  @Test
  public void testStructs() {
    assertOk("struct s {}");
    assertOk("struct s {x: Int;}");
    assertOk("struct s {x: Int; y: Int;}");
    assertErr("struct s {x: Int a;}", "Expected ;");
    assertErr("struct s {x: Int}", "Expected ;");
    assertErr("struct s {x: Int;", "Expected }");
    assertErr("struct {x: Int;}", "Expected struct name");
    assertErr("struct s", "Expected {");
  }

  @Test
  public void testInits() {
    assertOk("func x{ x: Int = 0; }");
    assertErr("func x{ x: Int = ; }", "Unexpected ;");
    assertErr("func x{ x: Int = 0 }", "Expected ;");
  }

  @Test
  public void testStructInits() {
    // Structs
    assertOk("func x{ x: Int = {}; }");
    assertOk("func x{ x: Int = {x: y}; }");
    assertOk("func x{ x: Int = {x: y, z: w}; }");
    assertOk("func x{ x: Int = default; }");
    assertOk("struct hej{ fisk: Int[1]; } func blah { x: hej = {fisk: [5]}; }");

    assertErr("func x{ x = default; }", "Unexpected default");
    assertErr("func x{ x = {}; }", "Unexpected {");
    assertErr("func x{ x: Int = {x}; }", "Expected :");
    assertErr("func x{ x: Int = {x:}; }", "Unexpected }");
    assertErr("func x{ x: Int = {:y}; }", "Expected }");
    assertErr("func x{ x: Int = {x:y w:z}; }", "Expected }");
  }

  @Test
  public void testArrayInits() {
    assertOk("func x{ x: Int[] = []; }");
    assertOk("func x{ x: Int[5] = [...]; }");
    assertOk("func x{ x: Int[5] = default; }");
    assertOk("func x{ x: Int[5] = [x, {blah: hej}, ...]; }");
    assertOk("func x{ x: Int[5] = [x, ...default]; }");
    assertOk("func x{ x: Int[7][8] = [[...]]; }");
    assertOk("func x{ x: Int[7][8] = [[...]]; }");
    assertOk("func x{ x: Int[9][11] = [[2, ...default],  [1, ...], ...]; }");
    assertOk("func x{ x: Int[15][12] = [[...],  [1, {}, ...], ...default]; }");

    assertErr("func x{ x: Int[5] = {default...}; }", "Expected }");
    assertErr("func x{ x: Int[5] = {{...}}; }", "Expected }");
  }

  @Test
  public void testFailing() {
    assertErr("func main { for (i: Int = 0; i << 5; i++) ; }", "Unexpected <");
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

  @Test
  public void testExamplePrograms() throws IOException {
    TestCases cases =
        TextFormat.parse(
            Files.readString(Paths.get("test_programs/tests.textproto"), StandardCharsets.UTF_8),
            TestCases.class);
    for (TestCase tc : cases.getTestCaseList()) {
      Errors parseErr = new Errors();
      Ast.Program parsed =
          Parser.parse(
              Tokenizer.create(
                  Files.readString(
                      Paths.get("test_programs/sources/" + tc.getName()), StandardCharsets.UTF_8)),
              parseErr);

      Ast.Program expected =
          TextFormat.parse(
              Files.readString(
                  Paths.get("test_programs/parse_trees/" + tc.getName() + ".parsetree"),
                  StandardCharsets.UTF_8),
              Ast.Program.class);
      ProtoTruth.assertThat(parsed).isEqualTo(expected);
      if (tc.getFailure() == FailureMode.PARSING) {
        Truth.assertWithMessage(tc.getName() + " parsing errors")
            .that(parseErr.errors())
            .isNotEmpty();
      } else {
        Truth.assertWithMessage(tc.getName() + " parsing errors").that(parseErr.errors()).isEmpty();
      }
    }
  }
}
