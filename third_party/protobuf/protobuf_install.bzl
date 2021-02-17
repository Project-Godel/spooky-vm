load("@rules_cc//cc:repositories.bzl", "rules_cc_dependencies")
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

def install_protobuf():
    rules_cc_dependencies()
    rules_java_dependencies()
    rules_java_toolchains()
    rules_proto_dependencies()
    rules_proto_toolchains()
