load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def setup_protobuf():
    # rules_java defines rules for generating Java code from Protocol Buffers.
    http_archive(
        name = "rules_java",
        strip_prefix = "rules_java-c13e3ead84afb95f81fbddfade2749d8ba7cb77f",
        urls = [
            "https://github.com/bazelbuild/rules_java/archive/c13e3ead84afb95f81fbddfade2749d8ba7cb77f.tar.gz",
        ],
    )

    # rules_proto defines abstract rules for building Protocol Buffers.
    http_archive(
        name = "rules_proto",
        strip_prefix = "rules_proto-f7a30f6f80006b591fa7c437fe5a951eb10bcbcf",
        urls = [
            "https://github.com/bazelbuild/rules_proto/archive/f7a30f6f80006b591fa7c437fe5a951eb10bcbcf.tar.gz",
        ],
    )
