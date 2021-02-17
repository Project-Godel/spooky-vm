load("@rules_antlr//antlr:repositories.bzl", "rules_antlr_dependencies")

ANTLR_VERSION = "4.8"

def install_antlr():
    rules_antlr_dependencies(ANTLR_VERSION)
