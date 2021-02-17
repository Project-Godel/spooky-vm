package se.jsannemo.spooky.vm.code;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import se.jsannemo.spooky.vm.code.Instructions.Address;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public final class SerializationTest {

  @ParameterizedTest()
  @ValueSource(ints = {Integer.MAX_VALUE, Integer.MIN_VALUE, 0, -1, 1, 293487, 9827598, 1238612})
  public void testIntSerialization(int val) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serialization.writeInt(baos, val);
    byte[] serialized = baos.toByteArray();

    assertThat(Serialization.readInt(new ByteStreamIterator(serialized))).isEqualTo(val);
  }

  @ParameterizedTest()
  @CsvSource({"0,0", "-1,0", Integer.MIN_VALUE + "," + Integer.MAX_VALUE, "234234,-432423"})
  public void testAddressSerialization(int base, int offset) throws IOException {
    Address addr = Address.baseAndOffset(base, offset);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serialization.writeAddr(baos, addr);
    byte[] serialized = baos.toByteArray();

    assertThat(Serialization.readAddr(new ByteStreamIterator(serialized))).isEqualTo(addr);
  }
}
