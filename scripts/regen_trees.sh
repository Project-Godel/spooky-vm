#!/bin/sh
bazel build java/se/jsannemo/spooky/compiler/testing:RegenParseTrees && ./bazel-bin/java/se/jsannemo/spooky/compiler/testing/RegenParseTrees
