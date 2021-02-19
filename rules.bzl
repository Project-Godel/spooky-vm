# From https://github.com/GerritCodeReview/bazlets/blob/master/tools/junit.bzl
# Copyright (C) 2016 The Android Open Source Project
# Apache V2: http://www.apache.org/licenses/LICENSE-2.0
load("@rules_java//java:defs.bzl", "java_test")

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
