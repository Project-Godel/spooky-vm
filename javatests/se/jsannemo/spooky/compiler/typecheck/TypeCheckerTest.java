package se.jsannemo.spooky.compiler.typecheck;

import com.google.common.truth.Truth;
import com.google.protobuf.TextFormat;
import org.junit.Assert;
import org.junit.Test;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Ast;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.compiler.testing.FailureMode;
import se.jsannemo.spooky.compiler.testing.TestCase;
import se.jsannemo.spooky.compiler.testing.TestCases;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;

public class TypeCheckerTest {

  @Test
  public void testStructs() {
    assertOk("struct a{} struct b{}");
    assertOk("struct a{ x: b; } struct b{ y: Int; }");
    assertOk("struct a{} struct b{ x: a; }");

    assertErr("struct Int{}", "builtin");
    assertErr("struct a{} struct a{}", "already defined");
    assertErr("struct a{ x: blah; }", "Unknown type");
    assertErr("struct a{ x: b; } struct b{ x: a;}", "cyclic dependency");
    assertErr("struct a{ x: a; }", "cyclic dependency");
  }

  @Test
  public void testExterns() {
    assertOk("extern blah");
    assertOk("extern blah -> Int");
    assertOk("extern blah(x: Int)");
    assertOk("struct blah{} extern blah(x: blah)");

    assertErr("extern blah extern blah", "already defined");
    assertErr("extern blah -> invalid", "Unknown type");
    assertErr("extern blah(x: invalid)", "Unknown type");
  }

  @Test
  public void testFunctions() {
    assertOk("func blah {}");
    assertOk("func blah(x: Int) {}");
    assertOk("func blah(x: Int) -> Int { return 0; }");

    assertErr("extern blah func blah {}", "already defined");
    assertErr("func blah -> invalid {}", "Unknown type");
    assertErr("func blah(x: invalid) {}", "Unknown type");
  }

  @Test
  public void testGlobals() {
    assertOk("x: Int = 0;");
    assertOk("x: Int[2] = default;");
    assertOk("x: Int = 0; y: Int = x;");
    assertOk("func hej -> Int { return 0; } x: Int = hej();");
    assertOk("x: blah = default; struct blah {}");
    assertOk("struct hej{ fisk: Int; } x: hej[] = [{fisk: 1}];");

    assertErr("x: Int = 0; x: Int = 0;", "already exists");
    assertErr("x: Int = x; y: Int = 0;", "No such variable");
    assertErr("x: blah = 0; struct blah {}", "not assignable");
  }

  @Test
  public void testReturns() {
    assertOk("func blah { return; }");
    assertOk("func blah -> Int { return 1; }");
    assertOk("func blah -> Int { if (true) return 1; }");
    assertOk("func blah -> Int { if (true) return 1; return 2; }");
    assertOk("func blah -> Int { if (false) return 1; else return 2; }");
    assertOk("func blah -> Int { if (true) return 1; else return 2; return 3;}");

    assertErr("func blah { return 1; }", "Return with value in void function");
    assertErr("func blah -> Int { return; }", "Return missing value");
    assertErr("func blah -> Int { }", "Function does not return");
    assertErr("func blah -> Int[2] { x: Int[2] = [...]; return x; }", "not assignable");
  }

  @Test
  public void testConditional() {
    assertOk("func blah { x: Int = 5; if (true) x = 1; }");
    assertOk("func blah { if (1 == 2) {1 + 1;} }");
    assertOk("func blah { x: Int = 5; if (true) x = 1; else { x = 7; } }");
    assertOk("func blah { if (true) 1; else 2; }");
    assertOk("func blah { if (true) 1; else if (true) 2; }");
    assertOk("func blah { if (true) 1; else if (true) 2; else 3; }");

    assertErr("func blah { if (1) 1; }", "Expected type Boolean");
    assertErr("func blah { if (true) x: Int = 1; x = 5; }", "No such variable");
    assertErr("func blah { if (true) x = 1; }", "No such variable");
    assertErr("func blah { if (true) 1; else x = 1; }", "No such variable");
    assertErr("func blah { if (true) 1; else { x: Int = 1; } x = 5; }", "No such variable");
  }

  @Test
  public void testLoop() {
    assertOk("func blah { x: Int = 5; for(y: Int = x; x > y; y = y + x) y = x; }");
    assertOk("func blah { for(y: Int = 1;; y = y + 1) y; }");
    assertOk("func blah { for(y: Int = 1;y < 10;) y; }");
    assertOk("func blah { for(;;) 1; }");

    assertErr("func blah { for(;;) y; }", "No such variable");
    assertErr("func blah { for(y: Int = 1;;) y; y; }", "No such variable");
    assertErr("func blah { for(y: Int = 1;;) x: Int = 1; x; }", "No such variable");
    assertErr("func blah { for(; 6;) 1; }", "Expected type Boolean");
  }

  @Test
  public void testDecl() {
    assertOk("func blah { x: Int = 1; }");
    assertOk("func blah -> Int { x: Int = 1; return x; }");
    assertOk("struct hej{} func blah { x: hej = {}; }");
    assertOk("struct hej{} func blah { x: hej = default; }");
    assertOk("struct hej{ fisk: Int; } func blah { x: hej = {fisk: 1}; }");
    assertOk("struct hej{ fisk: Int[1]; } func blah { x: hej = {fisk: [5]}; }");
    assertOk("func blah { x: Int[5] = default; }");
    assertOk("func blah { x: Int[5] = [...]; }");
    assertOk("func blah { x: Int[5][5] = [...]; }");
    assertOk("func blah { x: Int[5][5] = [...[1, 2, 3, 4, 5]]; }");
    assertOk("func blah { x: Int[5][5] = [default, default, ...[1, 2, 3, 4, 5]]; }");
    assertOk("struct hej{ fisk: Int; } func blah { x: hej[] = [{fisk: 1}]; }");
    assertOk("struct hej{} func blah { x: hej = default; y: hej = x; }");

    assertErr("struct hej{ fisk: Int; } func blah { x: hej = {fisk: true}; }", "not assignable");
    assertErr(
        "struct hej{ fisk: Int[1]; } func blah { x: hej = {fisk: []}; }", "Expected 1 values");
    assertErr("func blah { x: Int[5] = []; }", "Expected 5 values");
    assertErr("func blah { x: Int[5][5] = [...[1, 2, 3, 4]]; }", "Expected 5 values");
    assertErr("func blah { x: Int[5] = [...]; y: Int[5] = x; }", "not assignable");
  }

  @Test
  public void testAssignment() {
    assertOk("struct hej{} func blah { x: hej = {}; y: hej = x; }");
    assertOk("func blah { x: Int = 0; y: Int = x; }");
    assertOk("func blah { x: Int = 0; x += 1; }");
    assertOk("func blah { x: Int = 0; x -= 1; }");
    assertOk("func blah { x: Int = 0; x = x + 1; }");
    assertOk("func blah { x: Int = 0; y: Int = x = x + 1; }");
    assertOk("func blah(x: Int) { x = x + 1; }");

    assertErr("func blah { x: Int[5] = [...]; y: Int[5] = x; }", "not assignable");
    assertErr("func blah { x: Int = 5; y: Boolean = x; }", "not assignable");
    assertErr("func blah { x: Int = 0; x -= true; }", "not assignable");
    assertErr("func blah { x: Boolean = true; x -= 1; }", "not assignable");
    assertErr("func blah { 1 = 1; }", "not lvalue");
  }

  @Test
  public void testBinary() {
    assertOk("func blah { x: Boolean = 5 > 4; }");
    assertOk("func blah { x: Boolean = false || true && (1 <= 2); }");
    assertOk("func blah { x: Boolean = 4 == 3; }");
    assertOk("func blah { x: Int = 5 - 4 * 4 + 1 / 3 % 10; }");

    assertErr("func blah { x: Int = 5 - 4 * 4 + 1 / 3 % true; }", "Expected type Int");
    assertErr("func blah { x: Boolean = false || 4 && (1 <= 2); }", "Expected type Boolean");
  }

  @Test
  public void testTernary() {
    assertOk("func blah { x: Int = 5 < 3 ? 2 : 1; }");
    assertOk("func blah { x: Int = 5 < 3 ? 2 : 1 > 2 ? 5 : 1; }");

    assertErr("func blah { x: Int = 5 < 3 ? 2 : 1 > 2 ? true : 1; }", "incompatible type");
    assertErr("func blah { x: Int = 5 < 3 ? true : false; }", "not assignable");
    assertErr("func blah { x: Int = 5 ? 1 : 2; }", "Expected type Boolean");
  }

  @Test
  public void testUnary() {
    assertOk("func blah { x: Int = -5; }");
    assertOk("func blah { x: Int[2] = [...]; x[0]--; }");
    assertOk("func blah { x: Int = -5; y: Int = -x; }");
    assertOk("func blah { x: Boolean = !true; }");
    assertOk("func blah { x: Int = 5; y: Int = --x + x-- + ++x + x++; }");

    assertErr("func blah { x: Int = --5; }", "Expected lvalue");
    assertErr("func blah { x: Int = 5; --(--x); }", "Expected lvalue");
    assertErr("func blah { x: Boolean = false; --x; x--; ++x; x++; }", "Expected type Int");
  }

  @Test
  public void testSelect() {
    assertOk(
        "struct blah{tjo: hej; }struct hej{fisk: Int;} func blah { x: blah = {tjo: {fisk: 1}}; y: Int = x.tjo.fisk; }");
    assertOk("struct hej{ fisk: Int; } func blah { x: hej = {fisk: 1}; y: Int = x.fisk; }");
    assertOk("struct hej{ fisk: Int; } func blah { x: hej = {fisk: 1}; x.fisk = 1; }");

    assertErr(
        "struct hej{ fisk: Int; } func blah { x: hej = {fisk: 1}; x.fisk = true; }",
        "not assignable");
    assertErr(
        "struct hej{ fisk: Int; } func blah { x: hej = {fisk: 1}; y: Boolean = x.fisk; }",
        "not assignable");
  }

  @Test
  public void testArrayIndex() {
    assertOk("func blah { x: Int[5] = [...]; x[3] = 1; }");
    assertOk("func blah { x: Int[5] = [...]; x[3] = x[2]; }");
    assertOk("func blah { x: Int[5] = [...]; x[3] += 1; }");
    assertOk("func blah { x: Int[5] = [...]; y: Int = x[1]; }");

    assertErr("func blah { x: Int[5] = [...]; x[3] = true; }", "not assignable");
    assertErr("func blah { x: Int[5] = [...]; y: Boolean = x[1]; }", "not assignable");
  }

  @Test
  public void testCalls() {}

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
