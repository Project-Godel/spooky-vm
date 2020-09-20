package se.jsannemo.spooky.vm.code;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Address;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Data;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Text;

final class ExecutableParserTest {

  private static void assertParsingFails(Instruction... instructions) {
    assertThrows(
        InstructionException.class, () -> ExecutableParser.parse(Arrays.asList(instructions)));
  }

  private static Executable parseExec(Instruction... instructions) {
    return assertDoesNotThrow(() -> ExecutableParser.parse(Arrays.asList(instructions)));
  }

  @Test
  void testParseEmptyExec_fails() {
    assertParsingFails();
  }

  @Test
  void testParseMissingBinDef_fails() {
    assertParsingFails(Text.create(), Data.create(ImmutableList.of()));
  }

  @Test
  void testParseExecName() {
    Executable executable = parseExec(BinDef.create("name"), Text.create());
    assertThat(executable.name()).isEqualTo("name");
  }

  @Test
  void testParseEmptyText() {
    Executable executable = parseExec(BinDef.create("name"), Text.create());
    assertThat(executable.text()).isEmpty();
  }

  @Test
  void testParseText() {
    Add add =
        Add.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    Executable executable =
        parseExec(BinDef.create("name"), Text.create(), add, Data.create(ImmutableList.of()));
    assertThat(executable.text()).containsExactly(add);
  }

  @Test
  void testParseData() {
    Executable executable =
        parseExec(
            BinDef.create("name"),
            Text.create(),
            Data.create(ImmutableList.of(Integer.MIN_VALUE, Integer.MAX_VALUE)));
    assertThat(executable.data()).containsExactly(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }
}
