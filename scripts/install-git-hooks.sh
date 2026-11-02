#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
git -C "$PROJECT_DIR" rev-parse --git-dir > /dev/null
chmod +x "$PROJECT_DIR/.githooks/prepare-commit-msg" "$PROJECT_DIR/.githooks/commit-msg"
git -C "$PROJECT_DIR" config --local core.hooksPath .githooks
printf '%s\n' 'SignKit commit hooks enabled for this repository.'
