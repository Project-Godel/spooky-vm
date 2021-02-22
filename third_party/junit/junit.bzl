load("//third_party/maven_utils:maven.bzl", "maven_jar")

VERSION = "4.13"

def junit_maven():
    return [
        maven_jar(
            name = "junit4",
            artifact = "junit:junit",
            version = VERSION,
        ),
    ]
