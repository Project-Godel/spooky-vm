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

import static com.google.common.truth.Truth.assertThat;

public final class CompilerTest {

  @Test
  public void testHelloWorld()
          throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("helloworld")).isEqualTo("Hello World!");
  }

  @Test
  void testIsPrime() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("isprime")).isEqualTo("90 0\n91 0\n92 0\n93 0\n94 0\n95 0\n96 0\n97 1\n98 0\n99 0\n");
  }

  @Test
  void testCarl() {
    InputStream programStream =
            getClass().getClassLoader().getResourceAsStream("example_programs/carl.spooky");
    Assertions.assertThrows(ValidationException.class, () -> Compiler.compile(programStream));
  }

  @Test
  void testCarl2() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("carl2")).isEqualTo("A");
  }

  @Test
  void testAustrin() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("austrin")).isEqualTo("1-23");
  }

  @Test
  void testGlobals() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("globals")).isEqualTo("4210");
  }

  @Test
  void testGlobalCallingFunctions() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("globals_calling_functions")).endsWith("4");
  }

  @Test
  void testGlobalsStack() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("globals_stack")).isEqualTo("3");
  }

  @Test
  void testFizzBuzz() throws ParseException, ValidationException, InstructionException, VmException {
    assertThat(runExample("fizzbuzz")).isEqualTo("0010210012010012");
  }

  private static String runExample(String name) throws ParseException, ValidationException, InstructionException, VmException {
    InputStream programStream =
            CompilerTest.class.getClassLoader().getResourceAsStream("example_programs/" + name + ".spooky");

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
    return output.toString();
  }
}
