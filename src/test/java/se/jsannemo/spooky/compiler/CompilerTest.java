package se.jsannemo.spooky.compiler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.SpookyVm;
import se.jsannemo.spooky.vm.VmException;
import se.jsannemo.spooky.vm.code.ExecutableParser;
import se.jsannemo.spooky.vm.code.InstructionException;

final class CompilerTest {

  @Test
  void testHelloWorld()
      throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/helloworld.spooky");
    byte[] result = Compiler.compile(programStream);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream pw = new PrintStream(output);
    SpookyVm vm =
        SpookyVm.newBuilder(ExecutableParser.fromBinary(result))
            .addStdLib()
            .setMemorySize(1000)
            .setStdOut(pw)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    Assertions.assertEquals("Hello World!", output.toString());
  }

  @Test
  void testIsPrime() throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/isprime.spooky");
    byte[] result = Compiler.compile(programStream);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream pw = new PrintStream(output);
    SpookyVm vm =
        SpookyVm.newBuilder(ExecutableParser.fromBinary(result))
            .addStdLib()
            .setMemorySize(1000)
            .setStdOut(pw)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    Assertions.assertEquals("90 0\n91 0\n92 0\n93 0\n94 0\n95 0\n96 0\n97 1\n98 0\n99 0\n", output.toString());
  }

  @Test
  void testCarl() {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/carl.spooky");
    Assertions.assertThrows(ValidationException.class, () -> Compiler.compile(programStream));
  }

  @Test
  void testCarl2() throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/carl2.spooky");

    byte[] result = Compiler.compile(programStream);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream pw = new PrintStream(output);
    SpookyVm vm =
        SpookyVm.newBuilder(ExecutableParser.fromBinary(result))
            .addStdLib()
            .setMemorySize(1000)
            .setStdOut(pw)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    Assertions.assertEquals("A", output.toString());
  }

  @Test
  void testAustrin() throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/austrin.spooky");

    byte[] result = Compiler.compile(programStream);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream pw = new PrintStream(output);
    SpookyVm vm =
        SpookyVm.newBuilder(ExecutableParser.fromBinary(result))
            .addStdLib()
            .setMemorySize(1000)
            .setStdOut(pw)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    Assertions.assertEquals("123", output.toString());
  }

  @Test
  void testGlobals() throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/globals.spooky");

    byte[] result = Compiler.compile(programStream);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream pw = new PrintStream(output);
    SpookyVm vm =
        SpookyVm.newBuilder(ExecutableParser.fromBinary(result))
            .addStdLib()
            .setMemorySize(1000)
            .setStdOut(pw)
            .build();
    for (int i = 0; i < 10000 && vm.executeInstruction(); i++)
      ;
    Assertions.assertEquals("4210", output.toString());
  }

  @Test
  void testGlobalCallingFunctions() throws ParseException, ValidationException {
    InputStream programStream =
        getClass().getClassLoader().getResourceAsStream("example_programs/globals_calling_functions.spooky");

    byte[] result = Compiler.compile(programStream);
  }
}
