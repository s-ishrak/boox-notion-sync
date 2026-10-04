#!/usr/bin/env bash
# Bumps versionName/versionCode in app/build.gradle.kts.
# Usage: scripts/bump-version.sh X.Y.Z
set -euo pipefail

cd "$(dirname "$0")/.."

GRADLE_FILE="app/build.gradle.kts"
NEW_VERSION_NAME="${1:-}"

if [ -z "$NEW_VERSION_NAME" ]; then
    echo "Usage: make bump VERSION=X.Y.Z" >&2
    exit 1
fi

if ! [[ "$NEW_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: VERSION must be a semantic version X.Y.Z (got '$NEW_VERSION_NAME')" >&2
    exit 1
fi

CURRENT_VERSION_CODE=$(grep -E '^\s*versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
CURRENT_VERSION_NAME=$(grep -E '^\s*versionName = "' "$GRADLE_FILE" | sed -E 's/.*versionName = "([^"]+)".*/\1/')

if [ "$NEW_VERSION_NAME" = "$CURRENT_VERSION_NAME" ]; then
    echo "Error: versionName is already $CURRENT_VERSION_NAME" >&2
    exit 1
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

sed_inplace() {
    if sed --version >/dev/null 2>&1; then
        sed -i -E "$1" "$2"
    else
        sed -i '' -E "$1" "$2"
    fi
}

sed_inplace "s/versionCode = [0-9]+/versionCode = $NEW_VERSION_CODE/" "$GRADLE_FILE"
sed_inplace "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VERSION_NAME\"/" "$GRADLE_FILE"

echo "Bumped $CURRENT_VERSION_NAME (code $CURRENT_VERSION_CODE) -> $NEW_VERSION_NAME (code $NEW_VERSION_CODE)"
echo
echo "Next steps (see CONTRIBUTING.md):"
echo "  1. Review: git diff $GRADLE_FILE"
echo "  2. Commit: git commit -am \"chore: bump version to $NEW_VERSION_NAME\""
echo "  3. Tag:    git tag v$NEW_VERSION_NAME"
echo "  4. Build:  ./gradlew assembleStandardRelease --no-daemon"
