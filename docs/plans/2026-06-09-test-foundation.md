# Plan Snapshot — Test Suite Foundation (`test-foundation`)

> **Created:** 2026-06-09 (Phase 2.3, `/writing-plans`). This is the **initial plan snapshot** — what we believed before writing any code. Phase 5 updates it as a learning doc (plan-vs-reality). The PRD (`docs/prd/PRD_Test_Foundation.md`) is the executable checklist; this is the narrative + decision record.

## Why this epic

SuperKeyboard has **no test source set** — `app/src/test/` and `app/src/androidTest/` don't exist, and `gradle/libs.versions.toml` has zero test dependencies. This is **P0 #5** in `docs/context/PRODUCTION_READY.md`. The AI Dev Workflow Kit's 5th post-feature gate (Test-Suite Summary) is inert until a suite exists. This epic stands up both a JVM unit suite and an instrumented suite, then flips the 5th gate permanently on for every future epic (per `docs/KIT_DEVIATIONS.md`).

## Targets (chosen for high value ÷ low Android coupling)

| Target | Source | Suite | Why |
|--------|--------|-------|-----|
| `KeyboardState` | `ime/KeyboardState.kt` | JVM unit | Pure-Kotlin state machine, zero Android deps — ideal first unit test. |
| `ClipboardRepository` | `clipboard/ClipboardRepository.kt` | JVM unit (fake DAO) | Depends only on the `ClipboardDao` interface — fake DAO isolates real repo logic. |
| `ClipboardDatabase` | `clipboard/ClipboardDatabase.kt` | Instrumented | Proves the **encryption-at-rest privacy promise** with the real SQLCipher + Keystore path. |

## Task breakdown (execution order)

1. **Infra/build wiring** — test deps in the version catalog; `testImplementation`/`androidTestImplementation` in `app/build.gradle.kts`; `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`. Establish a green empty baseline.
2. **`KeyboardStateTest`** (JVM) — shift cycle, auto-shift, symbols pages, emoji toggles.
3. **`FakeClipboardDao` + `ClipboardRepositoryTest`** (JVM) — dedup, expiry math, pin, clear/cleanup delegation.
4. **`ClipboardDatabaseEncryptionTest`** (instrumented) — at-rest sentinel scan, encrypted round-trip, best-effort wrong-key.
5. **Run both suites green** — JVM (`:app:testDebugUnitTest`), instrumented (`connectedDebugAndroidTest` on AVD `Medium_Phone_API_36.1`).
6. **Discipline baselines** — update `docs/known-test-failures.md` / `docs/known-test-skips.md` from "no suite" stubs to a real baseline.

## Decisions (carried into the ADR)

| ID | Decision | Chosen | Rationale (short) |
|----|----------|--------|-------------------|
| D1 | Test framework | **JUnit4** | Shared with `AndroidJUnitRunner`; one model across both source sets. |
| D2 | Repository isolation | **Hand-written `FakeClipboardDao`** (in-memory store, not call-recorder) | Kit anti-over-mocking (A2); dedup must be behaviourally observable (A4). |
| D3 | Expiry-time assertion | **Range assertion** around `System.currentTimeMillis()` | No production Clock seam needed; avoids wall-clock flakiness (A7-adjacent). |
| D4 | Turbine in v1 | **Defer** (departs from Phase-0 locked stack) | Repo Flow methods are pure pass-throughs; a Turbine test would assert the fake's own StateFlow (A4). Add when real Flow logic exists. |
| D5 | Instrumented clean-slate | **Reflection reset** of the `instance` singleton + delete DB/passphrase files | No production change (locked scope); isolated to one test helper. |
| D6 | At-rest scan scope | **Scan all `clipboard.db*` sidecars** (db/-wal/-shm), UTF-8 + UTF-16LE | Robust to WAL without forcing a close/checkpoint against the singleton. |
| D7 | Wrong-key negative test | **Best-effort**; degrade to `@Ignore` + dated skip if not cleanly catchable | At-rest + round-trip are non-negotiable; this is the bonus third (A8 discipline). |

## Test stack (final)

JUnit4 · `kotlinx-coroutines-test` (`runTest`) · Truth (assertions) · `androidx.test` core / ext-junit / runner (instrumented) · hand-written fake DAO. **No MockK. No Turbine in v1** (D4).

## Skills map

| Step | Skill |
|------|-------|
| Every TDD red step | `/tdd`, read `docs/context/Unit_Test_Writing_Guide.md` |
| Every implement step | read `docs/context/Implementation_Patterns.md` |
| Per-task review | `/code-review` (`high` if significant new logic) |
| Pre-test gate (3.5) | `code-quality-agent` Pre-Test mode |
| Build/sideload reference | `docs/mobile/Android_Build_and_Sideload.md` |

## Risks (see plan file for full list)

- **R1** SQLCipher native lib — instrumented test defensively `System.loadLibrary("sqlcipher")` in `@BeforeClass` (app loads it in `SuperKeyboardApp.onCreate()`).
- **R2** Keystore on emulator first-run flakiness — mitigated by clean-slate reset (D5).
- **R3** Reflection-based singleton reset is rename-brittle — isolate + comment.
- **R4** A backfilled test may go RED on a real defect — fix the **code** per TDD, don't bend the test.

## Estimated scope

2 build files + 2 discipline docs modified; ~4 new test files + 2 new source-set dirs; ~12–16 JVM cases + ~3 instrumented; 0 migrations; **0 production-code change expected**.

---

## Retrospective (Phase 5)

**What went as planned (everything material):** The epic executed exactly as the Plan Mode plan and PRD described. All 6 task groups (T1–T6) landed in the planned order; 27 tests written (24 JVM + 3 instrumented), all green; **zero production-code change**, zero `fix(` commits, zero manual-test bugs. The diff (`git diff --stat 3388949..HEAD`) contains only test files, build wiring, and docs — no surprises.

**Tradeoff decisions revisited (all held):**
- **D1–D3, D5, D6** held as chosen with no friction.
- **D4 (defer Turbine)** held — confirmed correct: nothing in the repo needed a Flow-logic test, so Turbine would have been an unused dep.
- **D7 (wrong-key test best-effort)** resolved *better* than planned: `wrongPassphrase_cannotOpenDatabase` passed cleanly on `net.zetetic:sqlcipher-android:4.6.0`, so the contingency `@Ignore` + skip-log was **not** needed — baseline stays 0 skips.

**What we didn't foresee:**
- **Coroutines version skew risk.** Had to probe the transitively-resolved `kotlinx-coroutines-core` (1.7.3 via room-ktx) and pin `coroutines-test` to match. Cheap once spotted; would have caused confusing runtime errors if mismatched. Worth doing on any project before adding `coroutines-test`.
- **Emulator install conflict.** `connectedDebugAndroidTest` failed first run with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — an orphaned `io.superkeyboard` install on the AVD signed with a different key. `adb uninstall` cleared it; re-run was green. Worth a one-line note in the build/sideload doc's troubleshooting for the emulator path.

**Process miss (planning, not code):** At the Phase 4 manual-test handoff I pointed the user at the running emulator + `adb install` for on-device testing. The user corrected it: manual testing is **APK → `apk-releases/` → Syncthing → physical phone**, never the emulator (emulator is only for instrumented Gradle tests). Captured durably: memory `manual-testing-via-apk` + test-plan fix (`6ea338a`) + clearer device split in `test-foundation-tests.md`. Lesson for future epics: default the manual-test handoff to the APK-delivery loop.

**What we'd do differently:** Probe the coroutines version *during planning* (it's a known Android gotcha), and shut the emulator down immediately after the instrumented suite passes rather than leaving it up "for manual testing" (it was never the manual surface).

**Patterns to reuse:**
- `FakeClipboardDao` — hand-written, in-memory, *behavioural* (stores rows) fake. Generalises to any future DAO repo test; keep fakes hand-written over a mocking framework (D2/A2).
- Range-assertion for `System.currentTimeMillis()`-based code (D3) instead of forcing a Clock seam — tests real code without flakiness or a production change.
- Reflection-reset of a process-wide DB singleton for instrumented clean-slate (D5) — a faithful-fidelity alternative to adding a `@VisibleForTesting` production seam.
- Scan-all-sidecars (`db`/`-wal`/`-shm`) for an encryption-at-rest assertion (D6) — robust to WAL without a checkpoint dance.
