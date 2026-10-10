#!/usr/bin/env bash
# Create the release keystore used for GitHub APKs and the Play upload.
# Run once. Back up the file and the passwords. They are not recoverable.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${OMASHARE_KEYSTORE:-$ROOT/dist/omashare-release.keystore}"
ALIAS="${OMASHARE_KEY_ALIAS:-omashare}"

if [[ -e "$KEYSTORE" ]]; then
  echo "Keystore already exists: $KEYSTORE" >&2
  echo "Refusing to overwrite it." >&2
  exit 1
fi

if ! command -v keytool >/dev/null; then
  echo "keytool is missing. Install a JDK 17 or 21 and put it on PATH." >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE")"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Omashare, O=aerorohit"

echo
echo "Keystore: $KEYSTORE"
echo "Alias:    $ALIAS"
echo
echo "Add these GitHub Actions secrets on aerorohit/omashare-android:"
echo "  ANDROID_KEYSTORE_BASE64   (run: base64 -w0 \"$KEYSTORE\")"
echo "  ANDROID_KEYSTORE_PASSWORD"
echo "  ANDROID_KEY_ALIAS         $ALIAS"
echo "  ANDROID_KEY_PASSWORD"
echo
echo "Keep a copy of the keystore offline. Git ignores *.keystore."
