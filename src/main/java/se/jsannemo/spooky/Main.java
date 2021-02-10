package se.jsannemo.spooky;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import se.jsannemo.spooky.compiler.Compiler;
import se.jsannemo.spooky.compiler.ParseException;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.lsp.LanguageServerInit;
import se.jsannemo.spooky.vm.SpookyVm;
import se.jsannemo.spooky.vm.VmException;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.ExecutableParser;
import se.jsannemo.spooky.vm.code.InstructionException;

public final class Main {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("No command provided");
      return;
    }
    switch (args[0]) {
      case "compile":
        compile(args);
        break;
      case "run":
        run(args);
        break;
      case "lsp":
        lsp(args);
        break;
      default:
        System.err.println("Unknown command: " + args[0]);
    }
  }

  private static void lsp(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: lsp <PORT>");
      return;
    }
    int port = Integer.parseInt(args[1]);
    LanguageServerInit.run(port);
  }

  private static void run(String[] args) throws IOException, InstructionException, VmException {
    if (args.length < 2) {
      System.err.println("Usage: run <BINARY>");
      return;
    }
    Executable exec = ExecutableParser.fromFile(args[1]);
    SpookyVm vm = SpookyVm.newBuilder(exec).addStdLib().setMemorySize(1000).build();
    while (vm.executeInstruction())
      ;
  }

  private static void compile(String[] args)
      throws IOException, ParseException, ValidationException {
    if (args.length < 3) {
      System.err.println("Usage: compile <INFILE> <OUTFILE>");
      return;
    }
    byte[] code = Compiler.compile(new FileInputStream(args[1]));
    Files.write(Path.of(args[2]), code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
