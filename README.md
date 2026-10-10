<p align="center">
  <img src="logo.svg" width="128" alt="Omashare">
</p>

# Omashare

Omashare sends files between your devices. A Linux computer and an Android phone, or two phones. There is no account. The other person accepts before a file is saved.

The Linux program is [omashare](https://github.com/aerorohit/omashare). This page is the Android app.

## Install

Phones need a 64-bit ARM processor, which is what almost every phone since 2019 uses.

Pick one install path and stay with it. The GitHub download, F-Droid, and Play each sign the app with a different key, so Android will not update from one onto another. To switch, uninstall Omashare first. Files already saved in Downloads stay.

### Download the APK

Releases are published on GitHub:

<https://github.com/aerorohit/omashare-android/releases>

Download `omashare-*-arm64.apk`, open it, and allow your browser or files app to install unknown apps when Android asks. The first time, also allow nearby devices, Bluetooth, and notifications.

### F-Droid

Not listed yet. The catalog entry to submit is `fdroid/org.omashare.app.yml`. After it is accepted, install from F-Droid and ignore the GitHub APK.

### Play Store

Not listed yet. The file to upload is the `.aab` from the same GitHub release. After the store listing is live, install from Play and ignore the other two.

## Use

Turn sharing on with the control at the top of the app, or with the Omashare tile in quick settings. Sharing looks for nearby devices, so turn it off when you are done. That stops discovery and the ongoing notification.

Devices on the same Wi-Fi show up under Nearby, marked LAN. A device seen over Bluetooth is marked Bluetooth. Bluetooth only helps the phones find each other. The file itself still transfers over the network.

To send, tap **Select files**, tap a device, then **Send**. You can also share from another app and pick Omashare. The files are already selected. Choose a device and send.

When someone sends you a file, accept or decline on the notification. If Omashare is open, the same choice appears in the app. Accepted files are saved in the Downloads folder, in `Omashare`.

Tap a finished transfer in History to open that file. Settings has the theme, a way to add the quick settings tile, and a short about section.

## Permissions

Omashare asks only for what discovery and saving files need.

- Nearby Wi-Fi, or location on Android 12 and older, so other devices on the network can be found.
- Bluetooth, so two phones can find each other without the same Wi-Fi. It is not used to track location.
- Notifications, for the sharing status and for incoming files.
- Storage on Android 9 and older, to write into Downloads.

## Privacy

Omashare has no account and no analytics. Files you send go to the device you chose. Received files stay in Downloads on your phone. See [PRIVACY.md](PRIVACY.md).

## For developers

The app is the Android shell. Discovery, consent, and the file bytes live in the Rust repo [omashare](https://github.com/aerorohit/omashare), linked in through UniFFI (`libomashare_ffi.so`). Do not edit `app/src/main/java/uniffi/`.

### Build a debug APK

JDK 17 or 21, Android SDK 35, and NDK `27.2.12479018`. Gradle 8.11 does not run on JDK 26.

```bash
export JAVA_HOME=/path/to/jdk-21   # when java -version is 26 or newer
./build-native.sh                  # sibling ../omashare, or set OMASHARE_ROOT
./gradlew :app:assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. `build-native.sh` needs `cargo-ndk` and writes `app/src/main/jniLibs/arm64-v8a/libomashare_ffi.so`. That library is not committed.

### Publish

One keystore signs the GitHub APK and the Play upload. Create it once, on a machine you control, and keep a backup. Losing it means you cannot update the GitHub or Play listing.

```bash
./scripts/keystore.sh
```

The script prints the four values GitHub Actions needs as repository secrets:

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0` of the keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Store password |
| `ANDROID_KEY_ALIAS` | Key alias (`omashare` if you accepted the default) |
| `ANDROID_KEY_PASSWORD` | Key password |

Do not commit the keystore. `*.keystore` is gitignored.

Build a signed APK and an Android App Bundle locally:

```bash
export OMASHARE_KEYSTORE="$PWD/dist/omashare-release.keystore"
export OMASHARE_KEYSTORE_PASSWORD=...
export OMASHARE_KEY_ALIAS=omashare
export OMASHARE_KEY_PASSWORD=...
./scripts/release.sh
```

Artifacts land in `dist/out/`. `./scripts/signing-cert.sh` prints the certificate SHA-256. F-Droid needs that if a later build should match this signature.

Ship it by tagging the commit whose `versionName` in `app/build.gradle.kts` matches the tag, with `versionCode` higher than the previous release.

```bash
# versionName is 0.1.0, versionCode is 1
git tag v0.1.0
git push origin v0.1.0
```

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds the native library, signs the APK and the AAB, and attaches both to the GitHub release. The tag must match `versionName` (`v0.1.0` for `0.1.0`).

**F-Droid.** After `v0.1.0` exists, open a merge request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding `fdroid/org.omashare.app.yml`. Pin `srclibs` to the `omashare` commit that this app was built with. F-Droid compiles from source and usually signs with its own key, which is why that install does not update a sideloaded APK. Store text is in `fastlane/metadata/android/en-US/`.

**Play.** In Play Console, create the app, upload `omashare-*.aab`, and use the [privacy policy](PRIVACY.md) URL on GitHub (`https://github.com/aerorohit/omashare-android/blob/main/PRIVACY.md`) until you have a site. Play App Signing wraps the upload key, so Play installs also stay on their own update track. Bump `versionCode` for every upload.
