#!/bin/sh

COMMIT_TYPES='feat fix docs style refactor perf test build ci chore revert'

is_conventional_subject() {
    printf '%s\n' "$1" | LC_ALL=C grep -Eq \
        '^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([[:alnum:]_./-]+\))?!?: [^[:space:]].*'
}

commit_subject() {
    sed '/^[[:space:]]*#/d; /^[[:space:]]*$/d' "$1" | head -n 1
}

is_generated_subject() {
    case "$1" in 'Merge '*|'Revert "'*) return 0 ;; *) return 1 ;; esac
}
