package se.jsannemo.spooky.vm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.compiler.Assembler;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.ExecutableParser;
import se.jsannemo.spooky.vm.code.Instructions;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Address;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Move;

public final class SpookyVmTest {

  private static Executable exec(Instruction... instructions) {
    return exec(ImmutableList.copyOf(instructions), ImmutableList.of());
  }

  private static Executable exec(
          ImmutableList<Instruction> instructions, ImmutableList<Integer> data) {
    try {
      return ExecutableParser.fromBinary(
              Assembler.assemble(
                      ImmutableList.<Instruction>builder()
                              .add(BinDef.create("main"))
                              .add(Instructions.Text.create())
                              .addAll(instructions)
                              .add(Instructions.Data.create(data))
                              .build()));
    } catch (Exception e) {
      throw new RuntimeException("Assembled lib could not be parsed", e);
    }
  }

  private static Address addr(int addr) {
    return Address.baseAndOffset(3, addr);
  }

  @Test
  public void testAdd() throws VmException {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Add.create(addr(0), addr(1), addr(2))))
                    .setMemorySize(1000)
                    .build();
    vm.setM(0, 42);
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 + 1337);
  }

  @Test
  public void testLessThan() throws VmException {
    SpookyVm vm =
            SpookyVm.newBuilder(
                    exec(
                            LessThan.create(addr(0), addr(1), addr(2)),
                            LessThan.create(addr(1), addr(0), addr(2))))
                    .setMemorySize(1000)
                    .build();
    vm.setM(0, 1337);
    vm.setM(1, 42);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(0);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(1);
  }

  @Test
  public void testExtern() throws VmException {
    final boolean[] called = {false};
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Extern.create("test")))
                    .setMemorySize(1000)
                    .addExtern("test", (v) -> called[0] = true)
                    .build();
    vm.executeInstruction();
    assertThat(called[0]).isTrue();
  }

  @Test
  public void testMov() throws VmException {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Move.create(addr(1), addr(2)))).setMemorySize(1000).build();
    vm.setM(1, 500);
    vm.setM(2, 501);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(500);
  }

  @Test
  public void testOutOfBoundRead_crashes() {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Add.create(addr(1000), addr(1), addr(2))))
                    .setMemorySize(1000)
                    .build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  public void testOutOfBoundWrite_crashes() {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Add.create(addr(0), addr(1), addr(1000))))
                    .setMemorySize(1000)
                    .build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  public void testDataMemoryRead() throws VmException {
    SpookyVm vm =
            SpookyVm.newBuilder(
                    exec(
                            ImmutableList.of(Add.create(addr(-1), addr(1), addr(2))),
                            ImmutableList.of((42))))
                    .setMemorySize(1000)
                    .build();
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 + 1337);
  }

  @Test
  public void testOutOfBoundDataRead_crashes() {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Add.create(addr(-1), addr(1), addr(2))))
                    .setMemorySize(1000)
                    .build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  public void testDataMemoryWrite_crashes() {
    SpookyVm vm =
            SpookyVm.newBuilder(
                    exec(
                            ImmutableList.of(Add.create(addr(-1), addr(1), addr(-1))),
                            ImmutableList.of((42))))
                    .setMemorySize(1000)
                    .build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  public void testOutOfBoundsIp_crashes() throws VmException {
    SpookyVm vm =
            SpookyVm.newBuilder(exec(Add.create(addr(0), addr(0), addr(0))))
                    .setMemorySize(1000)
                    .build();
    vm.executeInstruction();
    assertThrows(VmException.class, vm::executeInstruction);
  }
}
