# ADR — Test Suite Foundation (`test-foundation`)

> **Status:** Accepted (2026-06-09). **Context:** first test suite in the repo (P0 #5). **Companions:** PRD `docs/prd/PRD_Test_Foundation.md`, plan snapshot `docs/plans/2026-06-09-test-foundation.md`.

## Context

The app has no test source set and no test dependencies. We are standing up the first JVM unit suite and the first instrumented suite over three targets (`KeyboardState`, `ClipboardRepository`, `ClipboardDatabase`). The decisions below are about **test architecture**, not product features — but two of them touch the project's hard privacy constraint, so they belong in an ADR.

> **Privacy note (project constraint).** This project requires an ADR for any outbound network call. This epic adds **none** — tests are fully offline. The privacy-relevant angle here is the inverse: the instrumented suite **verifies** the encryption-at-rest promise and must **not weaken** it. Specifically: tests must not log the decrypted clipboard text, the passphrase, or Keystore material (CLAUDE.md privacy rules); the at-rest test asserts plaintext is absent from disk; no test enables `allowBackup` or adds cloud sync. The reflection-based singleton reset (D5) touches only the in-process instance + local DB/passphrase files on the emulator — it does not alter the production encryption path or the Keystore alias `superkeyboard_clipboard_key`.

## Decisions

Each decision keeps its rejected options as the historical record of *why* the chosen one won.

### D1 — JUnit4 (not JUnit5)
- **Chosen:** JUnit4 across both source sets.
- **Rejected — JUnit5/JVM + JUnit4/instrumented:** two frameworks, extra `android-junit5` plugin, no payoff at this scale.
- **Why:** `AndroidJUnitRunner` is JUnit4-based; one model for unit + instrumented is simpler to maintain.

### D2 — Hand-written fake DAO (not MockK, not in-memory Room)
- **Chosen:** `FakeClipboardDao` implementing the `ClipboardDao` interface with a real in-memory backing store (+ `MutableStateFlow` for Flow methods).
- **Rejected — MockK:** the kit's anti-over-mocking stance (A2); a stubbed `findByText` asserts our own setup, not behaviour.
- **Rejected — in-memory Room:** pulls Room into the JVM set / needs Robolectric; the repo's logic (dedup/expiry/delegation) is cleanly exercised by a fake. Deferred to a future integration test.
- **Why:** the fake must *store* rows so `addEntry` dedup is observable behaviourally (A4), at zero extra dependency cost.

### D3 — Range assertion for expiry time (no Clock seam)
- **Chosen:** capture `before`/`after` around `addEntry` and assert `expiresAt ∈ [before+expiryMs, after+expiryMs]`; `Long.MAX_VALUE` exactly for `expiryMs ≤ 0`.
- **Rejected — inject a `Clock`/time-provider:** cleaner but a production change the locked scope excluded, for marginal value.
- **Why:** tests the real production code without a wall-clock flake (A7-adjacent) and without touching production.

### D4 — Defer Turbine (departs from Phase-0 locked stack) ⚠️
- **Chosen:** do **not** add Turbine in v1.
- **Rejected — include Turbine now:** the repo's Flow methods are pure pass-throughs; a Turbine test would assert the fake's own `StateFlow` (A4 — testing the double), adding an unused-ish dep and a no-signal test.
- **Why:** add Turbine the first time the repo has real Flow logic (a transform/filter over emissions). The Phase-0 handoff explicitly invited finalizing stack tradeoffs in Plan Mode; this is that finalization. Flagged to the user at the Plan Mode gate and approved.

### D5 — Reflection reset of the DB singleton (no production seam)
- **Chosen:** in instrumented `@Before`, reflectively close+null the `ClipboardDatabase` `instance` static field and delete `clipboard.db*` + `clipboard_passphrase`.
- **Rejected — `@VisibleForTesting clearInstanceForTest()`:** cleaner seam but a production change the locked scope excluded.
- **Rejected — tests build their own Room+SQLCipher instance:** lower fidelity — wouldn't exercise the real Keystore-wrapped passphrase path the privacy promise cares about.
- **Why:** keeps production untouched while giving each instrumented test a fresh, real, production-built DB. Risk (rename-brittle) is isolated to one commented helper.

### D6 — Scan all DB sidecars for the at-rest assertion
- **Chosen:** scan `clipboard.db`, `clipboard.db-wal`, `clipboard.db-shm` for the sentinel (UTF-8 and UTF-16LE), assert absent in all.
- **Rejected — force close/checkpoint then scan only `.db`:** couples the test to singleton lifecycle + journal mode.
- **Why:** SQLCipher under Room defaults to WAL; fresh rows may sit in `-wal`. Scanning all sidecars is robust and faithful (all are encrypted).

### D7 — Wrong-key negative test is best-effort
- **Chosen:** attempt opening the real encrypted file with a wrong passphrase and assert a `SQLiteException`-family failure; if not cleanly catchable on `net.zetetic:sqlcipher-android:4.6.0`, `@Ignore` it with a dated reason in `docs/known-test-skips.md`.
- **Why:** at-rest + round-trip are the non-negotiable two; this third strengthens the negative case but must not become a flaky/empty test (A8).

## Consequences

- **Positive:** kit 5th gate (Test-Suite Summary) becomes live for all future epics; the encryption-at-rest promise is now regression-protected; a reusable `FakeClipboardDao` exists for future repo tests.
- **Negative / debt:** instrumented suite requires a booted emulator (slower CI later); the reflection reset is a maintenance watch-point; Turbine and a real `Clock` seam are deferred (revisit when Flow/time logic grows).
- **Follow-ups (out of scope here):** Robolectric/instrumented coverage for `Context`-coupled classes (`ClipboardManagerService`, `KeyboardService` auto-capitalize, View rendering); CI wiring; real Room migrations (separate P0).

## Data flow / threats touched

No new data flow. Threat surface unchanged: the suite reads (does not transmit) the encrypted DB on the emulator, and asserts plaintext never reaches disk. `/vulnerability-scanner` is run in the Phase 3.5 / Phase 5.5 gates over the diff.
