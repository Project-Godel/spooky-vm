#!/bin/sh
bazel build java/se/jsannemo/spooky/compiler/testing:RegenTokenizations && ./bazel-bin/java/se/jsannemo/spooky/compiler/testing/RegenTokenizations
