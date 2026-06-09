# Epic: Test Suite Foundation (`test-foundation`) — Handoff / Kickoff

> **Status:** Phase 1 (scope) APPROVED 2026-06-09. Next chat resumes at **Phase 2 (Plan Mode)** of `docs/prompts/feature-lifecycle.md`. Single-chat mode, on branch `claude/ai-keyboard-app-dPrQ4`.

This epic closes **P0 #5** in `PRODUCTION_READY.md` (no test source set exists). It flips the project
from "suite-less" to "suite-ful," permanently activating the 5th gate (Test-Suite Summary) for all
future epics — consistent with `KIT_DEVIATIONS.md`'s five-gate decision.

## Locked decisions (Phase 0/1)

- **Execution mode:** single-chat (all phases in one chat).
- **Branch:** continue on `claude/ai-keyboard-app-dPrQ4` (no new branch; whole project lives here, `main` is far behind).
- **Scope:** full — JVM unit tests **+** instrumented SQLCipher encryption test.
- **Feature ID / commit prefix:** `test-foundation` (e.g. `feat(test-foundation): ...`).
- **Test stack (finalize tradeoffs in Plan Mode):** JUnit4 · `kotlinx-coroutines-test` · Turbine (Flow) · Truth (assertions) · `androidx.test` core/ext-junit/runner for instrumented · **hand-written fake DAO, no MockK** (kit anti-over-mocking).

## Approved scope

### In scope (v1)
1. **Infra:** add `app/src/test/` (JVM) + `app/src/androidTest/` (instrumented); add test deps to `gradle/libs.versions.toml` and wire into `app/build.gradle.kts`; set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.
2. **JVM `KeyboardStateTest`** (`ime/KeyboardState.kt` — pure Kotlin): shift cycle OFF→ON→LOCKED→OFF; `onCharacterTyped` reverts ON→OFF but NOT LOCKED; `setAutoShift` ignored when LOCKED; `toggleSymbols`/`toggleSymbolsPage2`; `switchToAlpha`; emoji mode toggles.
3. **JVM `ClipboardRepositoryTest`** (`clipboard/ClipboardRepository.kt` via a hand-written fake `ClipboardDao`): `addEntry` dedup (deletes existing same-text then inserts); expiry math (`expiryMs>0`→`now+expiryMs`; `≤0`→`Long.MAX_VALUE`); `togglePin` flips `isPinned`; `clearAll`/`clearUnpinned`/`cleanupExpired` delegate to the right DAO calls.
4. **Instrumented encryption-at-rest** (the privacy promise): insert a sentinel string via the real SQLCipher-backed `ClipboardDatabase`, then scan the raw `.db` file bytes on disk and assert the plaintext sentinel is **absent**.
5. **Instrumented encrypted round-trip:** insert/query/delete through the real encrypted Room DB; Keystore-wrapped passphrase opens it (and a wrong key fails, if cleanly testable).
6. **Discipline files:** create `docs/known-test-failures.md` + `docs/known-test-skips.md` baselines.
7. **Green baseline:** both suites pass. Instrumented runs on AVD `Medium_Phone_API_36.1` (also available: `Pixel_9_Pro_Fold`). No physical device attached; `~/Library/Android/sdk/emulator/emulator` is installed.

### Out of scope (deferred)
- Android-`Context`-coupled classes (`ClipboardManagerService`, `KeyboardService` auto-capitalize, custom-`View` rendering) → Robolectric/instrumented follow-up.
- CI wiring (GitHub Actions).
- Real Room migrations (separate P0 epic).
- No production-code change expected; if a backfilled test goes red on a real defect, fix the code per TDD and flag it.

## Test-target reference (already read — file:line)
- `ime/KeyboardState.kt` — pure Kotlin state machine (no Android deps).
- `clipboard/ClipboardRepository.kt` — depends only on `ClipboardDao` (interface) → fake DAO works for JVM.
- `clipboard/ClipboardDao.kt` — Room `@Dao` interface; `findByText`, `setPinned`, `deleteExpired(currentTime)`, `deleteAllUnpinned`, etc.
- `clipboard/ClipboardEntry.kt` — `DEFAULT_EXPIRY_MS = 24h`; fields `id/text/timestamp/isPinned/expiresAt`.
- `clipboard/ClipboardDatabase.kt` — SQLCipher + Keystore-wrapped passphrase (instrumented target). NOTE: still uses `fallbackToDestructiveMigration()` (separate P0).

## Build/host facts
- Java 17 (`/opt/homebrew/opt/openjdk@17`), `ANDROID_HOME=/Users/michaelperry/Library/Android/sdk`, build-tools 36.1.0.
- Commands: `./gradlew :app:testDebugUnitTest --no-daemon` (JVM), `./gradlew connectedDebugAndroidTest` (instrumented, needs booted AVD), `./gradlew :app:assembleDebug` (build). Sweep workers: `pkill -f "GradleWorkerMain|KotlinCompileDaemon"`.
- No test deps currently in the catalog or `build.gradle.kts` — starting from zero.

## Project state at handoff
- Branch `claude/ai-keyboard-app-dPrQ4`, working tree clean. Latest app version `0.1.3` (versionCode 4).
- **4 commits unpushed** (`933ac67`, `4cc735c`, `5affa52`, `acceb30`) — push needs the user's GitHub creds (`! git push -u origin claude/ai-keyboard-app-dPrQ4`); `gh` not installed, HTTPS remote.
- Phase 1 foundation validated on-device (renders, types, clipboard works). AI toolbar icons are placeholders (epic #2 — see memory `ai-action-engine-direction`).

## Next chat: start here
Switch to `opus`, then: "Continue the `test-foundation` epic at Phase 2 (Plan Mode) of `docs/prompts/feature-lifecycle.md`. Read this handoff: `docs/plans/test-foundation-epic.md`. Scope is approved; proceed to Plan Mode."
