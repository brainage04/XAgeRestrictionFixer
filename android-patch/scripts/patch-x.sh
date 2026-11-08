#!/usr/bin/env bash
# Patch an X APK (or .apkm/.apks/.xapk split bundle) with this project's patches using the
# Morphe CLI, signed with your own keystore so the result installs over your current build.
#
# Usage: scripts/patch-x.sh INPUT OUTPUT.apk
#
# Overridable environment:
#   MORPHE_CLI            Morphe Desktop jar (default ~/.local/share/morphe/morphe-desktop-all.jar)
#   PATCHES               .mpp to apply (default: the newest patches/build/libs/patches-*.mpp)
#   KEYSTORE              signing keystore (default ~/phone-backups/keys/x-age-restriction-fixer.keystore)
#   KEYSTORE_CREDENTIALS  file with "storepass:", "alias:" and "keypass:" lines (default: next to it)
set -Eeuo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd)"
MORPHE_CLI="${MORPHE_CLI:-$HOME/.local/share/morphe/morphe-desktop-all.jar}"
KEYSTORE="${KEYSTORE:-$HOME/phone-backups/keys/x-age-restriction-fixer.keystore}"
KEYSTORE_CREDENTIALS="${KEYSTORE_CREDENTIALS:-${KEYSTORE%.keystore}.credentials.txt}"

[[ $# -eq 2 ]] || { echo "usage: $(basename "$0") INPUT OUTPUT.apk" >&2; exit 2; }
input="$1"
output="$2"

if [[ -z "${PATCHES:-}" ]]; then
  PATCHES="$(ls -t "$PROJECT_DIR"/patches/build/libs/patches-*.mpp 2>/dev/null | grep -v -e sources -e javadoc | head -1 || true)"
fi
for file in "$input" "$MORPHE_CLI" "$PATCHES" "$KEYSTORE" "$KEYSTORE_CREDENTIALS"; do
  [[ -f "$file" ]] || { echo "missing file: ${file:-patches .mpp (build it first)}" >&2; exit 1; }
done

value() { sed -n "s/^$1: //p" "$KEYSTORE_CREDENTIALS"; }
storepass="$(value storepass)"
alias_name="$(value alias)"
keypass="$(value keypass)"
[[ -n "$storepass" && -n "$alias_name" && -n "$keypass" ]] || {
  echo "could not read storepass/alias/keypass from $KEYSTORE_CREDENTIALS" >&2
  exit 1
}

# The passwords go to the CLI in an argument file (picocli's @file), so they never appear on a
# command line or in the process list.
argfile="$(mktemp)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$argfile" "$work_dir"' EXIT
chmod 600 "$argfile"
printf '%s\n' \
  "--keystore=$KEYSTORE" \
  "--keystore-entry-alias=$alias_name" \
  "--keystore-password=$storepass" \
  "--keystore-entry-password=$keypass" >"$argfile"

mkdir -p "$(dirname -- "$output")"
java -jar "$MORPHE_CLI" patch \
  --patches="$PATCHES" \
  --signer=xagefixer \
  --temporary-files-path="$work_dir" \
  --result-file="${output%.apk}.result.json" \
  --out="$output" \
  "@$argfile" \
  "$input"

echo "patched APK: $output"
