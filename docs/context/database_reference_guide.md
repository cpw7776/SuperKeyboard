# Database Reference Guide — SuperKeyboard

**Purpose:** Reference for all on-device persistence — the encrypted clipboard store (Room + SQLCipher) and the settings store (DataStore Preferences).

**Last Updated:** 2026-06-06

> **Privacy invariant:** Clipboard contents are sensitive. Never log decrypted clipboard text, the passphrase, or Keystore key material. Never add cloud sync or backup of this data (`allowBackup="false"` is set deliberately).

---

## 1. Storage overview

| Store | Tech | Location | Encrypted? |
|-------|------|----------|-----------|
| Clipboard history | Room + SQLCipher | `clipboard.db` (app-private files dir) | **Yes** — SQLCipher, AES |
| App settings | DataStore Preferences | `settings` preferences file | No (non-sensitive prefs) |

---

## 2. Clipboard database (Room + SQLCipher)

- **Class:** `io.superkeyboard.clipboard.ClipboardDatabase`
- **DB file:** `clipboard.db`
- **Schema version:** 1 (`exportSchema = false`)
- **Migration policy:** `fallbackToDestructiveMigration()` — schema changes currently drop & recreate. **Before shipping to real users, replace this with real migrations** or clipboard history is lost on every schema bump.
- **Singleton:** `ClipboardDatabase.create(context)` (double-checked locking). Exposed lazily from `SuperKeyboardApp.clipboardDatabase`.

### Encryption / key management
- Opened via SQLCipher `SupportOpenHelperFactory(passphrase)`.
- **Passphrase:** 32 random bytes from `SecureRandom`, generated once.
- The passphrase is **wrapped** by an Android-Keystore AES-256-GCM key (alias `superkeyboard_clipboard_key`, `AndroidKeyStore`) and stored encrypted in the app-private file `clipboard_passphrase` (format: `[ivLen byte][iv][ciphertext]`).
- The wrapping key is hardware-backed where available and never leaves the keystore.
- **SQLCipher native lib must be loaded before DB init** (see commit `feba9d3`) — a regression class to watch.

### Table: `clipboard_entries`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `Long` (PK, autoGenerate) | |
| `text` | `String` | The clipped text (sensitive) |
| `timestamp` | `Long` | Capture time, default `now()` |
| `isPinned` | `Boolean` | Pinned entries survive expiry & "clear unpinned" |
| `expiresAt` | `Long` | Default `now + 24h` (`DEFAULT_EXPIRY_MS`) |

Entity: `ClipboardEntry` (`@Entity(tableName = "clipboard_entries")`).

### DAO: `ClipboardDao`

| Method | Query / effect |
|--------|----------------|
| `getAllEntries(): Flow<List<…>>` | Latest 20, pinned first then newest |
| `searchEntries(query): Flow<List<…>>` | LIKE `%query%`, pinned first |
| `insert(entry)` | REPLACE on conflict |
| `delete(entry)` / `deleteById(id)` | Remove one |
| `deleteExpired(currentTime)` | Drop expired **unpinned** rows |
| `deleteAllUnpinned()` | "Clear history" but keep pins |
| `deleteAll()` | Full wipe |
| `setPinned(id, pinned)` | Toggle pin |
| `getCount()` / `findByText(text)` | Count / dedupe lookup |

**Behavioral rules to preserve:** pinned entries are exempt from both expiry and "clear unpinned"; the visible list is capped at 20 and ordered pinned-first, newest-first; dedupe uses `findByText`.

### Access layers
- `ClipboardRepository` wraps the DAO (the only thing the IME/UI should call).
- `ClipboardManagerService` watches the system clipboard and inserts new entries (respecting the `clipboard_enabled` setting and the configured expiry).
- `ClipboardBottomSheet` renders entries via RecyclerView, surfaced from the IME.

---

## 3. Settings store (DataStore Preferences)

- **Class:** `io.superkeyboard.settings.SettingsRepository`
- **File name:** `settings`

| Key | Type | Default | Values |
|-----|------|---------|--------|
| `theme_mode` | String | `"system"` | `system` / `light` / `dark` |
| `keyboard_height_factor` | Float | `1.0` | ~0.8–1.4 |
| `haptic_enabled` | Boolean | `true` | |
| `sound_enabled` | Boolean | `false` | |
| `clipboard_enabled` | Boolean | `true` | gates the clipboard watcher |
| `clipboard_expiry_hours` | Int | `24` | 1, 6, 12, 24, 48, 0=never |

All reads are `Flow`s; writes go through `dataStore.edit { }`.

---

## 4. Change-impact checklist (when touching persistence)

- Bumping the Room schema → add a real `Migration` (don't ship `fallbackToDestructiveMigration` to users).
- Changing clipboard expiry/pin semantics → update `ClipboardDao` queries **and** `ClipboardManagerService` insert logic together.
- Anything touching the passphrase/keystore flow → verify native-lib load order and that an existing `clipboard_passphrase` still decrypts (don't rotate the Keystore alias casually — it orphans the DB).
- Adding a new setting → add key + default + flow + setter in `SettingsRepository`, and a screen control under `settings/screens/`.
