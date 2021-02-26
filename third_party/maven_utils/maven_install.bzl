load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@com_google_j2cl//build_defs:rules.bzl", "j2cl_maven_import_external")

def maven_jar(name, artifact, version):
    return struct(
        name = name,
        artifact = artifact,
        version = version,
        j2cl = False,
    )

def maven_j2cl_jar(name, artifact, version, annotation_only = False):
    return struct(
        name = name,
        artifact = artifact,
        version = version,
        j2cl = True,
        annotation_only = annotation_only,
    )

def install_maven_jars(artifacts):
    artifacts_to_maven = []
    for value in artifacts:
        if value.j2cl:
            j2cl_maven_import_external(
                name = value.name,
                annotation_only = value.annotation_only,
                artifact = value.artifact + ":" + value.version,
                server_urls = ["https://repo1.maven.org/maven2/"],
            )
        else:
            artifacts_to_maven.append(value.artifact + ":" + value.version)
    maven_install(
        artifacts = artifacts_to_maven,
        repositories = ["https://repo1.maven.org/maven2"],
        strict_visibility = True,
        fetch_sources = True,
        generate_compat_repositories = True,
    )

def errorprone_maven():
    VERSION = "2.3.4"
    return [
        maven_jar(
            name = "errorprone",
            artifact = "com.google.errorprone:error_prone_annotations",
            version = VERSION,
        ),
        maven_j2cl_jar(
            name = "com_googl_errorprone-j2cl",
            artifact = "com.google.errorprone:error_prone_annotations",
            annotation_only = True,
            version = VERSION,
        ),
    ]

def flogger_maven():
    VERSION = "0.5.1"
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

def google_java_format_maven():
    VERSION = "1.9"
    return [
        maven_jar(
            name = "google-java-format",
            artifact = "com.google.googlejavaformat:google-java-format",
            version = VERSION,
        ),
    ]

def guava_maven():
    VERSION = "29.0-jre"
    return [
        maven_jar(
            name = "guava",
            artifact = "com.google.guava:guava",
            version = VERSION,
        ),
        maven_j2cl_jar(
            name = "com_google_guava-j2cl",
            artifact = "com.google.guava:guava",
            version = VERSION,
        ),
    ]

def junit_maven():
    VERSION = "4.13"
    return [
        maven_jar(
            name = "junit4",
            artifact = "junit:junit",
            version = VERSION,
        ),
    ]

def truth_maven():
    VERSION = "1.0.1"
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

def install_maven():
    maven_deps = []
    maven_deps += errorprone_maven()
    maven_deps += flogger_maven()
    maven_deps += google_java_format_maven()
    maven_deps += guava_maven()
    maven_deps += junit_maven()
    maven_deps += truth_maven()
    install_maven_jars(maven_deps)
