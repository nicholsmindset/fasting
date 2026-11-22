#!/usr/bin/env bash
# Sign a beta APK and verify it against the expected distribution certificate.
set -euo pipefail
umask 077
input=${1:?unsigned APK required}
output=${2:?destination APK required}
: "${ANDROID_HOME:?Android SDK required}" "${BETA_KEYSTORE:?}" "${BETA_KEY_ALIAS:?}"
: "${BETA_STORE_PASSWORD:?}" "${BETA_KEY_PASSWORD:?}" "${BETA_CERT_SHA256:?}"
build_tools="$ANDROID_HOME/build-tools/36.0.0"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

package=$("$build_tools/aapt" dump badging "$input" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
[[ "$package" == com.fastingshift.app.beta ]] || { echo 'Unexpected beta package' >&2; exit 1; }
"$build_tools/zipalign" -P 16 -f 4 "$input" "$work/aligned.apk"
"$build_tools/apksigner" sign --ks "$BETA_KEYSTORE" --ks-key-alias "$BETA_KEY_ALIAS" \
    --ks-pass env:BETA_STORE_PASSWORD --key-pass env:BETA_KEY_PASSWORD \
    --out "$work/signed.apk" "$work/aligned.apk"
"$build_tools/zipalign" -P 16 -c 4 "$work/signed.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$work/signed.apk" > "$work/certificates"
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$work/certificates")
expected=$(printf '%s' "$BETA_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ && "$actual" == "$expected" ]] || { echo 'Beta signer mismatch' >&2; exit 1; }
mkdir -p "$(dirname "$output")"
cp "$work/signed.apk" "$output"
