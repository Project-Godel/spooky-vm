load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def setup_protobuf():
    # rules_java defines rules for generating Java code from Protocol Buffers.
    http_archive(
        name = "rules_java",
        strip_prefix = "rules_java-c13e3ead84afb95f81fbddfade2749d8ba7cb77f",
        sha256 = "7c4bbe11e41c61212a5cf16d9aafaddade3f5b1b6c8bf94270d78215fafd4007",
        urls = [
            "https://github.com/bazelbuild/rules_java/archive/c13e3ead84afb95f81fbddfade2749d8ba7cb77f.tar.gz",
        ],
    )

    # rules_proto defines abstract rules for building Protocol Buffers.
    http_archive(
        name = "rules_proto",
        strip_prefix = "rules_proto-f7a30f6f80006b591fa7c437fe5a951eb10bcbcf",
        sha256 = "9fc210a34f0f9e7cc31598d109b5d069ef44911a82f507d5a88716db171615a8",
        urls = [
            "https://github.com/bazelbuild/rules_proto/archive/f7a30f6f80006b591fa7c437fe5a951eb10bcbcf.tar.gz",
        ],
    )
