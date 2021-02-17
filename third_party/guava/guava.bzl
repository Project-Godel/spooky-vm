load("//third_party/maven_utils:maven.bzl", "maven_jar")

VERSION = "29.0-jre"

def guava_maven():
    return [
        maven_jar(
            name = "guava",
            artifact = "com.google.guava:guava",
            version = VERSION,
        ),
    ]
