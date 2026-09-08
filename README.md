# Omashare Android

Compose client for the Omashare protocol. Protocol, discovery, consent, and
bytes live in the sibling Rust repo [`omashare`](../omashare) (`omashare-core`
via UniFFI). This repo is UI and Android glue only.

## Build

Requires **JDK 17 or 21** (Gradle 8.11 cannot compile Kotlin DSL on JDK 26),
Android SDK (API 35), and NDK `27.2.12479018` (see `local.properties` /
`ANDROID_HOME`).

```bash
export JAVA_HOME=/path/to/jdk-21   # if `java -version` is 26+
./gradlew :app:assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The app ships
`arm64-v8a` only (`libomashare_ffi.so` under `app/src/main/jniLibs`).

## Native library

`build-native.sh` builds `omashare-ffi` with `cargo ndk` and regenerates
UniFFI Kotlin into `app/src/main/java/uniffi/omashare/`. Do not edit those
generated files.

```bash
# default: sibling ../omashare
./build-native.sh

# or
OMASHARE_ROOT=/path/to/omashare ./build-native.sh
```

Needs `cargo-ndk` and the NDK path above.

## Run

Install on a phone, grant nearby Wi-Fi (or location on older Android), Bluetooth
scan/advertise/connect, and notifications. Leave sharing on. Peers appear from
LAN (NSD) or Bluetooth; tap **Send files**, then a peer. Incoming offers show
in-app and as notification actions. Bluetooth is discovery only — the file still
goes over iroh (LAN, WAN, or n0 relay).
