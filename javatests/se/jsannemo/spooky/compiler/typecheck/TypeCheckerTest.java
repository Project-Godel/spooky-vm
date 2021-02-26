package se.jsannemo.spooky.compiler.typecheck;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;

public class TypeCheckerTest {

  @Test
  public void testStructs() {
    assertOk("struct a{} struct b{} func main{}");
    assertOk("struct a{ x: b; } struct b{ y: Int; z: String; } func main{}");
    assertOk("struct a{} struct b{ x: a; } func main{}");
    assertOk("struct a{} struct b{ x: a[5]; } func main{}");

    assertErr("struct Int{} func main{}", "builtin");
    assertErr("struct a{} struct a{} func main{}", "already defined");
    assertErr("struct a{ x: blah; } func main{}", "Unknown type");
    assertErr("struct a{ x: b; } struct b{ x: a;} func main{}", "cyclic dependency");
    assertErr("struct a{ x: a; } func main{}", "cyclic dependency");
    assertErr("struct a{ x: Int[]; } func main{}", "inferred");
    assertErr("struct a{ x: Int[1000000][1000000]; } func main{}", "too large");
  }

  @Test
  public void testExterns() {
    assertOk("extern blah func main{}");
    assertOk("extern blah -> Int func main{}");
    assertOk("extern blah(x: Int) func main{}");
    assertOk("extern blah(x: String) -> String func main{}");
    assertOk("struct blah{} extern blah(x: blah) func main{}");
    assertOk("extern blah -> Int[2] func main{}");
    assertOk("extern blah(x: Int[2]) func main{}");

    assertErr("extern blah extern blah func main{}", "already defined");
    assertErr("extern blah -> invalid func main{}", "Unknown type");
    assertErr("extern blah(x: invalid) func main{}", "Unknown type");
  }

  @Test
  public void testFunctions() {
    assertOk("func main {}");
    assertOk("func blah(x: Int) {} func main{}");
    assertOk("func blah(x: String) -> String { return \"\"; } func main{}");
    assertOk("func blah(x: Int) -> Int { return 0; } func main{}");
    assertOk("func blah(x: Int[2]) {} func main{}");
    assertOk("func blah -> Int[2] { x: Int[2] = [...]; return x; } func main{}");

    assertErr("extern blah extern blah func main {}", "already defined");
    assertErr("func blah -> invalid {} func main{}", "Unknown type");
    assertErr("func blah(x: invalid) {} func main{}", "Unknown type");
    assertErr("", "main()");
    assertErr("func main -> Int {}", "main()");
    assertErr("func main(x: Int) {}", "main()");
  }

  @Test
  public void testGlobals() {
    assertOk("x: Int = 0; func main{}");
    assertOk("x: String = \"hej\"; func main{}");
    assertOk("x: Int[2] = default; func main{}");
    assertOk("x: Int = 0; y: Int = x; func main{}");
    assertOk("func hej -> Int { return 0; } x: Int = hej(); func main{}");
    assertOk("x: blah = default; struct blah {} func main{}");
    assertOk("struct hej{ fisk: Int; } x: hej[] = [{fisk: 1}]; func main{}");
    assertOk("x: Int[2] = [...]; y: Int[2] = x; func main{}");

    assertErr("x: Int = 0; x: Int = 0; func main{}", "already exists");
    assertErr("x: Int = x; y: Int = 0; func main{}", "No such variable");
    assertErr("x: blah = 0; struct blah {} func main{}", "not assignable");
  }

  @Test
  public void testReturns() {
    assertOk("func main { return; }");
    assertOk("func blah -> Int { return 1; } func main{}");
    assertOk("func blah -> Int { if (true) return 1; } func main{}");
    assertOk("func blah -> Int { if (true) return 1; return 2; } func main{}");
    assertOk("func blah -> Int { if (false) return 1; else return 2; } func main{}");
    assertOk("func blah -> Int { if (true) return 1; else return 2; return 3;} func main{}");
    assertOk("func blah -> String { return \"\"; } func main{}");
    assertOk("struct blah{} func blah -> blah { x: blah = default; return x; } func main{}");
    assertOk("func blah -> Int[2] { x: Int[2] = [...]; return x; } func main{}");

    assertErr("func blah { return 1; } func main{}", "Return with value in void function");
    assertErr("func blah -> Int { return; } func main{}", "Return missing value");
    assertErr("func blah -> Int { } func main{}", "Function does not return");
  }

  @Test
  public void testConditional() {
    assertOk("func main { x: Int = 5; if (true) x = 1; }");
    assertOk("func main { if (1 == 2) {1 + 1;} }");
    assertOk("func main { x: Int = 5; if (true) x = 1; else { x = 7; } }");
    assertOk("func main { if (true) 1; else 2; }");
    assertOk("func main { if (true) 1; else if (true) 2; }");
    assertOk("func main { if (true) 1; else if (true) 2; else 3; }");

    assertErr("func main { if (1) 1; }", "Expected type Boolean");
    assertErr("func main { if (true) x: Int = 1; x = 5; }", "No such variable");
    assertErr("func main { if (true) x = 1; }", "No such variable");
    assertErr("func main { if (true) 1; else x = 1; }", "No such variable");
    assertErr("func main { if (true) 1; else { x: Int = 1; } x = 5; }", "No such variable");
  }

  @Test
  public void testLoop() {
    assertOk("func main { x: Int = 5; for(y: Int = x; x > y; y = y + x) y = x; }");
    assertOk("func main { for(y: Int = 1;; y = y + 1) y; }");
    assertOk("func main { for(y: Int = 1;y < 10;) y; }");
    assertOk("func main { for(;;) 1; }");

    assertErr("func main { for(;;) y; }", "No such variable");
    assertErr("func main { for(y: Int = 1;;) y; y; }", "No such variable");
    assertErr("func main { for(y: Int = 1;;) x: Int = 1; x; }", "No such variable");
    assertErr("func main { for(; 6;) 1; }", "Expected type Boolean");
  }

  @Test
  public void testDecl() {
    assertOk("func main { x: Int = 1; }");
    assertOk("func blah -> Int { x: Int = 1; return x; } func main{}");
    assertOk("struct hej{} func main { x: hej = {}; }");
    assertOk("struct hej{} func main { x: hej = default; }");
    assertOk("struct hej{ fisk: Int; } func main { x: hej = {fisk: 1}; }");
    assertOk("struct hej{ fisk: Int[1]; } func main { x: hej = {fisk: [5]}; }");
    assertOk("func main { x: Int[5] = default; }");
    assertOk("func main { x: Int[5] = [...]; }");
    assertOk("func main { x: Int[5][5] = [...]; }");
    assertOk("func main { x: Int[5][5] = [...[1, 2, 3, 4, 5]]; }");
    assertOk("func main { x: Int[5][5] = [default, default, ...[1, 2, 3, 4, 5]]; }");
    assertOk("struct hej{ fisk: Int; } func main { x: hej[] = [{fisk: 1}]; }");
    assertOk("struct hej{} func main { x: hej = default; y: hej = x; }");
    assertOk("func main { x: Int[2][] = [[1, 2], [1, ...]]; }");
    assertOk("func main { x: String = \"derp\"; }");
    assertOk("func main { x: Int[5] = [...]; y: Int[5] = x; }");

    assertErr("func main { x: Int[1000000][1000000] = default; }", "too large");
    assertErr("func main { x: Int[] = [...]; }", "Could not infer");
    assertErr("func main { x: Int[2][] = [[1, 2], [1, 2, 3]]; }", "Expected 2 values");
    assertErr("func main { x: Int[2][] = [[1, ...], [1, 2, ...]]; }", "Could not infer");
    assertErr("func main { x: Int[2][] = [[1, 2, 3, ...], [1, 2]]; }", "Expected at most 2 values");
    assertErr("struct hej{ fisk: Int; } func main { x: hej = {fisk: true}; }", "not assignable");
    assertErr(
        "struct hej{ fisk: Int[1]; } func main { x: hej = {fisk: []}; }", "Expected 1 values");
    assertErr("func main { x: Int[5] = []; }", "Expected 5 values");
    assertErr("func main { x: Int[5][5] = [...[1, 2, 3, 4]]; }", "Expected 5 values");
  }

  @Test
  public void testAssignment() {
    assertOk("struct hej{} func main { x: hej = {}; y: hej = x; }");
    assertOk("func main { x: Int = 0; y: Int = x; }");
    assertOk("func main { x: Int = 0; x += 1; }");
    assertOk("func main { x: Int = 0; x -= 1; }");
    assertOk("func main { x: Int = 0; x = x + 1; }");
    assertOk("func main { x: Int = 0; y: Int = x = x + 1; }");
    assertOk("func blah(x: Int) { x = x + 1; } func main{}");
    assertOk("func main { x: String = \"derp\"; y: String = x; }");
    assertOk("func main { x: Int[5] = [...]; y: Int[5] = x; }");

    assertErr("func main { x: Int = 5; y: Boolean = x; }", "not assignable");
    assertErr("func main { x: Int = 0; x -= true; }", "not assignable");
    assertErr("func main { x: Boolean = true; x -= 1; }", "not assignable");
    assertErr("func main { 1 = 1; }", "not lvalue");
  }

  @Test
  public void testBinary() {
    assertOk("func main { x: Boolean = 5 > 4; }");
    assertOk("func main { x: Boolean = false || true && (1 <= 2); }");
    assertOk("func main { x: Boolean = 4 == 3; }");
    assertOk("func main { x: Int = 5 - 4 * 4 + 1 / 3 % 10; }");

    assertErr("func main { x: Int = 5 - 4 * 4 + 1 / 3 % true; }", "Expected type Int");
    assertErr("func main { x: Boolean = false || 4 && (1 <= 2); }", "Expected type Boolean");
  }

  @Test
  public void testTernary() {
    assertOk("func main { x: Int = 5 < 3 ? 2 : 1; }");
    assertOk("func main { x: Int = 5 < 3 ? 2 : 1 > 2 ? 5 : 1; }");

    assertErr("func main { x: Int = 5 < 3 ? 2 : 1 > 2 ? true : 1; }", "incompatible type");
    assertErr("func main { x: Int = 5 < 3 ? true : false; }", "not assignable");
    assertErr("func main { x: Int = 5 ? 1 : 2; }", "Expected type Boolean");
  }

  @Test
  public void testUnary() {
    assertOk("func main { x: Int = -5; }");
    assertOk("func main { x: Int[2] = [...]; x[0]--; }");
    assertOk("func main { x: Int = -5; y: Int = -x; }");
    assertOk("func main { x: Boolean = !true; }");
    assertOk("func main { x: Int = 5; y: Int = --x + x-- + ++x + x++; }");

    assertErr("func main { x: Int = --5; }", "Expected lvalue");
    assertErr("func main { x: Int = 5; --(--x); }", "Expected lvalue");
    assertErr("func main { x: Boolean = false; --x; x--; ++x; x++; }", "Expected type Int");
  }

  @Test
  public void testSelect() {
    assertOk(
        "struct blah{tjo: hej; }struct hej{fisk: Int;} func main { x: blah = {tjo: {fisk: 1}}; y: Int = x.tjo.fisk; }");
    assertOk("struct hej{ fisk: Int; } func main { x: hej = {fisk: 1}; y: Int = x.fisk; }");
    assertOk("struct hej{ fisk: Int; } func main { x: hej = {fisk: 1}; x.fisk = 1; }");

    assertErr(
        "struct hej{ fisk: Int; } func main { x: hej = {fisk: 1}; x.fisk = true; }",
        "not assignable");
    assertErr(
        "struct hej{ fisk: Int; } func main { x: hej = {fisk: 1}; y: Boolean = x.fisk; }",
        "not assignable");
  }

  @Test
  public void testArrayIndex() {
    assertOk("func main { x: Int[5] = [...]; x[3] = 1; }");
    assertOk("func main { x: Int[5] = [...]; x[3] = x[2]; }");
    assertOk("func main { x: Int[5] = [...]; x[3] += 1; }");
    assertOk("func main { x: Int[5] = [...]; y: Int = x[1]; }");

    assertErr("func main { x: Int[5] = [...]; x[3] = true; }", "not assignable");
    assertErr("func main { x: Int[5] = [...]; y: Boolean = x[1]; }", "not assignable");
  }

  @Test
  public void testCalls() {
    assertOk("func hej {} func main { hej(); }");
    assertOk("func hej -> Int { return 0; } func main { x: Int = hej(); }");
    assertOk("func hej(x: Int){} func main { x: Int = 0; hej(x); }");
    assertOk("func hej(x: Int){} func main { hej(1); }");

    assertErr("func hej {} func main { heja(); }", "not found");
    assertErr("func hej -> Int { return 0; } func main { x: Boolean = hej(); }", "not assignable");
    assertErr("func hej(x: Int) { } func main { x: Boolean = true; hej(x); }", "not assignable");
  }

  private static void assertOk(String s) {
    Ast.Program parse = Parser.parse(Tokenizer.create(s), new Errors());
    assertThat(parse.getValid()).isTrue();
    Errors tcErrors = new Errors();
    TypeChecker.typeCheck(parse, tcErrors);
    assertThat(tcErrors.errors()).isEmpty();
  }

  private static void assertErr(String s, String msg) {
    Ast.Program parse = Parser.parse(Tokenizer.create(s), new Errors());
    assertThat(parse.getValid()).isTrue();
    Errors tcErrors = new Errors();
    TypeChecker.typeCheck(parse, tcErrors);
    for (Errors.Error e : tcErrors.errors()) {
      if (e.toString().contains(msg)) {
        return;
      }
    }
    Assert.fail("Found no matching error " + msg + " in " + tcErrors.errors());
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
      if (!parseErr.errors().isEmpty()) {
        Truth.assertThat(tc.getFailure()).isNotEqualTo(FailureMode.VALIDATION);
        continue;
      }
      TypeChecker.typeCheck(parsed, parseErr);
      if (tc.getFailure() == FailureMode.VALIDATION) {
        Truth.assertWithMessage(tc.getName() + " validation errors")
            .that(parseErr.errors())
            .isNotEmpty();
      } else {
        Truth.assertWithMessage(tc.getName() + " validation errors")
            .that(parseErr.errors())
            .isEmpty();
      }
    }
  }
}
