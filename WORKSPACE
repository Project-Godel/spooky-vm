load("//third_party/maven_utils:maven.bzl", "setup_maven")

setup_maven()

load("//third_party/maven_utils:maven_install.bzl", "install_maven")

install_maven()

load("//third_party/protobuf:protobuf.bzl", "setup_protobuf")

setup_protobuf()

load("//third_party/protobuf:protobuf_install.bzl", "install_protobuf")

install_protobuf()

load("//third_party/antlr:antlr.bzl", "setup_antlr")

setup_antlr()

load("//third_party/antlr:antlr_install.bzl", "install_antlr")

install_antlr()

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "openjdk15_linux_archive",
    build_file_content = """
java_runtime(name = 'runtime', srcs =  glob(['**']), visibility = ['//visibility:public'])
exports_files(["WORKSPACE"], visibility = ["//visibility:public"])
""",
    sha256 = "0a38f1138c15a4f243b75eb82f8ef40855afcc402e3c2a6de97ce8235011b1ad",
    strip_prefix = "zulu15.27.17-ca-jdk15.0.0-linux_x64",
    urls = [
        "https://mirror.bazel.build/cdn.azul.com/zulu/bin/zulu15.27.17-ca-jdk15.0.0-linux_x64.tar.gz",
        "https://cdn.azul.com/zulu/bin/zulu15.27.17-ca-jdk15.0.0-linux_x64.tar.gz",
    ],
)

http_archive(
    name = "com_google_j2cl",
    strip_prefix = "j2cl-master",
    url = "https://github.com/google/j2cl/archive/master.zip",
)

load("@com_google_j2cl//build_defs:repository.bzl", "load_j2cl_repo_deps")

load_j2cl_repo_deps()

load("@com_google_j2cl//build_defs:rules.bzl", "setup_j2cl_workspace")

setup_j2cl_workspace()
