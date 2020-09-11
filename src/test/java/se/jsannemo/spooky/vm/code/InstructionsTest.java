package se.jsannemo.spooky.vm.code;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;

final class InstructionsTest {

  @Test
  void testIsExecutable() {
    assertThat(BinDef.create("test").isExecutable()).isFalse();
    assertThat(Instructions.Data.create(ImmutableList.of()).isExecutable()).isFalse();
    assertThat(Instructions.Text.create().isExecutable()).isFalse();
    assertThat(Instructions.Add.create(1, 2, 3).isExecutable()).isTrue();
    assertThat(Instructions.Sub.create(1, 2, 3).isExecutable()).isTrue();
    assertThat(Instructions.Div.create(1, 2, 3).isExecutable()).isTrue();
    assertThat(Instructions.Mul.create(1, 2, 3).isExecutable()).isTrue();
    assertThat(Instructions.Jump.create(1, 2).isExecutable()).isTrue();
    assertThat(Instructions.LessThan.create(1, 2, 3).isExecutable()).isTrue();
    assertThat(Instructions.Extern.create("name").isExecutable()).isTrue();
    assertThat(Instructions.Const.create(1, 2).isExecutable()).isTrue();
    assertThat(Instructions.Move.create(1, 2).isExecutable()).isTrue();
  }
}
