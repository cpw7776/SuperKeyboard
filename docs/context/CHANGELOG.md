# Changelog

All notable changes to SuperKeyboard will be documented in this file.

> **Format:** Each entry should include: what changed, why, files affected, and any DB/component-surface/privacy changes.
> **Rule:** Every feature and every fix gets a changelog entry. No exceptions.
> **Note:** SuperKeyboard has no HTTP API and no auth — the "API changes" line tracks the Android component/service surface; the "Auth changes" line tracks the permission/privacy model.

## [Unreleased]

> Add entries here as features and fixes are completed. Move to a versioned section on release.

### Feature: Test Suite Foundation (`test-foundation`) — 2026-06-09

Stood up the **first test source sets** in the repo, closing P0 #5 (`PRODUCTION_READY.md`) and activating the kit's 5th post-feature gate (Test-Suite Summary) for all future epics. Two suites, both green, **zero production-code change** (all new deps are test-scope only — the shipped APK is byte-for-byte unchanged):
- **JVM unit suite** (`app/src/test/`, `:app:testDebugUnitTest`) — `KeyboardStateTest` (15 tests: shift cycle, auto-shift, symbols pages, emoji) + `ClipboardRepositoryTest` (9 tests: dedup, expiry math, pin, clear/cleanup) over a hand-written in-memory `FakeClipboardDao`. 24 tests, 0 failures/skips.
- **Instrumented suite** (`app/src/androidTest/`, `connectedDebugAndroidTest` on AVD `Medium_Phone_API_36.1`) — `ClipboardDatabaseEncryptionTest` (3 tests): **encryption-at-rest** (sentinel absent from raw `clipboard.db*` bytes — the privacy-promise regression guard), encrypted round-trip, and wrong-key rejection. 3 tests, 0 failures/skips.

Test stack: JUnit4 · `kotlinx-coroutines-test` 1.7.3 (pinned to room-ktx's coroutines-core) · Truth · `androidx.test`. Turbine deliberately deferred (repo Flow methods are pass-throughs). Decisions recorded in `docs/ard/ADR_Test_Foundation.md` (D1–D7). Discipline baselines (`docs/known-test-failures.md`, `docs/known-test-skips.md`) flipped from "no suite" to a real green baseline (0 skips).

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts` (+`testInstrumentationRunner`, test-scope deps); new test files under `app/src/test/` + `app/src/androidTest/`; docs (PRD/ADR/architecture/plan/test-plan, Context Index, KIT_DEVIATIONS gate-count note).
**DB changes:** None (tests read the existing schema; no migration). **Component-surface changes:** None. **Privacy changes:** None — the instrumented suite *verifies* encryption-at-rest and does not weaken it; no logging of decrypted text / passphrase / Keystore material; no network; `allowBackup` untouched.

### Fix: taller keys + clear the gesture/nav bar — 2026-06-09

On-device follow-up after v0.1.2: the keyboard rendered and typed correctly, but the bottom row sat against the gesture-navigation bar and the keys were a touch short. Increased proportional key height (`width/10 * 1.1 → * 1.34`) and added bottom padding to the IME root layout sized to the live navigation-bar + system-gesture insets (with a 16dp floor for the common IME zero-inset case), painting the padded area the keyboard background colour. Version `0.1.2→0.1.3` (`versionCode 3→4`).

**Files:** `ime/KeyboardView.kt`, `ime/KeyboardService.kt` (+`androidx.core.view` insets), `app/build.gradle.kts`.
**DB changes:** None. **Component-surface changes:** None. **Privacy changes:** None.

### Fix: blank keyboard on reopen + toolbar/layout polish — 2026-06-07

First on-device sideload (GrapheneOS) surfaced a P0: the keyboard rendered once, then came back **blank with dead keys** on reopen. Root cause — `KeyboardView` assigned each key's draw/hit rectangle (`Key.bounds`) only in `onSizeChanged()`, but `KeyboardLayout.getLayout()` returns fresh `Key` objects (empty bounds) on every state rebuild; when the IME input view is reused at the same size, `onSizeChanged()` doesn't fire, so the new keys never got bounds → `onDraw`/`onTouchEvent` skipped them all. Fix: `rebuildLayout()` now recomputes bounds immediately when the view is already sized (also fixes latent blanking on symbols/emoji layout switches). Same session shipped two layout-polish fixes: removed the redundant internal toolbar strip in `KeyboardView` (the keys now sit flush — no dead space), and changed `AIToolbarView` from a left-clustering `HorizontalScrollView` to an equal-weight horizontal `LinearLayout` so toolbar icons fill the width and shrink as more are added. Version `0.1.1→0.1.2` (`versionCode 2→3`).

**Files:** `ime/KeyboardView.kt`, `toolbar/AIToolbarView.kt`, `app/build.gradle.kts`. Bug report: `docs/bugs/2026-06-07-blank-keyboard-on-reopen.md`. Lesson: `docs/context/Implementation_Patterns.md` (Gap C).
**DB changes:** None. **Component-surface changes:** None (IME render/layout only). **Privacy changes:** None.

### Persistent debug-keystore sideload pipeline — 2026-06-07 (commit `933ac67`)

Set up reliable APK sideload-to-phone for the IME. Previously `assembleDebug` was signed with Gradle's auto-generated `~/.android/debug.keystore` (machine-local `CN=Android Debug` key), so rebuilds risked a signature mismatch and the phone rejected updates with "App not installed". Wired the canonical Type-A pipeline from `docs/mobile/Android_Build_and_Sideload.md` §2.4: generated and committed `keystore/superkeyboard-debug.keystore`, pointed `signingConfigs.debug` at it in `app/build.gradle.kts`, added a `.gitignore` exception so the shared keystore is tracked, bumped `versionCode 1→2` / `versionName 0.1.0→0.1.1`, and recorded the canonical signing SHA-256 (`53c0f253…`) in the new `apk-releases/BUILD.md`. First install over the old auto-keyed build needs a one-time uninstall; every build after updates in place.

**New files:** `keystore/superkeyboard-debug.keystore`, `apk-releases/BUILD.md`.
**DB changes:** None. **Component-surface changes:** None. **Privacy changes:** None — debug-only sideload signing; the checked-in key must never sign a release build, and this adds no network/backup behavior.

### AI Dev Workflow Kit adopted (v5.14) — 2026-06-06

Adopted the AI Dev Workflow Kit (fresh adoption). Copied `.claude/` (4 sub-agents + `/reconcile` command) and `docs/` (prompts, context, mobile, upgrading) into the project, adapted the sub-agent `[CUSTOMIZE]` slots to this native-Android/Kotlin/Gradle stack (Gradle test/build commands, no browser/dev-server, no auth, in-app docs surfaces), and built out the seven context files from the existing Phase 1 codebase.

**New files:** `.claude/agents/*.md`, `.claude/commands/reconcile.md`, `docs/**`, `docs/KIT_VERSION` (=5.14), `docs/KIT_DEVIATIONS.md`, `CLAUDE.md`.
**DB changes:** None. **Component-surface changes:** None. **Privacy changes:** None (documented existing model).

### Phase 1 foundation — (pre-kit, git commit `1c30398` + fixes through `feba9d3`)

Working IME keyboard with encrypted clipboard: custom drawn `KeyboardView`, gesture typing, emoji picker, AI toolbar scaffold, Compose settings app (Appearance/Clipboard/About), and a SQLCipher-encrypted Room clipboard store keyed by an Android-Keystore-wrapped passphrase. Follow-up fixes corrected the SQLCipher artifact/version, the `SupportOpenHelperFactory` import, RecyclerView adapter position handling, and native-library load order.

**DB changes:** Added `clipboard_entries` table (Room, SQLCipher-encrypted). **Component-surface changes:** Added `KeyboardService` (IME) + `SettingsActivity` (launcher). **Privacy changes:** Established encrypted-at-rest clipboard + no-network model.
