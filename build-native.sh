#!/usr/bin/env bash
set -euo pipefail
REPO="$(cd "$(dirname "$0")" && pwd)"
OMASHARE_ROOT="${OMASHARE_ROOT:-$REPO/../omashare}"
if [[ -f "$OMASHARE_ROOT/.cargo/env" ]]; then
  # rustup was installed with CARGO_HOME in the Rust workspace
  # shellcheck disable=SC1091
  . "$OMASHARE_ROOT/.cargo/env"
elif [[ -f "${HOME}/.cargo/env" ]]; then
  # shellcheck disable=SC1091
  . "${HOME}/.cargo/env"
fi
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME:-${HOME}/Android/Sdk}/ndk/27.2.12479018}"
if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME not found: $ANDROID_NDK_HOME" >&2
  exit 1
fi
if [[ ! -d "$OMASHARE_ROOT" ]]; then
  echo "Omashare Rust workspace not found: $OMASHARE_ROOT" >&2
  echo "Set OMASHARE_ROOT to the sibling omashare repo." >&2
  exit 1
fi
cd "$OMASHARE_ROOT"
if command -v rustup >/dev/null; then
  rustup target add aarch64-linux-android
fi
cargo ndk -t arm64-v8a -P 26 -o "$REPO/app/src/main/jniLibs" -- build -p omashare-ffi --release
SO="$REPO/app/src/main/jniLibs/arm64-v8a/libomashare_ffi.so"
find "$REPO/app/src/main/jniLibs" -name "*.so" ! -name "libomashare_ffi.so" -delete
# Generate before strip: llvm-strip drops UniFFI metadata bindgen needs.
cargo run -p omashare-ffi --bin uniffi-bindgen -- generate \
  --library "$SO" \
  --language kotlin \
  --no-format \
  --out-dir "$REPO/app/src/main/java"
STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
if [[ -x "$STRIP" ]]; then
  "$STRIP" --strip-unneeded "$SO"
fi
echo "Wrote $SO and UniFFI Kotlin bindings"
