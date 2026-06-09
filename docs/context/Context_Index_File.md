# Context Files Index

## Quick Reference to Key Context Documents

This index provides a quick reference to the key context documents in the repository root. These documents are the single source of truth for project architecture, development workflow, security, PRDs/ADRs, and roadmap.

---

## Sources of Truth

### Project-Level Source of Truth — `docs/context/` (7 files, always kept current)

These are **authoritative**. When in doubt, trust these over code comments, memory, or assumptions. Every feature implementation must update every file that was affected — skipping even one is a common failure mode.

| File | Covers | Update when... |
|------|--------|---------------|
| `Context_Index_File.md` | All files in the project | Any new or removed file anywhere |
| `database_reference_guide.md` | Schema, tables, columns, RLS, migrations | Any DB change |
| `API_REFERENCE.md` | All API routes, methods, auth requirements | Any new or modified route |
| `CHANGELOG.md` | Feature history | Always — every feature and fix |
| `PRODUCTION_READY.md` | Epic/milestone status, known bugs, launch checklist | Status changes, bugs added/resolved |
| `Project_Authentication.md` | Auth flow, session handling, RLS changes | Any auth or RLS change |
| `Project_PDR.md` | Product decisions and constraints | Architectural or product decisions |

### Feature-Level Source of Truth — PRD & ADR (one per epic/feature)

| Document | Covers | Update when... |
|----------|--------|---------------|
| `docs/prd/[feature].md` | What to build, task checklist, acceptance criteria | Plan changes, tasks ticked off |
| `docs/ard/[feature].md` | How to build it, decisions, data flow, security | Implementation deviates from plan |

**Rule:** Check for existing PRD/ARD before creating new ones — update, don't duplicate.

---

## When to Consult Each Document

### Before Starting Work
| Document | When to Consult |
|----------|-----------------|
| `PRODUCTION_READY.md` | Check current milestone status and known bugs |
| `Project_PDR.md` | System-level changes, architecture, platform overview |
| `database_reference_guide.md` | Before ANY database schema changes |
| `docs/prd/[feature].md` | Existing feature requirements if PRD exists |

### During Development
| Document | When to Consult |
|----------|-----------------|
| `docs/prd/[feature].md` | Implementation details, acceptance criteria, task breakdown |
| `docs/ard/[feature].md` | Architectural decisions, trade-offs, rationale |
| `docs/bugs/*.md` | When encountering similar issues — check if already documented |

### After Completing Work — MANDATORY AUDIT (do not skip)

Run through **every file** in `docs/context/` and update if the feature touched it:

| File | Update if... |
|------|-------------|
| `Context_Index_File.md` | Any new or removed files anywhere in the project |
| `database_reference_guide.md` | Any schema changes, new tables, new columns, migrations |
| `API_REFERENCE.md` | Any new or modified API routes |
| `CHANGELOG.md` | **Always** — add a summary entry for this feature |
| `PRODUCTION_READY.md` | Milestone status changed, bugs added/resolved |
| `Project_Authentication.md` | Auth flow, session handling, or RLS changes |
| `Project_PDR.md` | Product decisions or constraints changed |

Also update:
- `docs/prd/[feature].md` — tick off completed tasks, note any deviations

### Bug Documentation
| Situation | Action |
|-----------|--------|
| Bug found | Create `docs/bugs/BUG-NNN-description.md` |
| Bug fixed | Update bug report + PRODUCTION_READY.md Known Bugs table |
| Complex debug | Document attempted fixes and root cause in bug report |

---

## Project File Catalog

> SuperKeyboard is a native Android IME. There is no `src/` or `package.json` — Kotlin sources live under `app/src/main/kotlin/io/superkeyboard/`. Note `API_REFERENCE.md` documents the Android component surface (no HTTP API) and `Project_Authentication.md` documents the permission/privacy model (no auth).

### Root
| File | Purpose |
|------|---------|
| `CLAUDE.md` | AI agent instructions — conventions, commands, workflow rules |
| `README.md` | One-line product description (the only prose user-facing doc) |
| `settings.gradle.kts` | Gradle settings — includes `:app`, repo config |
| `build.gradle.kts` | Root build script (plugin aliases) |
| `app/build.gradle.kts` | App module build: Android config, deps |
| `gradle/libs.versions.toml` | Version catalog (all dependency versions) |
| `gradle.properties` / `local.properties` | Gradle flags / SDK path (git-ignored) |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Gradle wrapper (pins Gradle 8.11.1) |
| `keystore/superkeyboard-debug.keystore` | Committed persistent **debug** keystore — signs `assembleDebug` for sideload. Not for release. |
| `apk-releases/BUILD.md` | Per-build instructions + canonical debug signature SHA-256; built APKs land here (git-ignored) |

### App — `app/src/main/kotlin/io/superkeyboard/`
| File | Purpose |
|------|---------|
| `SuperKeyboardApp.kt` | `Application` — owns the lazy encrypted clipboard DB |

#### `ime/` — the keyboard (classic Views)
| File | Purpose |
|------|---------|
| `KeyboardService.kt` | `InputMethodService` entry point; builds the input view |
| `KeyboardView.kt` | Custom drawn keyboard view + `KeyboardActionListener` |
| `KeyboardState.kt` | Shift/symbol/layer state machine |
| `KeyboardLayout.kt` / `Key.kt` | Layout definitions + key model |
| `KeyboardTheme.kt` | Colors/metrics for the drawn keyboard |
| `GestureHandler.kt` | Swipe/gesture typing |
| `PopupKeyView.kt` | Long-press popup keys |
| `EmojiPickerView.kt` | Emoji grid |

#### `toolbar/`
| File | Purpose |
|------|---------|
| `AIToolbarView.kt` | AI action bar (translate/rewrite/… — UI scaffolded, engines TBD) |

#### `clipboard/` — encrypted clipboard history
| File | Purpose |
|------|---------|
| `ClipboardEntry.kt` | Room `@Entity` (`clipboard_entries`) |
| `ClipboardDao.kt` | Room DAO (list/search/insert/pin/expire/clear) |
| `ClipboardDatabase.kt` | SQLCipher DB + Keystore-wrapped passphrase |
| `ClipboardRepository.kt` | Façade over the DAO |
| `ClipboardManagerService.kt` | Watches the system clipboard, inserts entries |
| `ClipboardBottomSheet.kt` | RecyclerView UI surfaced from the IME |

#### `settings/` — configuration app (Compose)
| File | Purpose |
|------|---------|
| `SettingsActivity.kt` | Launcher activity; Compose host |
| `SettingsRepository.kt` | DataStore-backed preferences |
| `SettingsViewModel.kt` | Settings state for Compose |
| `screens/SettingsNavHost.kt` | Navigation graph |
| `screens/MainSettingsScreen.kt` | Top-level settings list |
| `screens/AppearanceScreen.kt` | Theme + keyboard height + feedback |
| `screens/ClipboardSettingsScreen.kt` | Clipboard enable/expiry/clear |
| `screens/AboutScreen.kt` | Version + privacy copy |

#### `util/`
| File | Purpose |
|------|---------|
| `ThemeManager.kt` | Day/night theme resolution |
| `HapticHelper.kt` | Vibration feedback |

### Resources — `app/src/main/res/`
| File | Purpose |
|------|---------|
| `xml/method.xml` | IME subtype declaration + settings activity |
| `values/strings.xml` | All user-visible strings |
| `values/themes.xml`, `values-night/themes.xml`, `values/colors.xml` | Theming |
| `drawable/ic_keyboard.xml`, `mipmap-anydpi-v26/ic_launcher.xml` | Icons |
| `AndroidManifest.xml` | IME `<service>` + launcher `<activity>`, permissions |

### Documentation — `docs/`
| File | Purpose |
|------|---------|
| `docs/context/Context_Index_File.md` | This file — master index of all project files |
| `docs/context/Project_PDR.md` | Project Design Reference — architecture and decisions |
| `docs/context/API_REFERENCE.md` | Android component/service surface (no HTTP API) |
| `docs/context/database_reference_guide.md` | Room/SQLCipher schema + DataStore prefs |
| `docs/context/CHANGELOG.md` | Chronological change history |
| `docs/context/PRODUCTION_READY.md` | Release criteria and milestone tracking |
| `docs/context/Project_Authentication.md` | Permission & privacy model (no auth) |
| `docs/mobile/Android_Build_and_Sideload.md` | APK build + sideload-to-device loop |
| `docs/bugs/2026-06-07-blank-keyboard-on-reopen.md` | P0 bug report: keyboard blank on reopen (fixed v0.1.2) |

### Tests
> First suites stood up by the `test-foundation` epic (2026-06-09). See `docs/prd/PRD_Test_Foundation.md` and `docs/ard/ADR_Test_Foundation.md`.

| File | Purpose |
|------|---------|
| `app/src/test/kotlin/io/superkeyboard/ime/KeyboardStateTest.kt` | JVM unit tests for the `KeyboardState` state machine (15 tests) |
| `app/src/test/kotlin/io/superkeyboard/clipboard/FakeClipboardDao.kt` | Hand-written in-memory fake of `ClipboardDao` for repo tests |
| `app/src/test/kotlin/io/superkeyboard/clipboard/ClipboardRepositoryTest.kt` | JVM unit tests for `ClipboardRepository` over the fake DAO (9 tests) |
| `app/src/androidTest/kotlin/io/superkeyboard/clipboard/ClipboardDatabaseEncryptionTest.kt` | Instrumented SQLCipher tests: encryption-at-rest, round-trip, wrong-key (3 tests) |

### Feature docs — `test-foundation`
| File | Purpose |
|------|---------|
| `docs/prd/PRD_Test_Foundation.md` | PRD — test-suite foundation task checklist |
| `docs/ard/ADR_Test_Foundation.md` | ADR — test-stack decisions D1–D7 |
| `docs/architecture/Feature_Architecture_Test_Foundation.md` | Invocation→assertion flow |
| `docs/plans/2026-06-09-test-foundation.md` | Plan snapshot + Phase 5 learning log |
| `docs/testing-agents/test-foundation-tests.md` | Native test plan (Gradle suites + on-device sanity) |
