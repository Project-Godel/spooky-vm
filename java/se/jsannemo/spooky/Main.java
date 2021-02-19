package se.jsannemo.spooky;

import se.jsannemo.spooky.compiler.Compiler;
import se.jsannemo.spooky.compiler.ValidationException;
import se.jsannemo.spooky.vm.Executable;
import se.jsannemo.spooky.vm.SpookyVm;
import se.jsannemo.spooky.vm.VmException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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
      default:
        System.err.println("Unknown command: " + args[0]);
    }
  }

  private static void run(String[] args) throws VmException, IOException {
    if (args.length < 2) {
      System.err.println("Usage: run <BINARY>");
      return;
    }
    Executable exec = Executable.parseFrom(new FileInputStream(args[1]));
    SpookyVm vm = SpookyVm.newBuilder(exec).addStdLib(System.out).setMemorySize(1000).build();
    while (vm.executeInstruction())
      ;
  }

  private static void compile(String[] args) throws IOException, ValidationException {
    if (args.length < 3) {
      System.err.println("Usage: compile <INFILE> <OUTFILE>");
      return;
    }
    byte[] code = Compiler.compile(Files.readString(Paths.get(args[1])));
    Files.write(
        Path.of(args[2]), code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
