package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static se.jsannemo.spooky.vm.code.OpCodes.ADD;
import static se.jsannemo.spooky.vm.code.OpCodes.CONST;
import static se.jsannemo.spooky.vm.code.OpCodes.DATA;
import static se.jsannemo.spooky.vm.code.OpCodes.DIV;
import static se.jsannemo.spooky.vm.code.OpCodes.EXTERN;
import static se.jsannemo.spooky.vm.code.OpCodes.JMP;
import static se.jsannemo.spooky.vm.code.OpCodes.BINDEF;
import static se.jsannemo.spooky.vm.code.OpCodes.LT;
import static se.jsannemo.spooky.vm.code.OpCodes.MOV;
import static se.jsannemo.spooky.vm.code.OpCodes.MUL;
import static se.jsannemo.spooky.vm.code.OpCodes.SUB;
import static se.jsannemo.spooky.vm.code.OpCodes.TEXT;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.Sub;
import se.jsannemo.spooky.vm.code.Instructions.Text;

final class InstructionTokenizerTest {

  private static byte[] bytearg(byte arg) {
    return new byte[] {arg};
  }

  private static byte[] intarg(int arg) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream(4);
    try {
      Serialization.writeInt(bos, arg);
    } catch (IOException e) {
      throw new AssertionError("ByteArrayOutputStream should never throw an exception.");
    }
    return bos.toByteArray();
  }

  private static byte[] stringarg(String arg) {
    checkArgument(arg.length() <= 0xff, "String too long to store length");
    byte[] bytes = arg.getBytes();
    byte[] res = new byte[1 + bytes.length];
    res[0] = (byte) arg.length();
    System.arraycopy(bytes, 0, res, 1, bytes.length);
    return res;
  }

  private static byte[] concat(byte[]... parts) {
    int length = 0;
    for (byte[] part : parts) length += part.length;
    byte[] result = new byte[length];
    int at = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, result, at, part.length);
      at += part.length;
    }
    return result;
  }

  @Test
  void testBinDef() throws InstructionException {
    byte[] program = concat(bytearg(BINDEF), stringarg("bin"));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(BinDef.create("bin"));
  }

  @Test
  void testEmptyBinName() {
    byte[] program = concat(bytearg(BINDEF), stringarg(""));
    assertThrows(InstructionException.class, () -> InstructionTokenizer.tokenize(program));
  }

  @Test
  void testTooShortString() {
    byte[] program = concat(bytearg(BINDEF), bytearg((byte) 0x01));
    assertThrows(InstructionException.class, () -> InstructionTokenizer.tokenize(program));
  }

  @Test
  void testStringWithAllChars() throws InstructionException {
    byte[] byte0to254 = new byte[255];
    for (int i = 0; i < 255; i++) {
      byte0to254[i] = (byte) i;
    }
    byte[] byte255 = new byte[] {(byte) 0xFF};

    BinDef bc1 =
        (BinDef)
            InstructionTokenizer.tokenize(concat(bytearg(BINDEF), bytearg((byte) 255), byte0to254))
                .get(0);
    BinDef bc2 =
        (BinDef)
            InstructionTokenizer.tokenize(concat(bytearg(BINDEF), bytearg((byte) 1), byte255))
                .get(0);

    assertThat(bc1.name()).hasLength(255);
    assertThat(bc2.name()).hasLength(1);
  }

  @Test
  void testText() throws InstructionException {
    byte[] program = concat(bytearg(TEXT));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Text.create());
  }

  @Test
  void testData() throws InstructionException {
    byte[] program =
        concat(
            bytearg(DATA),
            intarg(1),
            intarg(2),
            intarg(Integer.MIN_VALUE),
            intarg(Integer.MAX_VALUE));

    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);

    assertThat(instruction)
        .containsExactly(
            Instructions.Data.create(ImmutableList.of(1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE)));
  }

  @Test
  void testAdd() throws InstructionException {
    byte[] program = concat(bytearg(ADD), intarg(1), intarg(2), intarg(3));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Add.create(1, 2, 3));
  }

  @Test
  void testSub() throws InstructionException {
    byte[] program = concat(bytearg(SUB), intarg(1), intarg(2), intarg(3));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Sub.create(1, 2, 3));
  }

  @Test
  void testMul() throws InstructionException {
    byte[] program = concat(bytearg(MUL), intarg(1), intarg(2), intarg(3));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Mul.create(1, 2, 3));
  }

  @Test
  void testDiv() throws InstructionException {
    byte[] program = concat(bytearg(DIV), intarg(1), intarg(2), intarg(3));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Div.create(1, 2, 3));
  }

  @Test
  void testExtern() throws InstructionException {
    byte[] program = concat(bytearg(EXTERN), stringarg("random"));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Extern.create("random"));
  }

  @Test
  void testJump() throws InstructionException {
    byte[] program = concat(bytearg(JMP), intarg(1), intarg(2));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Jump.create(1, 2));
  }

  @Test
  void testLessThan() throws InstructionException {
    byte[] program = concat(bytearg(LT), intarg(1), intarg(2), intarg(3));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(LessThan.create(1, 2, 3));
  }

  @Test
  void testMove() throws InstructionException {
    byte[] program = concat(bytearg(MOV), intarg(1), intarg(2));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Move.create(1, 2));
  }

  @Test
  void testConst() throws InstructionException {
    byte[] program = concat(bytearg(CONST), intarg(1), intarg(Integer.MAX_VALUE));
    List<Instructions.Instruction> instruction = InstructionTokenizer.tokenize(program);
    assertThat(instruction).containsExactly(Const.create(1, Integer.MAX_VALUE));
  }
}
