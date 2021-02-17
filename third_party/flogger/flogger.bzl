load("//third_party/maven_utils:maven.bzl", "maven_jar")

VERSION = "0.5.1"

def flogger_maven():
    return [
        maven_jar(
            name = "flogger",
            artifact = "com.google.flogger:flogger",
            version = VERSION,
        ),
        maven_jar(
            name = "flogger-system-backend",
            artifact = "com.google.flogger:flogger-system-backend",
            version = VERSION,
        ),
    ]
