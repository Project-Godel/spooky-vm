package se.jsannemo.spooky.vm.code;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.code.Instructions.*;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ExecutableParserTest {

  private static void assertParsingFails(Instruction... instructions) {
    assertThrows(
        InstructionException.class, () -> ExecutableParser.parse(Arrays.asList(instructions)));
  }

  private static Executable parseExec(Instruction... instructions) {
    return assertDoesNotThrow(() -> ExecutableParser.parse(Arrays.asList(instructions)));
  }

  @Test
  public void testParseEmptyExec_fails() {
    assertParsingFails();
  }

  @Test
  public void testParseMissingBinDef_fails() {
    assertParsingFails(Text.create(), Data.create(ImmutableList.of()));
  }

  @Test
  public void testParseExecName() {
    Executable executable = parseExec(BinDef.create("name"), Text.create());
    assertThat(executable.name()).isEqualTo("name");
  }

  @Test
  public void testParseEmptyText() {
    Executable executable = parseExec(BinDef.create("name"), Text.create());
    assertThat(executable.text()).isEmpty();
  }

  @Test
  public void testParseText() {
    Add add =
        Add.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    Executable executable =
        parseExec(BinDef.create("name"), Text.create(), add, Data.create(ImmutableList.of()));
    assertThat(executable.text()).containsExactly(add);
  }

  @Test
  public void testParseData() {
    Executable executable =
        parseExec(
            BinDef.create("name"),
            Text.create(),
            Data.create(ImmutableList.of(Integer.MIN_VALUE, Integer.MAX_VALUE)));
    assertThat(executable.data()).containsExactly(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }
}
