package se.jsannemo.spooky.compiler.ir;

import com.google.auto.value.AutoValue;

public class IrStatement {

  private IrStatement() {}

  @AutoValue
  public abstract static class IrLabel extends IrStatement {
    IrLabel() {}

    public abstract int label();

    public static IrLabel of(int label) {
      return new AutoValue_IrStatement_IrLabel(label);
    }

    @Override
    public String toString() {
      return "L" + label();
    }
  }

  @AutoValue
  public abstract static class IrHalt extends IrStatement {
    IrHalt() {}

    public static IrHalt of() {
      return new AutoValue_IrStatement_IrHalt();
    }

    @Override
    public String toString() {
      return "HALT";
    }
  }

  @AutoValue
  public abstract static class IrExtern extends IrStatement {
    IrExtern() {}

    public abstract String name();

    public abstract int spOffset();

    public static IrExtern of(String name, int spOffset) {
      return new AutoValue_IrStatement_IrExtern(name, spOffset);
    }

    @Override
    public String toString() {
      return "extern " + name() + "()";
    }
  }

  @AutoValue
  public abstract static class IrCall extends IrStatement {
    IrCall() {}

    public abstract String name();

    public abstract int spOffset();

    public abstract IrLabel jumpAfter();

    public static IrCall of(String name, int spOffset, IrLabel label) {
      return new AutoValue_IrStatement_IrCall(name, spOffset, label);
    }

    @Override
    public String toString() {
      return "call " + name() + "()";
    }
  }

  @AutoValue
  public abstract static class IrJmpZero extends IrStatement {
    IrJmpZero() {}

    public abstract IrLabel label();

    public abstract IrAddr flag();

    public static IrJmpZero of(IrLabel label, IrAddr flag) {
      return new AutoValue_IrStatement_IrJmpZero(label, flag);
    }

    @Override
    public String toString() {
      return "JZ " + flag() + " " + label();
    }
  }

  @AutoValue
  public abstract static class IrJmpAdr extends IrStatement {
    IrJmpAdr() {}

    public abstract IrAddr addr();

    public static IrJmpAdr of(IrAddr addr) {
      return new AutoValue_IrStatement_IrJmpAdr(addr);
    }

    @Override
    public String toString() {
      return "JMP " + addr();
    }
  }

  @AutoValue
  public abstract static class IrStore extends IrStatement {
    IrStore() {}

    public abstract IrAddr addr();

    public abstract int value();

    public static IrStore of(IrAddr addr, int value) {
      return new AutoValue_IrStatement_IrStore(addr, value);
    }

    @Override
    public String toString() {
      return addr() + " = " + value();
    }
  }

  @AutoValue
  public abstract static class IrStoreLabel extends IrStatement {
    IrStoreLabel() {}

    public abstract IrAddr addr();

    public abstract IrLabel label();

    public static IrStoreLabel of(IrAddr addr, IrLabel label) {
      return new AutoValue_IrStatement_IrStoreLabel(addr, label);
    }

    @Override
    public String toString() {
      return addr() + " = " + label();
    }
  }

  @AutoValue
  public abstract static class IrAdd extends IrStatement {
    IrAdd() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrAdd forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrAdd(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " + " + b();
    }
  }

  @AutoValue
  public abstract static class IrSub extends IrStatement {
    IrSub() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrSub forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrSub(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " - " + b();
    }
  }

  @AutoValue
  public abstract static class IrMul extends IrStatement {
    IrMul() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrMul forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrMul(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " * " + b();
    }
  }

  @AutoValue
  public abstract static class IrDiv extends IrStatement {
    IrDiv() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrDiv forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrDiv(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " / " + b();
    }
  }

  @AutoValue
  public abstract static class IrMod extends IrStatement {
    IrMod() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrMod forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrMod(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " % " + b();
    }
  }

  @AutoValue
  public abstract static class IrLessThan extends IrStatement {
    IrLessThan() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrLessThan forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrLessThan(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " < " + b();
    }
  }

  @AutoValue
  public abstract static class IrLessEquals extends IrStatement {
    IrLessEquals() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrLessEquals forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrLessEquals(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " < " + b();
    }
  }

  @AutoValue
  public abstract static class IrEquals extends IrStatement {
    IrEquals() {}

    public abstract IrAddr a();

    public abstract IrAddr b();

    public abstract IrAddr result();

    public static IrEquals forTermsAndTarget(IrAddr termA, IrAddr termB, IrAddr target) {
      return new AutoValue_IrStatement_IrEquals(termA, termB, target);
    }

    @Override
    public String toString() {
      return result() + " = " + a() + " == " + b();
    }
  }

  @AutoValue
  public abstract static class IrCopy extends IrStatement {
    IrCopy() {}

    public abstract IrAddr from();

    public abstract IrAddr to();

    public static IrStatement fromTo(IrAddr from, IrAddr to) {
      return new AutoValue_IrStatement_IrCopy(from, to);
    }

    @Override
    public String toString() {
      return to() + " = " + from();
    }
  }
}
