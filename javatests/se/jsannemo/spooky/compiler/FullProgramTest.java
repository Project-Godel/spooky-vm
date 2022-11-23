package se.jsannemo.spooky.compiler;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.codegen.CodeGen;
import se.jsannemo.spooky.compiler.ir.IrProgram;
import se.jsannemo.spooky.compiler.ir.ToIr;
import se.jsannemo.spooky.compiler.parser.Parser;
import se.jsannemo.spooky.compiler.parser.Tokenizer;
import se.jsannemo.spooky.vm.SpookyVm;
import se.jsannemo.spooky.vm.VmException;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.ExecutableParser;
import se.jsannemo.spooky.vm.code.InstructionException;
import se.jsannemo.spooky.vm.code.Instructions;

public class FullProgramTest {

  @Test
  public void testPrograms() throws VmException {
    assertThat(runProgram("binary.spooky")).isEqualTo("1\n111\n0101\n");
    assertThat(runProgram("callafterprint.spooky")).isEqualTo("A");
    assertThat(runProgram("fizzbuzz.spooky")).isEqualTo("0010210012010012");
    assertThat(runProgram("globalassign.spooky")).isEqualTo("4210");
    assertThat(runProgram("globals_calling_functions.spooky")).isEqualTo("42");
    assertThat(runProgram("globals_stack.spooky")).isEqualTo("3");
    assertThat(runProgram("helloworld.spooky")).isEqualTo("Hello World!");
    assertThat(runProgram("isprime.spooky"))
        .isEqualTo("90 0\n91 0\n92 0\n93 0\n94 0\n95 0\n96 0\n97 1\n98 0\n99 0\n");
    assertThat(runProgram("shortcircuit.spooky")).isEqualTo("342");
    assertThat(runProgram("ternary.spooky")).isEqualTo("-8-9-8-9-8-9-8-9-8-9");
    assertThat(runProgram("printfun.spooky")).isEqualTo("1-23");
    assertThat(runProgram("printglobal.spooky")).isEqualTo("1");
    assertThat(runProgram("printliteral.spooky")).isEqualTo("42");
  }

  private static String runProgram(String name) throws VmException {
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(Paths.get("test_programs", "sources", name));
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read " + name);
    }

    Errors errs = new Errors();
    Tokenizer tokenizer = Tokenizer.create(new String(bytes, StandardCharsets.UTF_8));
    Program program = Parser.parse(tokenizer, errs);
    assertThat(errs.errors()).isEmpty();
    IrProgram irProgram = ToIr.generate(program, errs);
    assertThat(errs.errors()).isEmpty();
    List<Instructions.Instruction> codegen = CodeGen.codegen(name, irProgram);
    Executable executable;
    try {
      executable = ExecutableParser.fromInstructions(codegen);
    } catch (InstructionException e) {
      throw new RuntimeException("Invalid codegen instructions???");
    }

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    SpookyVm vm =
        SpookyVm.newBuilder(executable)
            .addStdLib()
            .setStdOut(new PrintStream(bos))
            .setMemorySize(1000)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    return bos.toString();
  }
}
