#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Uso: $0 /percorso/Garanzia.apk" >&2
  exit 2
fi

EXPECTED_PACKAGE="com.milanstevic.garanzia"
EXPECTED_VERSION_NAME="1.0.0"
EXPECTED_VERSION_CODE="5"
EXPECTED_CERT_SHA256="34b9327e4500c642afce16db2304c70dc9e638e0850ef4dacb4a36bd0be882f4"

: "${ANDROID_HOME:?ANDROID_HOME non impostato}"

APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -1)"
ZIPALIGN="$(find "$ANDROID_HOME/build-tools" -type f -name zipalign | sort -V | tail -1)"
APKANALYZER="$(find "$ANDROID_HOME" -type f -name apkanalyzer | head -1 || true)"
AAPT2="$(find "$ANDROID_HOME/build-tools" -type f -name aapt2 | sort -V | tail -1)"

test -x "$APKSIGNER"
test -x "$ZIPALIGN"

BUILD_TOOLS_DIR="$(dirname "$APKSIGNER")"
if [[ -d "$BUILD_TOOLS_DIR/lib64" ]]; then
  export LD_LIBRARY_PATH="$BUILD_TOOLS_DIR/lib64:${LD_LIBRARY_PATH:-}"
fi

if [[ -n "$APKANALYZER" && -x "$APKANALYZER" ]]; then
  PACKAGE="$("$APKANALYZER" manifest application-id "$APK")"
  VERSION_NAME="$("$APKANALYZER" manifest version-name "$APK")"
  VERSION_CODE="$("$APKANALYZER" manifest version-code "$APK")"
else
  test -x "$AAPT2"
  BADGING="$("$AAPT2" dump badging "$APK")"
  PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")"
  VERSION_CODE="$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")"
  VERSION_NAME="$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")"
fi

[[ "$PACKAGE" == "$EXPECTED_PACKAGE" ]]
[[ "$VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]]
[[ "$VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]]

VERIFY_OUTPUT="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" <<<"$VERIFY_OUTPUT"
grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" <<<"$VERIFY_OUTPUT"

SIGNER_COUNT="$(grep -c '^Signer #[0-9].*certificate DN:' <<<"$VERIFY_OUTPUT" || true)"
[[ "$SIGNER_COUNT" == "1" ]]

ACTUAL_CERT="$(
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$VERIFY_OUTPUT"     | tr '[:upper:]' '[:lower:]'     | tr -d ':[:space:]'
)"
[[ "$ACTUAL_CERT" == "$EXPECTED_CERT_SHA256" ]]

"$ZIPALIGN" -c -P 16 -v 4 "$APK" >/dev/null

echo "APK release verificato correttamente."
echo "Package: $PACKAGE"
echo "Versione: $VERSION_NAME ($VERSION_CODE)"
echo "Certificato SHA-256: $ACTUAL_CERT"
sha256sum "$APK"
