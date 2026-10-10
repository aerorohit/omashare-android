#!/usr/bin/env bash
# Build a signed arm64 release APK and an Android App Bundle.
# The APK is what GitHub users install. The AAB is what Play Console takes.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

java_major="$(java -version 2>&1 | sed -n 's/.* version "\([0-9][0-9]*\).*/\1/p' | head -1)"
if [[ -n "$java_major" && "$java_major" -ge 25 ]]; then
  echo "JDK $java_major cannot run this Gradle project. Set JAVA_HOME to JDK 17 or 21." >&2
  exit 1
fi

for var in OMASHARE_KEYSTORE OMASHARE_KEYSTORE_PASSWORD OMASHARE_KEY_ALIAS OMASHARE_KEY_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing $var. Create a keystore with ./scripts/keystore.sh, then export the four OMASHARE_* variables." >&2
    exit 1
  fi
done
if [[ ! -f "$OMASHARE_KEYSTORE" ]]; then
  echo "Keystore not found: $OMASHARE_KEYSTORE" >&2
  exit 1
fi

version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
if [[ -z "$version_name" ]]; then
  echo "Could not read versionName from app/build.gradle.kts" >&2
  exit 1
fi
if [[ -n "${GITHUB_REF_NAME:-}" ]]; then
  tag="${GITHUB_REF_NAME#v}"
  if [[ "$tag" != "$version_name" ]]; then
    echo "Tag $GITHUB_REF_NAME does not match versionName $version_name in app/build.gradle.kts" >&2
    exit 1
  fi
fi

if [[ "${SKIP_NATIVE:-}" != "1" ]]; then
  OMASHARE_ROOT="${OMASHARE_ROOT:-$ROOT/../omashare}" ./build-native.sh
fi

chmod +x ./gradlew
./gradlew --no-daemon :app:assembleRelease :app:bundleRelease

apk="$(find app/build/outputs/apk/release -name '*release*.apk' ! -name '*unsigned*' | head -1)"
aab="$(find app/build/outputs/bundle/release -name '*.aab' | head -1)"
if [[ -z "$apk" || -z "$aab" ]]; then
  echo "Signed release outputs were not produced." >&2
  exit 1
fi

out="$ROOT/dist/out"
mkdir -p "$out"
cp "$apk" "$out/omashare-${version_name}-arm64.apk"
cp "$aab" "$out/omashare-${version_name}.aab"
echo "Wrote $out/omashare-${version_name}-arm64.apk"
echo "Wrote $out/omashare-${version_name}.aab"
