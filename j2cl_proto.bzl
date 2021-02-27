# Adapted from https://github.com/google/j2cl-protobuf/blob/master/java/com/google/protobuf/contrib/j2cl/j2cl_proto.bzl
# Copyright 2019 Google LLC, Licensed under the Apache License, Version 2.0
def _unarchived_jar_path(path):
    """Get the path of the unarchived directory.

    Args:
      path: The path to the archive file.

    Returns:
      The path to the directory that this file will expand to.
    """
    if not path.endswith(".srcjar"):
        fail("Path %s doesn't end in \".srcjar\"" % path)
    return path[0:-7]

def _j2cl_proto_library_rule_impl(ctx):
    if len(ctx.attr.deps) != 1:
        fail("Only one deps entry allowed")

    proto_info = ctx.attr.deps[0][ProtoInfo]
    srcs = proto_info.direct_sources
    transitive_srcs = proto_info.transitive_sources

    jar_archive = None
    if srcs:
        jar_archive = ctx.actions.declare_file(ctx.label.name + "_j2cl.srcjar")
        protoc_command_template = """
          set -e -o pipefail

          rm -rf {dir}
          mkdir -p {dir}

          {protoc} --plugin=protoc-gen-j2cl_protobuf={protoc_plugin} \
                        --proto_path=. \
                        --proto_path={genfiles} \
                        --j2cl_protobuf_out={dir} \
                        {proto_sources}
          java_files=$(find {dir} -name '*.java')
          chmod -R 664 $java_files
          {jar} -cf {jar_file} -C {dir} .
          """
        protoc_command = protoc_command_template.format(
            dir = _unarchived_jar_path(jar_archive.path),
            protoc = ctx.executable._protocol_compiler.path,
            protoc_plugin = ctx.executable._protoc_gen_j2cl.path,
            genfiles = ctx.configuration.genfiles_dir.path,
            proto_sources = " ".join([s.path for s in srcs]),
            jar = ctx.executable._jar.path,
            jar_file = jar_archive.path,
        )
        print(protoc_command, transitive_srcs)
        resolved_tools, resolved_command, input_manifest = ctx.resolve_command(
            command = protoc_command,
            tools = [
                ctx.attr._protocol_compiler,
                ctx.attr._protoc_gen_j2cl,
                ctx.attr._jar,
            ],
        )

        ctx.actions.run_shell(
            command = resolved_command,
            inputs = transitive_srcs,
            tools = resolved_tools,
            outputs = [jar_archive],
            input_manifests = input_manifest,
            progress_message = "Generating J2CL proto files",
        )
        return [DefaultInfo(files = depset([jar_archive]))]

j2cl_proto_library = rule(
    implementation = _j2cl_proto_library_rule_impl,
    attrs = {
        "deps": attr.label_list(
            providers = [ProtoInfo],
        ),
        "_zip": attr.label(
            cfg = "host",
            executable = True,
            default = Label("@bazel_tools//tools/zip:zipper"),
        ),
        "_protocol_compiler": attr.label(
            executable = True,
            cfg = "host",
            default = Label("@com_google_protobuf//:protoc"),
        ),
        "_protoc_gen_j2cl": attr.label(
            executable = True,
            cfg = "host",
            default = Label("@com_google_j2cl_protobuf//java/com/google/protobuf/contrib/j2cl/internal_do_not_use:J2CLProtobufCompiler"),
        ),
        "_jar": attr.label(
            executable = True,
            cfg = "host",
            default = Label("@bazel_tools//tools/jdk:jar"),
        ),
    },
    fragments = ["java"],
)
