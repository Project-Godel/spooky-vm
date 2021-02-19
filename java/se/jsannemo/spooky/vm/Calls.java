package se.jsannemo.spooky.vm;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Wrappers for external calls that do not need any specific VM access aside from parameters. */
public final class Calls {

  public static ExternCall retInt(Supplier<Integer> is) {
    return vm -> StdLib.setReturn(vm, 0, is.get());
  }

  public static ExternCall getInt(Consumer<Integer> ic) {
    return vm -> ic.accept(StdLib.getArg(vm, 0));
  }

  public static ExternCall intToInt(Function<Integer, Integer> fun) {
    return vm -> StdLib.setReturn(vm, 1, fun.apply(StdLib.getArg(vm, 0)));
  }
}
