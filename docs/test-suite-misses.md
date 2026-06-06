# Test Suite Misses — Chronological Log

> **Purpose:** Append-only audit trail of every bug the unit/integration test suite failed to catch. Every entry here is the output of a `docs/prompts/test-suite-retro.md` run.
>
> **This file is exhaustive, not curated.** Every miss lands here, regardless of whether it produced a Project Lesson in `docs/context/Unit_Test_Writing_Guide.md`. The guide stays scannable; this log stays complete.
>
> **Maintenance:** Do NOT hand-edit. New entries are appended by `test-suite-retro.md` Phase 3.2. If you spot a row that looks wrong, fix it via a retro, not directly — the retro is the discipline that keeps the data honest.

---

## How to read this log

- **Date** — when the miss was retro'd (usually same day or day after the bug was found)
- **Bug** — one-line summary, enough to recognise it
- **Category** — one of A–H from `test-suite-retro.md` Phase 2:
  - A — Wrong assertion (asserting implementation, not behaviour)
  - B — Over-mocking (mock hid the bug)
  - C — Missing scenario / edge case
  - D — Asserting the mock (tautological)
  - E — Test data didn't reflect real shape
  - F — No coverage at all (absence)
  - G — Async / silent no-op
  - H — Integration gap at a seam
- **Fix ref** — commit hash, PR number, or path to bug report — anything that lets a reader find the actual change
- **Tier** — 1 (one-off), 2 (project lesson — guide updated), 3 (universal — kit change proposed)
- **Guide ref** — if Tier 2 or 3, link/anchor to the `Unit_Test_Writing_Guide.md` section that was added or sharpened. `—` if Tier 1.

---

## How to use this log to find patterns

Run a retro pattern-check whenever:

- This file gains 5+ new entries since the last check
- A single Category appears 3+ times within a quarter
- A single subsystem appears 3+ times in any Category

When a pattern emerges, the right response is to run a **pattern-level retro** — not a per-bug retro — and capture the rule in the guide as a Project Lesson. That's the moment Tier 1s should have been Tier 2s; promotion is allowed retroactively.

---

## Log

| Date | Bug | Category | Fix ref | Tier | Guide ref |
|------|-----|----------|---------|------|-----------|
| _(no entries yet)_ | | | | | |

<!-- Append new rows above this line. Newest at the top. -->

---

## Out-of-scope exemptions

Some areas of the codebase are exempt from unit-test discipline because they cannot reasonably be tested at the unit/integration layer. Document them here so the next retro doesn't keep filing Category F misses against them.

| Area | Reason exempt | Compensating control |
|------|---------------|---------------------|
| _(none — populate as exemptions are agreed)_ | | |

**Discipline:** A miss against an exempt area is NOT a test-suite miss — it's a manual-testing miss or a monitoring miss. Route it to the appropriate retro (e.g., add a manual-test step in the PRD, add a runtime alert).
