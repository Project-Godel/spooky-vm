load("//third_party/maven_utils:maven.bzl", "maven_jar")

VERSION = "1.0.1"

def truth_maven():
    return [
        maven_jar(
            name = "truth",
            artifact = "com.google.truth:truth",
            version = VERSION,
        ),
        maven_jar(
            name = "truth-java8",
            artifact = "com.google.truth.extensions:truth-java8-extension",
            version = VERSION,
        ),
        maven_jar(
            name = "truth-proto",
            artifact = "com.google.truth.extensions:truth-proto-extension",
            version = VERSION,
        ),
    ]
