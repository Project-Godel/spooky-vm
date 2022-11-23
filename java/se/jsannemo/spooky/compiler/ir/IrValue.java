package se.jsannemo.spooky.compiler.ir;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class IrValue {
  IrValue() {}

  public static IrValue ofTypeAndAddress(IrType type, IrAddr address) {
    return new AutoValue_IrValue(type, address);
  }

  public abstract IrType type();

  public abstract IrAddr address();
}
