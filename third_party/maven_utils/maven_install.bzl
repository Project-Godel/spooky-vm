load("//third_party/flogger:flogger.bzl", "flogger_maven")
load("//third_party/guava:guava.bzl", "guava_maven")
load("//third_party/junit:junit.bzl", "junit_maven")
load("//third_party/truth:truth.bzl", "truth_maven")
load("@rules_jvm_external//:defs.bzl", "maven_install")

def install_maven_jars(artifacts):
    artifacts_to_maven = []
    for value in artifacts:
        artifacts_to_maven.append(value.artifact + ":" + value.version)
    maven_install(
        artifacts = artifacts_to_maven,
        repositories = ["https://repo1.maven.org/maven2"],
        strict_visibility = True,
        fetch_sources = True,
    )

def install_maven():
    maven_deps = []
    maven_deps += flogger_maven()
    maven_deps += guava_maven()
    maven_deps += junit_maven()
    maven_deps += truth_maven()
    install_maven_jars(maven_deps)
