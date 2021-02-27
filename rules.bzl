load("@rules_java//java:defs.bzl", "java_library", "java_proto_library", "java_test")
load("@com_google_j2cl//build_defs:rules.bzl", "j2cl_library")
load("@com_google_j2cl_protobuf//java/com/google/protobuf/contrib/immutablejs:immutable_js_proto_library.bzl", "immutable_js_proto_library")
load("//:j2cl_proto.bzl", "j2cl_proto_library")

def java_j2cl_proto_library(name, visibility = ["//visibility:private"], deps = [], **kwargs):
    java_proto_library(name = name, deps = deps, visibility = visibility, **kwargs)
    j2cl_proto_library(name = name + "-j2cl-src", deps = deps, **kwargs)
    immutable_js_proto_library(
        name = name + "-immutable",
        deps = deps,
    )
    j2cl_library(
        name = name + "-j2cl",
        srcs = [":" + name + "-j2cl-src"],
        visibility = visibility,
        deps = [
            "@com_google_j2cl//:jsinterop-annotations-j2cl",
            "@com_google_j2cl_protobuf//third_party:jsinterop-base-j2cl",
            "@com_google_j2cl_protobuf//third_party:j2cl_proto_runtime",
            ":" + name + "-immutable",
            "@com_google_j2cl_protobuf//java/com/google/protobuf/contrib/immutablejs:runtime",
        ],
        exports = [
            ":" + name + "-immutable",
        ],
    )

def java_j2cl_library(name, **kwargs):
    deps = kwargs.pop("deps", [])
    j2cl_deps = [_label_fix(d) + "-j2cl" for d in deps]
    java_library(name = name, deps = deps, **kwargs)
    j2cl_library(name = name + "-j2cl", deps = j2cl_deps, **kwargs)

def _label_fix(s):
    if ":" not in s:
        s = s + ":" + s[s.rindex("/") + 1:]
    return s

# From https://github.com/GerritCodeReview/bazlets/blob/master/tools/junit.bzl
# Copyright (C) 2016 The Android Open Source Project
# Apache V2: http://www.apache.org/licenses/LICENSE-2.0
_OUTPUT = """import org.junit.runners.Suite;
import org.junit.runner.RunWith;
@RunWith(Suite.class)
@Suite.SuiteClasses({%s})
public class %s {}
"""

_PREFIXES = ("se",)

def _SafeIndex(l, val):
    for i, v in enumerate(l):
        if val == v:
            return i
    return -1

def _AsClassName(fname):
    fname = [x.path for x in fname.files.to_list()][0]
    toks = fname[:-5].split("/")
    findex = -1
    for s in _PREFIXES:
        findex = _SafeIndex(toks, s)
        if findex != -1:
            break
    if findex == -1:
        fail("%s does not contain any of %s" % (fname, _PREFIXES))
    return ".".join(toks[findex:]) + ".class"

def _impl(ctx):
    classes = ",".join(
        [_AsClassName(x) for x in ctx.attr.srcs],
    )
    ctx.actions.write(output = ctx.outputs.out, content = _OUTPUT % (
        classes,
        ctx.attr.outname,
    ))

_GenSuite = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "outname": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _impl,
)

def junit_tests(name, srcs, **kwargs):
    s_name = name.replace("-", "_") + "TestSuite"
    _GenSuite(
        name = s_name,
        srcs = srcs,
        outname = s_name,
    )
    java_test(
        name = name,
        test_class = s_name,
        srcs = srcs + [":" + s_name],
        **kwargs
    )
