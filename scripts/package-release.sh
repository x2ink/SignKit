#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
release_tag=${1:?Usage: package-release.sh vX.Y.Z}
version=${release_tag#v}
if [ "$release_tag" = "$version" ] || ! printf '%s\n' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    printf 'Expected a version tag such as v1.0.0, got: %s\n' "$release_tag" >&2
    exit 1
fi

release_dir="$PROJECT_DIR/build/releases/$release_tag"
mkdir -p "$release_dir"
cp "$PROJECT_DIR/signkit/build/outputs/aar/signkit-release.aar" "$release_dir/signkit-$version.aar"
cp "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" "$release_dir/signkit-demo-$version-debug.apk"
cd "$release_dir"
if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "signkit-$version.aar" "signkit-demo-$version-debug.apk" > SHA256SUMS
else
    shasum -a 256 "signkit-$version.aar" "signkit-demo-$version-debug.apk" > SHA256SUMS
fi
printf 'Release assets ready: %s\n' "$release_dir"
