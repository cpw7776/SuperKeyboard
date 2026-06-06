# Project Authentication — SuperKeyboard

## Overview

> **There is no authentication in SuperKeyboard.** It is a single-user, on-device Android keyboard with no accounts, no login, no sessions, no backend, and no network calls. This file is repurposed (per the kit's adaptation for this stack) to document the **permission, trust, and data-privacy model** — the things that actually gate access to sensitive behavior on this platform.

---

## 1. Trust boundaries

| Boundary | Who/what is gated | Mechanism |
|----------|-------------------|-----------|
| IME binding | Only the Android system may bind the keyboard service | `android:permission="android.permission.BIND_INPUT_METHOD"` on `KeyboardService` |
| Becoming the active keyboard | The **user** must explicitly enable SuperKeyboard in Android Settings → System → Languages & input, then select it as the input method | OS-enforced, manual |
| Clipboard data at rest | Anyone with filesystem access still can't read clipboard history | SQLCipher encryption; passphrase wrapped by hardware-backed Android-Keystore key |
| Cloud backup | Encrypted data is excluded from device backups | `android:allowBackup="false"` |

There are no in-app roles, tiers, or privileged users.

---

## 2. Privacy model (the real "auth" of this app)

The product promise is *"the most helpful keyboard and completely private."* Concretely:

- **No telemetry, analytics, or crash reporting** that leaves the device.
- **No network permission** is requested. Keystrokes and clipboard contents never leave the device.
- **Clipboard history is encrypted at rest** (see `database_reference_guide.md`). The encryption passphrase is random per-install and wrapped by a non-exportable Keystore key.
- **AI toolbar features**, when implemented, must run **on-device** or against the **user's own configured endpoint** — never a hard-coded third-party service. The `about_privacy`/`about_description` strings make this commitment to users; code must honor it.

### Hard rules for agents working in this repo
1. Do not add the `INTERNET` permission or any outbound network call without an ADR that explicitly reconciles it with the privacy promise.
2. Never log decrypted clipboard text, the DB passphrase, or Keystore material.
3. Do not enable `allowBackup` or add cloud sync for clipboard data.
4. Do not rotate or rename the Keystore alias `superkeyboard_clipboard_key` casually — it orphans the existing encrypted DB.

---

## 3. Key/secret handling

- Encryption keys live in the Android Keystore (`AndroidKeyStore` provider), generated on first run, never exported.
- The wrapped DB passphrase is stored in the app-private files dir (`clipboard_passphrase`), unreadable by other apps under Android's per-app sandbox.
- There are no API keys, tokens, OAuth secrets, or service credentials in this project. `local.properties` holds only the SDK path and is git-ignored.
