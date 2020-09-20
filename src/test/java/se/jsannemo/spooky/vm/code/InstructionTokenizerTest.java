package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static se.jsannemo.spooky.vm.code.OpCode.BINDEF;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import se.jsannemo.spooky.vm.code.Instructions.Add;
import se.jsannemo.spooky.vm.code.Instructions.Address;
import se.jsannemo.spooky.vm.code.Instructions.BinDef;
import se.jsannemo.spooky.vm.code.Instructions.Const;
import se.jsannemo.spooky.vm.code.Instructions.Data;
import se.jsannemo.spooky.vm.code.Instructions.Div;
import se.jsannemo.spooky.vm.code.Instructions.Equals;
import se.jsannemo.spooky.vm.code.Instructions.Extern;
import se.jsannemo.spooky.vm.code.Instructions.Instruction;
import se.jsannemo.spooky.vm.code.Instructions.Jump;
import se.jsannemo.spooky.vm.code.Instructions.LessThan;
import se.jsannemo.spooky.vm.code.Instructions.Move;
import se.jsannemo.spooky.vm.code.Instructions.Mul;
import se.jsannemo.spooky.vm.code.Instructions.Sub;
import se.jsannemo.spooky.vm.code.Instructions.Text;

final class InstructionTokenizerTest {

  private static byte[] bytearg(byte arg) {
    return new byte[] {arg};
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

  private static byte[] binary(Instruction inst) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    inst.writeBinary(baos);
    return baos.toByteArray();
  }

  @Test
  void testBinDef() throws InstructionException, IOException {
    BinDef binDef = BinDef.create("program");
    assertThat(InstructionTokenizer.tokenize(binary(binDef))).containsExactly(binDef);
  }

  @Test
  void testEmptyBinName() {
    byte[] program = concat(bytearg(BINDEF.code), stringarg(""));
    assertThrows(InstructionException.class, () -> InstructionTokenizer.tokenize(program));
  }

  @Test
  void testTooShortString() {
    byte[] program = concat(bytearg(BINDEF.code), bytearg((byte) 0x01));
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
            InstructionTokenizer.tokenize(
                    concat(bytearg(BINDEF.code), bytearg((byte) 255), byte0to254))
                .get(0);
    BinDef bc2 =
        (BinDef)
            InstructionTokenizer.tokenize(concat(bytearg(BINDEF.code), bytearg((byte) 1), byte255))
                .get(0);

    assertThat(bc1.name()).hasLength(255);
    assertThat(bc2.name()).hasLength(1);
  }

  @Test
  void testText() throws InstructionException, IOException {
    Text text = Text.create();
    assertThat(InstructionTokenizer.tokenize(binary(text))).containsExactly(text);
  }

  @Test
  void testData() throws InstructionException, IOException {
    Data data = Data.create(ImmutableList.of(1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE));
    assertThat(InstructionTokenizer.tokenize(binary(data))).containsExactly(data);
  }

  @Test
  void testAdd() throws InstructionException, IOException {
    Add add =
        Add.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(add))).containsExactly(add);
  }

  @Test
  void testSub() throws InstructionException, IOException {
    Sub sub =
        Sub.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(sub))).containsExactly(sub);
  }

  @Test
  void testMul() throws InstructionException, IOException {
    Mul mul =
        Mul.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(mul))).containsExactly(mul);
  }

  @Test
  void testDiv() throws InstructionException, IOException {
    Div div =
        Div.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(div))).containsExactly(div);
  }

  @Test
  void testExtern() throws InstructionException, IOException {
    Extern extern = Extern.create("random");
    assertThat(InstructionTokenizer.tokenize(binary(extern))).containsExactly(extern);
  }

  @Test
  void testJump() throws InstructionException, IOException {
    Jump jump = Jump.create(Address.baseAndOffset(1, 2), 1);
    assertThat(InstructionTokenizer.tokenize(binary(jump))).containsExactly(jump);
  }

  @Test
  void testLessThan() throws InstructionException, IOException {
    LessThan lessThan =
        LessThan.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(lessThan))).containsExactly(lessThan);
  }

  @Test
  void testEquals() throws InstructionException, IOException {
    Equals eq =
        Equals.create(
            Address.baseAndOffset(0, 1), Address.baseAndOffset(2, 3), Address.baseAndOffset(4, 5));
    assertThat(InstructionTokenizer.tokenize(binary(eq))).containsExactly(eq);
  }

  @Test
  void testMove() throws InstructionException, IOException {
    Move mov = Move.create(Address.baseAndOffset(1, 0), Address.baseAndOffset(3, 4));
    assertThat(InstructionTokenizer.tokenize(binary(mov))).containsExactly(mov);
  }

  @Test
  void testConst() throws InstructionException, IOException {
    Const cnst = Const.create(1, Address.baseAndOffset(0, Integer.MAX_VALUE));
    assertThat(InstructionTokenizer.tokenize(binary(cnst))).containsExactly(cnst);
  }
}
