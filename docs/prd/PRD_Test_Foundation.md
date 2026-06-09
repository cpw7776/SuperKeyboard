# PRD — Test Suite Foundation (`test-foundation`)

> **Status:** Phase 2 (planning). Single-chat mode, branch `claude/ai-keyboard-app-dPrQ4`.
> **Supersedes:** nothing (first PRD in this repo).
> **Companions:** plan snapshot `docs/plans/2026-06-09-test-foundation.md` · ADR `docs/ard/ADR_Test_Foundation.md` · architecture `docs/architecture/Feature_Architecture_Test_Foundation.md`.
> **This PRD is the implementation checklist.** Every Phase 3 task lives here as a checkable box. Rewritten in Phase 5 to match what was actually built.

## Problem & goal

No test source set exists (`app/src/test/`, `app/src/androidTest/` absent; zero test deps). This is **P0 #5** in `PRODUCTION_READY.md`, and it keeps the kit's 5th post-feature gate inert. Goal: stand up a green JVM unit suite **and** a green instrumented suite over three high-value targets, then update the discipline baselines so the 5th gate is live for all future epics.

## Success criteria

- `./gradlew :app:testDebugUnitTest --no-daemon` runs and is **green** (all pass, 0 unexpected skips).
- `./gradlew connectedDebugAndroidTest` runs **green** on AVD `Medium_Phone_API_36.1`, proving clipboard data is encrypted at rest.
- `./gradlew :app:assembleDebug` still builds clean (test wiring didn't break the app build).
- `docs/known-test-failures.md` / `docs/known-test-skips.md` reflect the real baseline.
- **0 production-code changes** (per locked scope; if a test exposes a real defect, fix the code per TDD and flag it — `DEVIATION:`).

## Anti-pattern guard (read before any failing test)

Read `docs/context/Unit_Test_Writing_Guide.md` and `docs/context/Implementation_Patterns.md` once before Task 2. Relevant IDs cited per task below: **A2** (mock at boundary, not inside own code), **A3** (error/empty/boundary cases), **A4** (assert behaviour the mock couldn't fake alone), **A7** (async/timing actually observed), **A8** (skips need a dated reason).

---

## Tasks

### T1 — Test infra & build wiring
- [ ] Add to `gradle/libs.versions.toml` `[versions]`: `junit = "4.13.2"`, `coroutinesTest` (match the coroutines version Room/ktx pulls — pin explicitly), `truth = "1.4.4"`, `androidxTestCore`/`androidxTestExtJunit`/`androidxTestRunner` (`androidx.test` 1.6.x / ext-junit 1.2.x / runner 1.6.x).
- [ ] Add `[libraries]` entries: `junit`, `kotlinx-coroutines-test`, `truth`, `androidx-test-core`, `androidx-test-ext-junit`, `androidx-test-runner`.
- [ ] In `app/build.gradle.kts`: `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`, `testImplementation(libs.truth)`; `androidTestImplementation(libs.androidx.test.ext.junit)`, `androidTestImplementation(libs.androidx.test.runner)`, `androidTestImplementation(libs.androidx.test.core)`, `androidTestImplementation(libs.truth)`.
- [ ] Add `defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`.
- [ ] Create source-set dirs `app/src/test/kotlin/io/superkeyboard/` and `app/src/androidTest/kotlin/io/superkeyboard/` (confirm Kotlin source roots resolve; add `sourceSets` overrides only if Gradle doesn't pick up `src/test/kotlin` automatically).
- [ ] Verify the empty suite runs green: `./gradlew :app:testDebugUnitTest --no-daemon` (no tests = success). **Clean baseline before writing any test.**

### T2 — `KeyboardStateTest` (JVM)
> `STOP: AI Test` — pure-Kotlin state machine. Anti-patterns in play: **A3** (cover the LOCKED branch and no-op edges, not just the happy toggle). No mocking at all (it's pure logic). Assert observable state (`shiftState`, `layoutPage`, `isEmojiMode`, `isShifted`), not internals (**A4**).
- [ ] `toggleShift` cycles OFF→ON→LOCKED→OFF.
- [ ] `onCharacterTyped` reverts ON→OFF but leaves LOCKED untouched (and is a no-op from OFF).
- [ ] `setAutoShift(true)`→ON and `setAutoShift(false)`→OFF when not LOCKED; **ignored while LOCKED** (both true and false).
- [ ] `isShifted` true for ON and LOCKED, false for OFF.
- [ ] `toggleSymbols` QWERTY↔SYMBOLS_1, and SYMBOLS_2→QWERTY.
- [ ] `toggleSymbolsPage2` SYMBOLS_1↔SYMBOLS_2, and **no-op from QWERTY** (boundary, A3).
- [ ] `switchToAlpha` forces QWERTY from any page.
- [ ] `toggleEmojiMode` flips; `setEmojiMode(true/false)` sets explicitly.
- [ ] Run RED→GREEN per case; `/code-review`; tick boxes.

### T3 — `FakeClipboardDao` + `ClipboardRepositoryTest` (JVM)
> `STOP: AI Test` — repository logic over a **hand-written in-memory fake** of the `ClipboardDao` interface. Anti-patterns: **A2** (fake at the DAO boundary, the seam the repo actually talks to — do NOT mock the repo's own methods), **A4** (the fake must really store rows so dedup is observable, not a stub that returns a canned value), **A3** (empty store, `expiryMs<=0` boundary), **A7** (suspend funcs run under `runTest`, assertions actually awaited).
- [ ] `FakeClipboardDao`: `MutableList<ClipboardEntry>` backing store with autoincrement id on `insert`; `MutableStateFlow` for `getAllEntries`/`searchEntries`; faithful `findByText`/`delete`/`deleteById`/`deleteExpired`/`deleteAllUnpinned`/`deleteAll`/`setPinned`/`getCount`. Optional call-count counters for delegation assertions where state isn't the cleanest signal.
- [ ] `addEntry` inserts a new row when text is novel; `expiresAt` is within `[before+expiryMs, after+expiryMs]` (range assertion, D3/A7).
- [ ] `addEntry` with `expiryMs <= 0` sets `expiresAt == Long.MAX_VALUE` (boundary, A3).
- [ ] `addEntry` dedup: adding the same text twice leaves exactly **one** row with that text (behavioural, A4) — old one deleted, new inserted.
- [ ] `togglePin` flips `isPinned` for the entry's id (verify via `setPinned` effect on stored row).
- [ ] `clearAll` empties the store; `clearUnpinned` removes only unpinned; `cleanupExpired` delegates to `deleteExpired`.
- [ ] Run RED→GREEN per case; `/code-review high` (new logic); tick boxes.

### T4 — `ClipboardDatabaseEncryptionTest` (instrumented)
> `STOP: AI Test` — real SQLCipher + Keystore on a booted emulator. This is the **privacy-promise** test. Defensive `System.loadLibrary("sqlcipher")` + clean-slate reset (D5) in setup. Anti-patterns: **A3** (also assert the negative — wrong key fails), **A8** (if wrong-key isn't cleanly catchable, `@Ignore` + dated skip in `docs/known-test-skips.md`).
- [ ] `@Before`: reflection-reset the `ClipboardDatabase.instance` singleton (close if open, null the static field), delete `clipboard.db*` + `clipboard_passphrase`. `@BeforeClass`: `System.loadLibrary("sqlcipher")`.
- [ ] **Encryption-at-rest:** insert a distinctive sentinel via `ClipboardDatabase.create(context).clipboardDao().insert(...)`; read raw bytes of every `clipboard.db*` sidecar from `context.getDatabasePath("clipboard.db")`; assert the sentinel (UTF-8 **and** UTF-16LE) is absent from all of them (D6).
- [ ] **Encrypted round-trip:** insert → query (`getAllEntries`/`findByText`) returns the row → delete → gone. Confirms the Keystore-wrapped passphrase opens the real DB.
- [ ] **Wrong-key (best-effort, D7):** open the existing encrypted file with a deliberately wrong passphrase via `SupportOpenHelperFactory`; assert a `SQLiteException`-family failure on first access. If not cleanly catchable → `@Ignore("wrong-key not cleanly testable on net.zetetic 4.6.0 — see known-test-skips")` + log it.
- [ ] Run on AVD: boot `Medium_Phone_API_36.1`, `./gradlew connectedDebugAndroidTest`.

### T5 — Discipline baselines
- [ ] Update `docs/known-test-failures.md`: replace the "no suite exists" status note; table stays empty (no approved failures).
- [ ] Update `docs/known-test-skips.md`: set the real baseline skip count (0, or 1 with dated reason if T4 wrong-key degraded).

### T6 — Context index & changelog
- [ ] Add every new file to `docs/context/Context_Index_File.md`.
- [ ] CHANGELOG entry (mandatory) — handled in Phase 5.7 context-docs gate, but note it here.

---

## Human Testing Plan (manual, Phase 4 STOP)

This epic is test infrastructure — most verification is the Gradle suites themselves. The human handoff confirms the suites run on the user's machine + emulator and the **app still works** (test wiring didn't regress the build):

1. **JVM suite:** run `./gradlew :app:testDebugUnitTest --no-daemon`. Expect all green; note the summary line.
2. **Instrumented suite:** boot the emulator (`~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1`), then `./gradlew connectedDebugAndroidTest`. Expect green; confirm the at-rest test passed (sentinel absent).
3. **App still builds & runs:** `./gradlew :app:assembleDebug`, sideload, enable the IME, type a few keys, copy text and confirm it appears in clipboard history. (Regression check that the build-file changes didn't break the app.)
4. **Optional sanity:** temporarily break one production method (e.g. flip the dedup `delete` line), re-run the unit suite, confirm a test goes RED, then revert (A7 confidence check).
