package se.jsannemo.spooky;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import se.jsannemo.spooky.compiler.Errors;
import se.jsannemo.spooky.compiler.ast.Program;
import se.jsannemo.spooky.compiler.codegen.Assembler;
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

public class CLI {
  public static void main(String... args) {
    if (args.length == 0) {
      usage();
      return;
    }
    if ("compile".equals(args[0])) {
      compile(args);
    } else if ("run".equals(args[0])) {
      run(args);
    } else {
      usage();
    }
  }

  private static void compile(String... args) {
    if (args.length != 3) {
      System.err.println("usage: spooky compile input.spooky output.spook");
      return;
    }
    String source;
    try {
      source = Files.readString(Path.of(args[1]));
    } catch (IOException e) {
      System.err.println("Could not read " + args[1] + ": " + e.getMessage());
      return;
    }
    Tokenizer tokenizer = Tokenizer.create(source);
    Errors errorReporter = new Errors();
    Program parse = Parser.parse(tokenizer, errorReporter);
    IrProgram irProgram = ToIr.generate(parse, errorReporter);
    ImmutableList<Errors.Error> errors = errorReporter.errors();
    if (!errors.isEmpty()) {
      errors.forEach(e -> System.err.println(e.toString()));
      return;
    }
    List<Instructions.Instruction> instructions = CodeGen.codegen(args[2], irProgram);
    byte[] execBytes = Assembler.assemble(instructions);
    try {
      Files.write(
          Path.of(args[2]),
          execBytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      System.err.println("Could not write executable: " + e.getMessage());
    }
  }

  private static void run(String... args) {
    if (args.length != 2) {
      System.err.println("usage: spooky run exec.spook");
      return;
    }
    byte[] execBytes;
    try {
      execBytes = Files.readAllBytes(Path.of(args[1]));
    } catch (IOException e) {
      System.err.println("Could not read " + args[1] + ": " + e.getMessage());
      return;
    }
    Executable executable;
    try {
      executable = ExecutableParser.fromBinary(execBytes);
    } catch (InstructionException e) {
      System.err.println("Malformatted executable: " + e.getMessage());
      return;
    }
    SpookyVm vm = SpookyVm.newBuilder(executable).addStdLib().setMemorySize(1000).build();
    while (true) {
      try {
        if (!vm.executeInstruction()) break;
      } catch (VmException e) {
        System.err.println("Run-time error: " + e.getMessage());
      }
    }
  }

  private static void usage() {
    System.err.println("usage: spooky compile/run ...");
  }
}
