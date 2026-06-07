# SuperKeyboard — APK Build & Sideload (project-specific)

Type **A — Native Android** (Kotlin DSL). Full reference: `docs/mobile/Android_Build_and_Sideload.md`.

## Canonical debug signature (MUST match on every build)

```
CN=SuperKeyboard Debug, OU=Personal, O=SuperKeyboard
SHA-256: 53c0f25398e291eccd15c1f5f23b5d9c23b18219d41d83efaada564ea73302ff
```

Keystore: `keystore/superkeyboard-debug.keystore` (committed — store/key pass `superkeyboard-debug`, alias `superkeyboard`).
If a build ever prints a different SHA-256, **stop** — the keystore wiring broke. A mismatch means the phone
will reject the update with "App not installed (signature mismatch)".

> The pre-keystore build (April 2026, `app-debug.apk`) was signed with Gradle's auto-generated
> `~/.android/debug.keystore` (`CN=Android Debug`, SHA-256 `2498d953…`). That key is machine-local and is
> the reason early sideloads failed. The **first** install of a `53c0f253…`-signed APK over that old build
> requires a one-time uninstall; every build after that updates in place.

## Host env (this Mac)

```bash
export ANDROID_HOME=/Users/michaelperry/Library/Android/sdk
export JAVA_HOME=/opt/homebrew/opt/openjdk@17        # AGP/native → Java 17
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

## Per-build one-liner

```bash
cd /Users/michaelperry/Desktop/Files/03_SoftwareProjects/_Active/Claude/SuperKeyboard && \
export ANDROID_HOME=/Users/michaelperry/Library/Android/sdk && \
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && \
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH && \
./gradlew clean assembleDebug --no-daemon && \
VERSION=$(grep versionName app/build.gradle.kts | head -1 | grep -oE '"[^"]+"' | tr -d '"') && \
cp app/build/outputs/apk/debug/app-debug.apk apk-releases/SuperKeyboard-v${VERSION}-<descriptor>.apk && \
$ANDROID_HOME/build-tools/36.1.0/apksigner verify --print-certs \
    apk-releases/SuperKeyboard-v${VERSION}-<descriptor>.apk | grep -i "SHA-256 digest" && \
pkill -f "GradleWorkerMain|KotlinCompileDaemon" 2>/dev/null
```

Bump **both** `versionCode` (monotonic int) and `versionName` in `app/build.gradle.kts` every build.

## Version log

| versionName | versionCode | APK | Notes |
|---|---|---|---|
| 0.1.1 | 2 | `SuperKeyboard-v0.1.1-sideload-keystore-setup.apk` | First build with persistent keystore. ~38 MB (SQLCipher native libs ×4 ABIs). |

## Size note

~38 MB is dominated by `libsqlcipher.so` shipped for all 4 ABIs (arm64-v8a, armeabi-v7a, x86, x86_64).
To roughly halve it for a single physical phone, add to `defaultConfig`:
`ndk { abiFilters += "arm64-v8a" }` (covers every Android phone since ~2017; drops the x86 emulator ABIs).
Left universal for now so the APK installs on anything.
