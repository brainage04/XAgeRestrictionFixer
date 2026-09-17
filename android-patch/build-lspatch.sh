#!/usr/bin/env bash
# Build the module APK and produce an LSPatch-patched com.twitter.android APK.
#
# The patched app is a merged, re-signed copy of the installed X build with the
# module embedded; no root, no LSPosed, nothing else on the device is touched.
# Re-run after every X update, then install the result over the previous patch
# (same signer, so it is an in-place update).
#
# Overridable environment:
#   ADB_SERIAL      device serial (defaults to the first adb device)
#   WORK_DIR        build directory (default ~/phone-backups/x-lspatch)
#   LSPATCH_JAR     LSPatch CLI jar     APKEDITOR_JAR  APKEditor jar
#   KEYSTORE        signing keystore    KEYSTORE_CREDENTIALS  its credentials file
#   SIGBYPASS       LSPatch signature bypass level (default 2)
#   SKIP_BUILD      set to 1 to reuse the existing module APK
set -Eeuo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
MODULE_APK="$REPO_ROOT/android-patch/app/build/outputs/apk/debug/app-debug.apk"

WORK_DIR="${WORK_DIR:-$HOME/phone-backups/x-lspatch}"
LSPATCH_JAR="${LSPATCH_JAR:-$HOME/.local/share/lspatch/lspatch.jar}"
APKEDITOR_JAR="${APKEDITOR_JAR:-$HOME/.local/share/lspatch/APKEditor.jar}"
KEYSTORE="${KEYSTORE:-$HOME/phone-backups/keys/x-age-restriction-fixer.keystore}"
KEYSTORE_CREDENTIALS="${KEYSTORE_CREDENTIALS:-$HOME/phone-backups/keys/x-age-restriction-fixer.credentials.txt}"
SIGBYPASS="${SIGBYPASS:-2}"
SKIP_BUILD="${SKIP_BUILD:-0}"
TARGET_PACKAGE="com.twitter.android"

for tool in java keytool; do
  command -v "$tool" >/dev/null || { echo "missing $tool" >&2; exit 1; }
done
for jar in "$LSPATCH_JAR" "$APKEDITOR_JAR"; do
  [[ -f "$jar" ]] || { echo "missing jar: $jar" >&2; exit 1; }
done
[[ -f "$KEYSTORE" && -f "$KEYSTORE_CREDENTIALS" ]] ||
  { echo "missing keystore or credentials: $KEYSTORE" >&2; exit 1; }

if [[ -z "${ADB_SERIAL:-}" ]]; then
  ADB_SERIAL="$(adb devices | awk '$2 == "device" { print $1; exit }')"
  [[ -n "$ADB_SERIAL" ]] || { echo "no adb device found" >&2; exit 1; }
  echo "using adb device: $ADB_SERIAL"
fi
adb_device=(adb -s "$ADB_SERIAL")

if [[ "$SKIP_BUILD" != 1 ]]; then
  echo "building module APK"
  "$REPO_ROOT/android-patch/gradlew" -p "$REPO_ROOT/android-patch" --quiet \
    testDebugUnitTest assembleDebug
fi
[[ -f "$MODULE_APK" ]] || { echo "module APK not found: $MODULE_APK" >&2; exit 1; }

version="$("${adb_device[@]}" shell dumpsys package "$TARGET_PACKAGE" |
  sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1 | tr -d '\r')"
[[ -n "$version" ]] || { echo "$TARGET_PACKAGE is not installed on $ADB_SERIAL" >&2; exit 1; }
stamp="$WORK_DIR/x-$version"
stock_dir="${STOCK_DIR:-$stamp-stock}"

if [[ "${STOCK_DIR:-}" == "" ]]; then
  mkdir -p "$stock_dir"
  echo "pulling stock split APKs for X $version"
  while IFS= read -r path; do
    "${adb_device[@]}" pull "$path" "$stock_dir/$(basename "$path")" >/dev/null
  done < <("${adb_device[@]}" shell pm path "$TARGET_PACKAGE" | tr -d '\r' | sed 's/^package://')

  # Patching an already-patched install would embed a second loader, so refuse
  # and point at the stock APKs kept from the first run (or a fresh reinstall).
  if unzip -l "$stock_dir"/*.apk 2>/dev/null | grep -q 'assets/lspatch/'; then
    rm -rf "$stock_dir"
    cat >&2 <<EOF
The installed $TARGET_PACKAGE is already an LSPatch build, so it is not a valid
source APK. Reinstall the Play build first, or reuse the stock APKs kept from an
earlier run:

  STOCK_DIR=$WORK_DIR/x-$version-stock $(basename "$0")

EOF
    exit 1
  fi
else
  [[ -d "$stock_dir" ]] || { echo "STOCK_DIR not found: $stock_dir" >&2; exit 1; }
  echo "merging stock APKs from $stock_dir"
fi

merged="$stamp-standalone.apk"
# LSPatch reads the original signature to spoof it, so the merge must keep
# META-INF and the signing block: never pass -clean-meta here.
echo "merging splits into $merged"
java -jar "$APKEDITOR_JAR" m -i "$stock_dir" -o "$merged" -f >/dev/null

mapfile -t creds < <(
  printf '%s\n' \
    "$(sed -n 's/^storepass: //p' "$KEYSTORE_CREDENTIALS")" \
    "$(sed -n 's/^alias: //p' "$KEYSTORE_CREDENTIALS")" \
    "$(sed -n 's/^keypass: //p' "$KEYSTORE_CREDENTIALS")"
)
[[ "${#creds[@]}" -eq 3 ]] || { echo "could not parse $KEYSTORE_CREDENTIALS" >&2; exit 1; }

echo "patching with the module (signature bypass level $SIGBYPASS)"
patch_log="$(java -jar "$LSPATCH_JAR" "$merged" -m "$MODULE_APK" -o "$WORK_DIR" \
  -k "$KEYSTORE" "${creds[0]}" "${creds[1]}" "${creds[2]}" -l "$SIGBYPASS" -f)"
printf '%s\n' "$patch_log" | tail -3
patched="$(printf '%s\n' "$patch_log" | sed -n 's/^Done\. Output APK: //p' | tail -1)"
[[ -f "$patched" ]] || { echo "could not determine the patched APK path" >&2; exit 1; }
echo
echo "patched APK: $patched"
echo "stock APKs:  $stock_dir"
echo "install (uninstall the Play build first — different signature):"
echo "  adb -s $ADB_SERIAL uninstall $TARGET_PACKAGE"
echo "  adb -s $ADB_SERIAL install \"$patched\""
