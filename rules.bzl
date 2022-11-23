load("@com_google_j2cl//build_defs:rules.bzl", "j2cl_library")

def java_j2cl_library(name, deps = [], exports = [], **kwargs):
    native.java_library(name = name, deps = deps, exports = exports, **kwargs)
    j2cl_library(name = name + "-j2cl", generate_j2wasm_library = False, exports = _j2clify(exports), deps = _j2clify(deps), **kwargs)

def _j2clify(labels):
    ret = []
    for l in labels:
        if ":" not in l:
            l = l + ":" + l.split("/")[-1]
        l += "-j2cl"
        ret.append(l)
    return ret
