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
| 0.1.2 | 3 | `SuperKeyboard-v0.1.2-fix-blank-keyboard-and-toolbar.apk` | Fix blank keyboard on reopen (P0) + toolbar fills width + removed dead space above keys. In-place update over 0.1.1 (same signature). |
| 0.1.3 | 4 | `SuperKeyboard-v0.1.3-bigger-keys-clear-gesture-bar.apk` | Taller keys + bottom inset padding so the bottom row clears the gesture/nav bar. In-place update. |
| 0.2.0 | 5 | `SuperKeyboard-v0.2.0-ai-action-engine.apk` | E2 — AI action engine (5 toolbar actions wired to a user-configured OpenAI-compatible endpoint; default-OFF; preview-then-apply; Keystore-encrypted key). First INTERNET permission. In-place update (same signature). |
| 0.2.1 | 6 | `SuperKeyboard-v0.2.1-ai-test-connection.apk` | E2 Phase-4 fix — AI errors now surface HTTP status + body snippet (diagnosable); added a "Test connection" button to AI settings. In-place update. |
| 0.2.2 | 7 | `SuperKeyboard-v0.2.2-ai-network-io-fix.apk` | E2 Phase-4 fix — run the AI network call on Dispatchers.IO (was on the main thread → NetworkOnMainThreadException: swallowed as "Unexpected response" in 0.2.0, then crashed Test Connection in 0.2.1). Now main-safe + defensive catch. In-place update. |

## Size note

~38 MB is dominated by `libsqlcipher.so` shipped for all 4 ABIs (arm64-v8a, armeabi-v7a, x86, x86_64).
To roughly halve it for a single physical phone, add to `defaultConfig`:
`ndk { abiFilters += "arm64-v8a" }` (covers every Android phone since ~2017; drops the x86 emulator ABIs).
Left universal for now so the APK installs on anything.
