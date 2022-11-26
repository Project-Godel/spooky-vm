package se.jsannemo.spooky.vm;

import jsinterop.annotations.JsType;

/** The signature of an external call that should be supported in the VM. */
@FunctionalInterface
@JsType
public interface ExternCall {
  void call(SpookyVm vm) throws VmException;
}
