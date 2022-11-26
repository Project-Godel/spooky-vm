load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

J2CL_TAG = "3038a77a114d6a52612d756b7d94ce096c0ece04"

J2CL_SHA = "7b75f524d9a1640dc33447bf089e0b969c91425163f7386fcd5346825ffee2ef"

http_archive(
    name = "com_google_j2cl",
    sha256 = J2CL_SHA,
    strip_prefix = "j2cl-%s" % J2CL_TAG,
    url = "https://github.com/google/j2cl/archive/%s.zip" % J2CL_TAG,
)

load("@com_google_j2cl//build_defs:repository.bzl", "load_j2cl_repo_deps")

load_j2cl_repo_deps()

load("@com_google_j2cl//build_defs:rules.bzl", "setup_j2cl_workspace")

setup_j2cl_workspace()

ELEMENTAL_TAG = "4845f7938212d809f67bc556bb9b4faadbc9f914"

ELEMENTAL_SHA = "117ace6a1c853a4cb303362994aa2f58b4520a19485e6fa5c49d0b803907c54a"

http_archive(
    name = "com_google_elemental2",
    sha256 = ELEMENTAL_SHA,
    strip_prefix = "elemental2-%s" % ELEMENTAL_TAG,
    url = "https://github.com/google/elemental2/archive/%s.zip" % ELEMENTAL_TAG,
)

load("@com_google_elemental2//build_defs:repository.bzl", "load_elemental2_repo_deps")

load_elemental2_repo_deps()

load("@com_google_elemental2//build_defs:workspace.bzl", "setup_elemental2_workspace")

setup_elemental2_workspace()

RULES_JVM_EXTERNAL_TAG = "4.2"

RULES_JVM_EXTERNAL_SHA = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("//third_party:maven_install.bzl", "install_maven")

install_maven()

load("@maven//:compat.bzl", "compat_repositories")

compat_repositories()
