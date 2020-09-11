package se.jsannemo.spooky.vm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.compiler.Assembler;
import se.jsannemo.spooky.vm.code.Instructions;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.Sub;
import se.jsannemo.spooky.vm.code.Executable;
import se.jsannemo.spooky.vm.code.ExecutableParser;

final class SpookyVmTest {

  private static Executable exec(Instruction... instructions) {
    return exec(ImmutableList.copyOf(instructions), ImmutableList.of());
  }

  private static Executable exec(ImmutableList<Instruction> instructions, ImmutableList<Integer> data) {
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

  @Test
  void testAdd() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec(Add.create(0, 1, 2))).setMemorySize(1000).build();
    vm.setM(0, 42);
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 + 1337);
  }

  @Test
  void testSub() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec(Sub.create(0, 1, 2))).setMemorySize(1000).build();
    vm.setM(0, 42);
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 - 1337);
  }

  @Test
  void testMul() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec(Mul.create(0, 1, 2))).setMemorySize(1000).build();
    vm.setM(0, 42);
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 * 1337);
  }

  @Test
  void testDiv() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec(Div.create(0, 1, 2))).setMemorySize(1000).build();
    vm.setM(0, 1337);
    vm.setM(1, 42);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(1337 / 42);
  }

  @Test
  void testLessThan() throws VmException {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(LessThan.create(0, 1, 2), LessThan.create(1, 0, 2)))
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
  void testConst() throws VmException {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(Const.create(1, 0), Const.create(2, 42)))
            .setMemorySize(1000)
            .build();
    vm.executeInstruction();
    assertThat(vm.getM(0)).isEqualTo(1);
    vm.executeInstruction();
    assertThat(vm.getM(42)).isEqualTo(2);
  }

  @Test
  void testJmp() throws VmException {
    /*
    0: mem[999] = 3
    1: if mem[0] == 0 {
       goto mem[999]
    }
    2: mem[0] = 1
    3: mem[0] = 2
    4: mem[999] = 2
    5: if mem[0] == 0 {
        goto mem[999]
    }
    6: mem[0] = 3
     */
    SpookyVm vm =
        SpookyVm.newBuilder(
                exec(
                    Const.create(3, 999),
                    Jump.create(0, 999),
                    Const.create(1, 0),
                    Const.create(2, 0),
                    Const.create(2, 999),
                    Jump.create(0, 999),
                    Const.create(3, 0)))
            .setMemorySize(1000)
            .build();
    vm.executeInstruction();
    vm.executeInstruction();
    vm.executeInstruction();
    assertThat(vm.getM(0)).isEqualTo(2);
    vm.executeInstruction();
    vm.executeInstruction();
    vm.executeInstruction();
    assertThat(vm.getM(0)).isEqualTo(3);
  }

  @Test
  void testExtern() throws VmException {
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
  void testMov() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec(Move.create(1, 2))).setMemorySize(1000).build();
    vm.setM(1, 500);
    vm.setM(2, 501);
    vm.setM(500, 42);
    vm.executeInstruction();
    assertThat(vm.getM(501)).isEqualTo(42);
  }

  @Test
  void testOutOfBoundRead_crashes() {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(Add.create(1000, 1, 2))).setMemorySize(1000).build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  void testOutOfBoundWrite_crashes() {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(Add.create(0, 1, 1000))).setMemorySize(1000).build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  void testDataMemoryRead() throws VmException {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(ImmutableList.of(Add.create(-1, 1, 2)), ImmutableList.of((42))))
            .setMemorySize(1000)
            .build();
    vm.setM(1, 1337);
    vm.executeInstruction();
    assertThat(vm.getM(2)).isEqualTo(42 + 1337);
  }

  @Test
  void testOutOfBoundDataRead_crashes() {
    SpookyVm vm = SpookyVm.newBuilder(exec(Add.create(-1, 1, 2))).setMemorySize(1000).build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  void testDataMemoryWrite_crashes() {
    SpookyVm vm =
        SpookyVm.newBuilder(exec(ImmutableList.of(Add.create(-1, 1, -1)), ImmutableList.of((42))))
            .setMemorySize(1000)
            .build();
    assertThrows(VmException.class, vm::executeInstruction);
  }

  @Test
  void testOutOfBoundsIp_crashes() throws VmException {
    SpookyVm vm = SpookyVm.newBuilder(exec((Add.create(0, 0, 0)))).setMemorySize(1000).build();
    vm.executeInstruction();
    assertThrows(VmException.class, vm::executeInstruction);
  }
}
