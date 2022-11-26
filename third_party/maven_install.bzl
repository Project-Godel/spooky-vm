load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@com_google_j2cl//build_defs:rules.bzl", "j2cl_maven_import_external")

def maven_jar(name, artifact, version):
    return struct(
        name = name,
        artifact = artifact,
        version = version,
        j2cl = False,
    )

def maven_j2cl_jar(name, artifact, version, deps = [], annotation_only = False):
    return struct(
        name = name,
        artifact = artifact,
        version = version,
        j2cl = True,
        annotation_only = annotation_only,
        deps = deps,
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
                deps = value.deps,
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

def checker_maven():
    VERSION = "3.27.0"
    return [
        maven_j2cl_jar(
            name = "checkerframework-j2cl",
            artifact = "org.checkerframework:checker-qual",
            version = VERSION,
            annotation_only = True,
        ),
    ]

def errorprone_maven():
    VERSION = "2.16"
    return [
        maven_jar(
            name = "errorprone",
            artifact = "com.google.errorprone:error_prone_annotations",
            version = VERSION,
        ),
        maven_j2cl_jar(
            name = "com_google_errorprone-j2cl",
            artifact = "com.google.errorprone:error_prone_annotations",
            annotation_only = True,
            version = VERSION,
        ),
    ]

def autovalue_maven():
    VERSION = "1.10.1"
    return [
        maven_jar(
            name = "auto-value",
            artifact = "com.google.auto.value:auto-value",
            version = VERSION,
        ),
        maven_jar(
            name = "auto-value-annotations",
            artifact = "com.google.auto.value:auto-value-annotations",
            version = VERSION,
        ),
        maven_j2cl_jar(
            name = "com_google_auto_value_auto_value_annotations-j2cl",
            artifact = "com.google.auto.value:auto-value-annotations",
            version = VERSION,
        ),
    ]

def guava_maven():
    VERSION = "30.1.1-jre"
    return [
        maven_jar(
            name = "guava",
            artifact = "com.google.guava:guava",
            version = VERSION,
        ),
        maven_j2cl_jar(
            name = "com_google_guava-j2cl",
            artifact = "com.google.guava:guava-gwt",
            version = VERSION,
            deps = [
                "@com_google_elemental2//:elemental2-promise-j2cl",
                "@com_google_j2cl//:jsinterop-annotations-j2cl",
                "@com_google_errorprone-j2cl",
                "@checkerframework-j2cl",
                "@j2objc-j2cl",
            ],
        ),
    ]

def j2objc_maven():
    VERSION = "1.3"
    return [
        maven_j2cl_jar(
            name = "j2objc-j2cl",
            version = VERSION,
            annotation_only = True,
            artifact = "com.google.j2objc:j2objc-annotations",
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
    VERSION = "1.1.3"
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
    ]

def jsinterop_maven():
    VERSION = "2.0.0"
    return [
        maven_j2cl_jar(
            name = "jsinterop",
            version = VERSION,
            annotation_only = True,
            artifact = "com.google.jsinterop:jsinterop-annotations",
        ),
        maven_j2cl_jar(
            name = "jsinterop-j2cl",
            version = VERSION,
            annotation_only = True,
            artifact = "com.google.jsinterop:jsinterop-annotations",
        ),
    ]

def install_maven():
    maven_deps = []
    maven_deps += autovalue_maven()
    maven_deps += checker_maven()
    maven_deps += errorprone_maven()
    maven_deps += guava_maven()
    maven_deps += junit_maven()
    maven_deps += j2objc_maven()
    maven_deps += truth_maven()
    maven_deps += jsinterop_maven()
    install_maven_jars(maven_deps)
