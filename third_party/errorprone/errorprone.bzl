load("//third_party/maven_utils:maven.bzl", "maven_jar")

VERSION = "2.3.4"

def errorprone_maven():
    return [
        maven_jar(
            name = "errorprone",
            artifact = "com.google.errorprone:error_prone_annotations",
            version = VERSION,
        ),
    ]
