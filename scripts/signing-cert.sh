#!/usr/bin/env bash
# Print the release certificate SHA-256. F-Droid reproducible builds use this
# as AllowedAPKSigningKeys so their APK matches the GitHub one.
set -euo pipefail
if [[ -z "${OMASHARE_KEYSTORE:-}" || -z "${OMASHARE_KEY_ALIAS:-}" ]]; then
  echo "Set OMASHARE_KEYSTORE and OMASHARE_KEY_ALIAS." >&2
  exit 1
fi
keytool -list -v \
  -keystore "$OMASHARE_KEYSTORE" \
  -alias "$OMASHARE_KEY_ALIAS"
