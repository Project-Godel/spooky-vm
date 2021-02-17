package se.jsannemo.spooky.vm.code;

import static com.google.common.base.Preconditions.checkArgument;

final class ByteStreamIterator {
  int position;
  byte[] content;

  ByteStreamIterator(byte[] content) {
    this.position = 0;
    this.content = content;
  }

  boolean finished() {
    return position == content.length;
  }

  byte currentByte() {
    if (finished()) {
      throw new IllegalStateException("Attempting to retrieve current byte of finished iterator");
    }
    return content[position];
  }

  void advance(int steps) {
    checkArgument(0 <= steps, "Attempting to advance iterator backwards");
    checkArgument(position + steps <= content.length);
    position += steps;
  }
}
