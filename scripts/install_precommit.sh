#!/usr/bin/env bash

set -e
scripts_path=`dirname "$0"`
cp $scripts_path/git-pre-commit $scripts_path/../.git/hooks/pre-commit
