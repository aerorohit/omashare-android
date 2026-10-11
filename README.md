<p align="center">
  <img src="logo.svg" width="128" alt="Omashare">
</p>

# Omashare

Start a transfer with someone nearby, then walk away. The file keeps moving in the background, over mobile data if you leave Wi-Fi. You do not have to stand next to each other until it finishes.

The transfer is peer to peer. The file goes from one device straight to the other. There is no account server, and no server keeps a copy. The connection is encrypted, so anything in between cannot read the file. On the same Wi-Fi that path is the local network, which is why large files are fast. If you walk out of range, the two devices find another path, including over mobile data, and the transfer continues.

When a direct path is not available, a relay helps the two devices reach each other. It forwards encrypted traffic. It does not store the file, and it cannot open it. Discovery itself stays on the local network or Bluetooth.

Two phones can do this with each other, and a phone can do it with a Linux computer running [omashare](https://github.com/aerorohit/omashare). The other person accepts before anything is saved. Accepted files land in Downloads, in `Omashare`, not locked inside the app.

Find someone on the local network, or over Bluetooth when you are not on the same Wi-Fi. Bluetooth is only how you meet. The file still travels over the network. Send one file or several, from Omashare or from the share menu in another app. Turn discovery off from the app or the quick settings tile when you are not exchanging files. That stops the scanning. A transfer that is already running keeps going if you leave the app.

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

Turn sharing on with the control at the top of the app, or with the Omashare tile in quick settings. Nearby devices show whether they were found on the local network or over Bluetooth.

Tap **Select files**, tap a device, then **Send**. Or share from another app and pick Omashare. The files are already selected. Choose a device and send.

When someone sends you a file, accept or decline on the notification. You can leave the app after that. The transfer continues, including after you leave Wi-Fi. Tap a finished transfer in History to open the file.

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
./build-native.sh                  # submodule omashare/, sibling ../omashare, or OMASHARE_ROOT
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
# versionName is 0.1.4, versionCode is 5
git tag v0.1.4
git push origin v0.1.4
```

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds the native library from the `omashare` submodule, signs the APK and the AAB, and attaches both to the GitHub release. The tag must match `versionName` (`v0.1.4` for `0.1.4`).

**F-Droid.** After a tag exists, open a merge request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding `fdroid/org.omashare.app.yml`. The native code is the `omashare` git submodule. F-Droid compiles from source and usually signs with its own key, which is why that install does not update a sideloaded APK. Store text is in `fastlane/metadata/android/en-US/`.

**Play.** In Play Console, create the app, upload `omashare-*.aab`, and use the [privacy policy](PRIVACY.md) URL on GitHub (`https://github.com/aerorohit/omashare-android/blob/main/PRIVACY.md`) until you have a site. Play App Signing wraps the upload key, so Play installs also stay on their own update track. Bump `versionCode` for every upload.
