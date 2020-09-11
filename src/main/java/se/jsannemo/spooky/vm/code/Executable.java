package se.jsannemo.spooky.vm.code;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** A parsed Spooky code executable. */
@AutoValue
public abstract class Executable {

  Executable() {}

  static Builder builder() {
    return new AutoValue_Executable.Builder().data(ImmutableList.of());
  }

  /** The name of the executable. */
  public abstract String name();

  /** The executable instructions making up the text segment of the executable. */
  public abstract ImmutableList<Instructions.Instruction> text();

  /** The binary data making up the data segment of the executable. */
  public abstract ImmutableList<Integer> data();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder name(String name);

    abstract Builder text(ImmutableList<Instructions.Instruction> text);

    abstract Builder data(ImmutableList<Integer> data);

    abstract Executable build();
  }
}
