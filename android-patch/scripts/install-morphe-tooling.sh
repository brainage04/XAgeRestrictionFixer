#!/usr/bin/env bash
# Build the Morphe patcher and the Morphe Gradle plugin from source and publish them to Maven
# local (~/.m2/repository), so this project builds without a GitHub Packages token.
#
# Morphe publishes both only to GitHub Packages, which asks for a token with read:packages even
# for public packages. Building them from their public Git tags gives the same artifacts.
# The versions come from this project's build files, so bumping them there is enough.
#
# Overridable environment:
#   MORPHE_TOOLING_DIR  where the sources are cloned (default ~/.cache/x-age-restriction-fixer)
#   FORCE=1             rebuild even if the versions are already in Maven local
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd)"
TOOLING_DIR="${MORPHE_TOOLING_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/x-age-restriction-fixer}"
M2="${MAVEN_LOCAL:-$HOME/.m2/repository}"

patcher_version="$(sed -n 's/^morphe-patcher = "\(.*\)"/\1/p' "$PROJECT_DIR/gradle/libs.versions.toml")"
plugin_version="$(sed -n 's/.*id("app.morphe.patches") version "\(.*\)".*/\1/p' "$PROJECT_DIR/settings.gradle.kts")"
[[ -n "$patcher_version" && -n "$plugin_version" ]] || {
  echo "could not read the Morphe versions from the build files" >&2
  exit 1
}

# Both projects sign their publications with gpg; Maven local needs no signature.
init_script="$(mktemp --suffix=.gradle)"
trap 'rm -f "$init_script"' EXIT
cat >"$init_script" <<'EOF'
allprojects {
    tasks.matching { it.name.startsWith("sign") }.configureEach { enabled = false }
}
EOF

publish() {
  local repo="$1" version="$2" marker="$3"
  if [[ "${FORCE:-0}" != 1 && -e "$M2/$marker" ]]; then
    echo "$repo $version is already in Maven local"
    return
  fi
  local src="$TOOLING_DIR/$repo-$version"
  if [[ ! -d "$src/.git" ]]; then
    rm -rf "$src"
    git -c advice.detachedHead=false clone --quiet --depth 1 --branch "v$version" \
      "https://github.com/MorpheApp/$repo.git" "$src"
  fi
  echo "publishing $repo $version to Maven local"
  (cd "$src" && ./gradlew --no-daemon --quiet --init-script "$init_script" publishToMavenLocal)
  [[ -e "$M2/$marker" ]] || {
    echo "$repo $version did not appear in $M2" >&2
    exit 1
  }
}

mkdir -p "$TOOLING_DIR"
publish morphe-patcher "$patcher_version" \
  "app/morphe/morphe-patcher/$patcher_version/morphe-patcher-$patcher_version.jar"
publish morphe-patches-gradle-plugin "$plugin_version" \
  "app/morphe/patches/app.morphe.patches.gradle.plugin/$plugin_version"
