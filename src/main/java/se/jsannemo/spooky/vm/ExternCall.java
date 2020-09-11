package se.jsannemo.spooky.vm;

/** The signature of an external call that should be supported in the VM. */
@FunctionalInterface
public interface ExternCall {
  void call(SpookyVm vm) throws VmException;
}
