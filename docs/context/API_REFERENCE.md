# API Reference — SuperKeyboard (Android Component & Service Surface)

> **Last Updated:** 2026-06-06
> **Note:** SuperKeyboard has **no HTTP/network API** — it is an offline, on-device app. This file is repurposed (per the kit's adaptation for this stack) to document the **Android component surface**: the IME service contract, exported components, intents, and the internal service/repository APIs that act as the app's "routes".

---

## 1. Exported / OS-facing components

### `KeyboardService` — the IME (`InputMethodService`)
- **Manifest:** `<service android:name=".ime.KeyboardService" android:exported="true" android:permission="android.permission.BIND_INPUT_METHOD">`
- **Intent filter:** `android.view.InputMethod`
- **Meta-data:** `android.view.im` → `@xml/method` (`res/xml/method.xml`)
- **Contract:** Implements `InputMethodService` + `KeyboardView.KeyboardActionListener`. Builds its input view (`onCreateInputView`) from classic Views: `KeyboardView`, `AIToolbarView`, `EmojiPickerView`, and the `ClipboardBottomSheet`. Owns a `ClipboardManagerService` lifecycle (start in `onCreate`, stop in `onDestroy`).
- **IME subtypes (`method.xml`):** one subtype — `en_US`, mode `keyboard`. `settingsActivity = io.superkeyboard.settings.SettingsActivity`.

### `SettingsActivity` — the configuration app
- **Manifest:** `<activity android:name=".settings.SettingsActivity" android:exported="true">` with `MAIN` / `LAUNCHER`.
- **Contract:** Compose host. Renders `SettingsNavHost` over `SettingsViewModel` (backed by `SettingsRepository`). Also the IME's declared settings activity.

### `SuperKeyboardApp` — `Application`
- Owns the lazily-created, SQLCipher-encrypted `clipboardDatabase` shared by the IME and the clipboard watcher.

**Permissions:** only `android.permission.VIBRATE` (haptics). No internet, storage, contacts, or accessibility permissions. `allowBackup="false"`.

---

## 2. Internal "APIs" (the app's real call surface)

### Clipboard
- `ClipboardRepository(dao)` — façade over `ClipboardDao`: list / search / insert / delete / pin / clear. The only clipboard entry point for IME + UI.
- `ClipboardManagerService(context, repository)` — `startListening()` / `stopListening()`. Subscribes to the system `ClipboardManager`, captures copies (gated by `clipboard_enabled`), applies expiry.
- `ClipboardBottomSheet` — RecyclerView UI surfaced from inside the IME; calls back into the repository for pin/delete/paste.

### Settings
- `SettingsRepository(context)` — `Flow`-based getters + suspend setters for each preference (see `database_reference_guide.md` §3).
- `SettingsViewModel` — exposes settings state to the Compose screens.

### Keyboard / IME internals
- `KeyboardView.KeyboardActionListener` — callback interface `KeyboardService` implements (key press, delete, shift, layer switch, gesture commit).
- `KeyboardState` — shift/symbol/layer state machine.
- `KeyboardLayout` / `Key` — layout + key models.
- `GestureHandler` — swipe-typing recognition.
- `KeyboardTheme` / `ThemeManager` / `HapticHelper` — theming + feedback helpers.

### AI toolbar (scaffolded)
- `AIToolbarView` — exposes action buttons (translate, rewrite, summarize, expand, dictation, read-aloud, presets, clipboard). **Engines are not yet wired.** When implemented, they MUST honor the privacy invariant: on-device, or the user's own configured endpoint — never a hard-coded third-party cloud.

---

## 3. There is intentionally no REST/GraphQL surface

Adding any outbound network call is an architectural decision that conflicts with the product's privacy promise and must be captured as an ADR (`docs/ard/`) before implementation.
