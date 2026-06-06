# Project Design Reference — SuperKeyboard

> **Version:** 1.0
> **Last Updated:** 2026-06-06
> **Status:** Current (early development — Phase 1 foundation landed)

---

## 1. Project Overview

**SuperKeyboard** is a native Android **IME (Input Method Editor / custom soft keyboard)** focused on being *the most helpful keyboard while remaining completely private*. It provides standard text entry plus an AI assistance toolbar (translate, rewrite, summarize, dictation, read-aloud, presets), gesture typing, an emoji picker, theming, and an **encrypted clipboard history** that never leaves the device.

**Problem it solves:** Mainstream "smart" keyboards trade privacy for features — keystrokes, clipboard contents, and typing data are often sent to the cloud. SuperKeyboard delivers helpful features (including AI ones) while keeping data on-device; any AI capability is designed to run on-device or against the user's *own* private endpoint.

**Current state:** Early development. Phase 1 foundation has shipped — a working IME with an encrypted clipboard. AI-toolbar actions are scaffolded (`AIToolbarView`) but most are not yet wired to a real engine.

---

## 2. Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Platform | Android (native) | compileSdk/targetSdk 35, minSdk 24 |
| Language | Kotlin | 2.0.21 |
| Build | Gradle (Kotlin DSL) + AGP | AGP 8.7.3, wrapper Gradle 8.11.1 |
| UI (settings app) | Jetpack Compose + Material 3 | Compose BOM 2024.12.01, material3 1.3.1 |
| UI (keyboard surface) | Classic Android Views (custom `KeyboardView`, drawn in the IME service) | — |
| Navigation (settings) | Navigation-Compose | 2.8.5 |
| Local DB | Room | 2.6.1 |
| DB encryption | SQLCipher (`net.zetetic:sqlcipher-android`) | 4.6.0 |
| Key management | Android Keystore (AES-256-GCM wrapping a random DB passphrase) | platform |
| Settings storage | Jetpack DataStore (Preferences) | 1.1.1 |
| Annotation processing | KSP (for Room) | 2.0.21-1.0.28 |
| Lists | RecyclerView (clipboard bottom sheet) | 1.3.2 |
| Testing | None yet — JVM unit tests (`app/src/test/`) and instrumented tests (`app/src/androidTest/`) are not present; see KIT_DEVIATIONS.md | — |

**JVM target:** 17 (source & target compatibility). **Java to build:** JDK 17+ (Gradle 8.11.1 supports JDK 17–21).

---

## 3. System Architecture

This is a single-process Android app with no backend, no network calls, and no accounts. Two user-facing entry points share one application instance.

```
┌─────────────────────────────────────────────────────────────┐
│ SuperKeyboardApp (Application)                               │
│  └── clipboardDatabase  (lazy, SQLCipher-encrypted Room DB)  │
└───────────────┬─────────────────────────┬───────────────────┘
                │                          │
   ┌────────────▼───────────┐   ┌──────────▼─────────────────┐
   │ KeyboardService        │   │ SettingsActivity (LAUNCHER)│
   │ (InputMethodService)   │   │  Compose + Navigation       │
   │  • KeyboardView (Views)│   │  • Appearance / Clipboard / │
   │  • AIToolbarView       │   │    About screens            │
   │  • EmojiPickerView     │   │  • SettingsViewModel        │
   │  • ClipboardBottomSheet│   │      ↕ SettingsRepository   │
   │  • ClipboardManager-   │   │        (DataStore)          │
   │    Service (watches    │   └─────────────────────────────┘
   │    system clipboard)   │
   └────────────┬───────────┘
                │
     ┌──────────▼──────────────┐
     │ ClipboardRepository     │
     │   → ClipboardDao        │
     │   → ClipboardDatabase   │ ── encrypted at rest (SQLCipher)
     └─────────────────────────┘
```

**Key architectural notes:**
- **No network, no API, no auth, no telemetry.** Privacy is the headline architectural constraint. AI features, when built, must run on-device or against a user-supplied endpoint.
- The **keyboard surface is drawn with classic Views** inside `InputMethodService`; the **settings app is Compose**. Don't assume Compose in IME code.
- The **clipboard store is encrypted at rest** via SQLCipher; the passphrase is a 32-byte random value wrapped by an Android-Keystore AES-256-GCM key and stored in app-private files. The keystore key never leaves the secure hardware.
- Settings live in **DataStore Preferences**, not the DB.
- `allowBackup="false"` in the manifest — encrypted clipboard data must not be swept into cloud backups.

---

## 4. App / Module Structure

```
app/src/main/
├── AndroidManifest.xml          IME <service> + launcher <activity>
├── kotlin/io/superkeyboard/
│   ├── SuperKeyboardApp.kt       Application — owns lazy clipboardDatabase
│   ├── ime/                      The keyboard itself (classic Views)
│   │   ├── KeyboardService.kt    InputMethodService entry point
│   │   ├── KeyboardView.kt       Custom drawn keyboard view
│   │   ├── KeyboardState.kt      Shift/symbol/layer state
│   │   ├── KeyboardLayout.kt     Key rows / layout definitions
│   │   ├── Key.kt                Key model
│   │   ├── KeyboardTheme.kt      Colors/metrics for the drawn keyboard
│   │   ├── GestureHandler.kt     Swipe/gesture typing
│   │   ├── PopupKeyView.kt       Long-press popup keys
│   │   └── EmojiPickerView.kt    Emoji grid
│   ├── toolbar/
│   │   └── AIToolbarView.kt      AI action bar (translate/rewrite/… — scaffolded)
│   ├── clipboard/                Encrypted clipboard history
│   │   ├── ClipboardEntry.kt     Room @Entity
│   │   ├── ClipboardDao.kt       Room DAO
│   │   ├── ClipboardDatabase.kt  SQLCipher + Keystore passphrase
│   │   ├── ClipboardRepository.kt
│   │   ├── ClipboardManagerService.kt  Watches system clipboard
│   │   └── ClipboardBottomSheet.kt     RecyclerView UI surfaced from the IME
│   ├── settings/                 Settings app (Compose)
│   │   ├── SettingsActivity.kt
│   │   ├── SettingsRepository.kt DataStore-backed prefs
│   │   ├── SettingsViewModel.kt
│   │   └── screens/              MainSettings / Appearance / ClipboardSettings / About / NavHost
│   ├── util/
│   │   ├── ThemeManager.kt
│   │   └── HapticHelper.kt
│   └── res/                      themes, colors, strings, method.xml (IME subtypes), icons
```

**"Route protection":** N/A — there is no routing or auth. The only privileged surface is the IME service, gated by the OS-enforced `BIND_INPUT_METHOD` permission.

---

## 5. Key Architectural Patterns

### Pattern: Encrypted-at-rest local store
- **What:** Room database opened through SQLCipher's `SupportOpenHelperFactory` with a random passphrase wrapped by an Android-Keystore key.
- **Where used:** `clipboard/ClipboardDatabase.kt`.
- **Why:** Clipboard contents are sensitive (passwords, 2FA codes). On-disk encryption with a hardware-backed wrapping key keeps them private even if the device filesystem is accessed.

### Pattern: Repository over DAO
- **What:** `ClipboardRepository` / `SettingsRepository` wrap the raw DAO / DataStore so the IME and ViewModels never touch persistence primitives directly.
- **Where used:** `clipboard/`, `settings/`.
- **Why:** Single place to apply expiry/pinning rules and to keep the IME service thin.

### Pattern: Singleton DB via double-checked locking
- **What:** `ClipboardDatabase.create()` returns a `@Volatile` singleton.
- **Where used:** `ClipboardDatabase.kt`; exposed lazily off `SuperKeyboardApp`.
- **Why:** The DB is shared between the IME service and the clipboard watcher; one instance avoids multiple SQLCipher handles.

### Pattern: Two UI toolkits, one app
- **What:** IME keyboard surface uses classic Views; the settings app uses Compose.
- **Where used:** `ime/` (Views) vs `settings/` (Compose).
- **Why:** IMEs render most reliably with custom Views; Compose is ergonomic for the settings screens. Keep them separate.

---

## 6. Feature Status

| # | Feature | Status |
|---|---------|--------|
| 1 | Working IME keyboard (typing, layouts, gestures, emoji) | Shipped (Phase 1) |
| 2 | Encrypted clipboard history (SQLCipher + Keystore) | Shipped (Phase 1) |
| 3 | Settings app (Appearance / Clipboard / About) | Shipped (Phase 1) |
| 4 | AI toolbar actions (translate, rewrite, summarize, dictation, TTS, presets) | Scaffolded UI; engines not wired |
| 5 | Tests (unit + instrumented) | Not started |

---

## 7. Environment Variables

| Variable | Purpose |
|----------|---------|
| *(none)* | The app has no env vars. `local.properties` holds only the Android SDK path (`sdk.dir`) and is git-ignored — it is not application configuration. |

---

## 8. Development Commands

| Command | Purpose |
|---------|---------|
| `./gradlew :app:assembleDebug` | Build a debug APK |
| `./gradlew :app:installDebug` | Build + install on a connected device/emulator |
| `./gradlew :app:compileDebugKotlin` | Fast compile-only sanity check |
| `./gradlew :app:testDebugUnitTest --no-daemon` | Run JVM unit tests (single run — once a test source set exists) |
| `./gradlew connectedDebugAndroidTest` | Run instrumented tests (needs a device/emulator) |
| `./gradlew lint` | Android Lint |

**Testing note:** Gradle tests are always single-run (no watch mode). Use `--no-daemon` for clean one-shot runs in automation. There is **no unit-test source set yet** (`app/src/test/` is absent) — the first test-bearing change must create it.

---

## 9. Key Reference Documents

| Document | Purpose |
|----------|---------|
| `docs/context/database_reference_guide.md` | Room/SQLCipher schema, encryption, queries |
| `docs/context/API_REFERENCE.md` | Android service/component surface (no HTTP API) |
| `docs/context/Project_Authentication.md` | Permission & privacy model (no auth) |
| `docs/context/CHANGELOG.md` | Change history |
| `docs/mobile/Android_Build_and_Sideload.md` | APK build + sideload-to-device loop |
| `CLAUDE.md` | AI agent conventions and rules |
