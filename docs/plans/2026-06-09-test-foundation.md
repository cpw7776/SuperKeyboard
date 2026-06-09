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

## Phase 5 learning log (to be filled in Phase 5)

_Plan-vs-reality, surprises, and what we'd do differently land here after implementation._
