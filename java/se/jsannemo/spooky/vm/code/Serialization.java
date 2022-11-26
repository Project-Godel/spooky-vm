package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Utility class for serializing and de-serializing non-trivial data types. */
final class Serialization {

  private Serialization() {}

  static int readInt(ByteStreamIterator iterator) {
    checkArgument(iterator.position + 4 <= iterator.content.length, "Not enough bytes to read int");
    int res = 0;
    for (int i = 0; i < 4; i++) {
      res = (res << 8) | (((int) iterator.currentByte()) & 0xff);
      iterator.advance(1);
    }
    return res;
  }

  static void writeInt(OutputStream out, int value) throws IOException {
    for (int i = 0; i < 4; i++) {
      int bitpos = (3 - i) * 8;
      int msk = 0xff << bitpos;
      int val = (value & msk) >>> bitpos;
      out.write(val);
    }
  }

  static String readString(ByteStreamIterator iterator) {
    checkArgument(!iterator.finished(), "Not enough bytes to read string length");
    int length = ((int) iterator.currentByte()) & 0xff;
    iterator.advance(1);
    checkArgument(
        iterator.position + length <= iterator.content.length, "Not enough bytes to read string ");
    String res =
        new String(iterator.content, iterator.position, length, StandardCharsets.ISO_8859_1);
    iterator.advance(length);
    return res;
  }

  static void writeString(OutputStream out, String value) throws IOException {
    byte[] byteVal = value.getBytes(StandardCharsets.ISO_8859_1);
    checkArgument(byteVal.length <= 255, "String too long for serialization");
    out.write(value.length());
    out.write(byteVal);
  }

  public static void writeAddr(OutputStream os, Instructions.Address addr) throws IOException {
    writeInt(os, addr.baseAddr());
    writeInt(os, addr.offset());
  }

  public static Instructions.Address readAddr(ByteStreamIterator iterator) {
    return Instructions.Address.baseAndOffset(readInt(iterator), readInt(iterator));
  }
}
