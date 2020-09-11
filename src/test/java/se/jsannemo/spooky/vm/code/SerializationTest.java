package se.jsannemo.spooky.vm.code;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SerializationTest {

  @ParameterizedTest()
  @ValueSource(ints = {Integer.MAX_VALUE, Integer.MIN_VALUE, 0, -1, 1, 293487, 9827598, 1238612})
  void testIntSerialization(int val) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Serialization.writeInt(baos, val);
    byte[] serialized = baos.toByteArray();

    assertThat(Serialization.readInt(new ByteStreamIterator(serialized))).isEqualTo(val);
  }



}