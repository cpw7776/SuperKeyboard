# Test Suite Retro (Unit & Integration)

> **Purpose:** Self-improvement loop for the project's **unit and integration test suite**. When a bug surfaces that the suite should have caught, this protocol identifies why it passed over broken code, root-causes the miss, and updates the canonical `docs/context/Unit_Test_Writing_Guide.md` if the lesson is generalisable. Each miss also gets a one-line entry in `docs/test-suite-misses.md` (the chronological audit log) regardless of whether a guide update applies.

> **Standalone — use anytime a bug is found that unit or integration tests should have caught.** Also invoked automatically from `bugfix.md` (Phase 1.4 → 2.4) and `feature-lifecycle.md` (Phase 5.1 Gap A).

> **Sibling protocol:** `docs/prompts/testing-retro.md` does the same job for the **browser testing-agent library** (Gap B). The two protocols are deliberately parallel but cover different surfaces: this one for code-level test honesty, that one for the persistent browser-test library. A given bug retro may need to run **both** — Gap A AND Gap B — if both layers missed it.

> **Philosophy:** A bug that escaped the unit suite means one of three things: the suite has a *honesty problem* (it asserts the wrong thing), a *scope problem* (it doesn't cover the relevant seam), or a *blind spot* (a pattern this codebase keeps falling into). Fixing the bug without diagnosing which one is which guarantees the same class of bug comes back.

**The Miss:** [INSERT BUG DESCRIPTION + WHICH TEST(S) RAN AND PASSED OVER THIS BROKEN BEHAVIOUR, OR "NO TEST COVERED THIS" IF THE GAP IS ABSENCE OF COVERAGE]

---

## When to Run This Protocol

Run whenever a fault meets BOTH of these conditions:

1. The fault is in code/behaviour that unit or integration tests **could** have caught (i.e., not a pure-UI rendering issue, not a deployment/infra problem, not a third-party outage).
2. The fault escaped to manual testing, production, or surfaced via a bugfix protocol — i.e., the unit/integration suite was green when it shouldn't have been.

**Skip this protocol when:**
- The bug was caught at unit-test time (RED step) before the implementation was written — that's TDD working as designed.
- The bug is **only** observable in the browser (e.g., layout overflow, CSS rendering) — that's Gap B, run `testing-retro.md` instead.
- The bug is in a layer fundamentally outside the suite's reach (cron jobs running in production with prod-only data, third-party webhooks with no local reproduction). Note the exemption in the retro report.

---

## Phase 1: Identify What Should Have Caught This

### 1.1 Find the existing tests closest to the broken behaviour

Search the test suite for tests that touch:
- The function / module / route where the bug lives
- The data shape involved
- The user flow (for integration tests) the bug appeared in

Three possible findings:

- **Tests exist and passed over the broken code.** Read them carefully — what are they actually asserting? This is the most common case and the most informative; the test suite isn't *missing*, it's *dishonest*.
- **Tests exist but were skipped.** Check `docs/known-test-skips.md` for the reason. A skip adjacent to the bug's scope is itself the finding.
- **No tests cover this code.** The gap is absence, not dishonesty. Different fix shape (Category F below).

### 1.2 Read the closest existing test honestly

For each candidate test:
- What does the test name claim it asserts?
- What does the test body actually assert?
- What does the test mock — and is the mock hiding the bug?
- Does the assertion fire? (Async tests in particular can silently no-op.)

Capture a 1-3 sentence note per relevant test: "Test `X` claims Y, actually asserts Z, missed the bug because W."

### 1.3 Reproduce the bug as a failing test

Before going further, write a test that **fails against the current buggy code** (RED). This is non-negotiable — Phase 2 categorisation is more accurate when you've actually seen the failure happen in a test you wrote. The test you write here becomes the artefact that proves the gap is closed in Phase 5.

---

## Phase 2: Root-Cause the Miss

Categorise the miss into **exactly one** of these buckets. Each bucket maps to a different fix shape and a different anti-pattern in `Unit_Test_Writing_Guide.md`.

### Category A — Wrong assertion (asserting implementation, not behaviour)

The test ran the right code but asserted the wrong thing. E.g., it checked that a function was called, not that the resulting value was correct. Maps to anti-pattern **A1** in the guide.

**Fix shape:** Rewrite the assertion to check user-observable behaviour. The test name may also be wrong (A10) — update both.

### Category B — Over-mocking (mock hid the bug)

The test mocked the layer where the bug lives, so the bug couldn't be reached. E.g., mocked the DB client when the bug is in the SQL. Maps to anti-pattern **A2**.

**Fix shape:** Move the mock outward (to the system boundary) OR convert the test to an integration test that runs the real seam. The line between unit and integration depends on what's being verified — be honest about which one you actually need.

### Category C — Missing scenario / edge case (happy-path-only)

The test covered the happy path but missed an error path, empty input, boundary value, or repeated invocation. Maps to anti-pattern **A3**.

**Fix shape:** Add the missing scenario(s). At minimum: valid + invalid + empty + boundary + downstream error.

### Category D — Asserting the mock (tautological test)

The mock's return value flowed straight to the assertion; the implementation was never really tested. Maps to anti-pattern **A4**.

**Fix shape:** Assert something the mock couldn't decide alone — a transformation, a derived value, or a side effect captured at a different layer.

### Category E — Test data didn't reflect real shape

The test used minimal placeholder data; production hit unicode, nulls, missing optional fields, or other shapes the test never exercised. Maps to anti-pattern **A5**.

**Fix shape:** Add fixtures that mirror real-world shapes; use them everywhere. If this codebase has a recurring data-shape gotcha, it becomes a Project Lesson.

### Category F — No coverage at all (absence)

No test existed for this code or this seam. The fix is to write one — but the retro is still valuable because *why* there was no test is itself a finding (was it a gap in the PRD's test plan? A seam that's not documented as a test target? An area the team thought "didn't need" tests?).

**Fix shape:** Write the missing test(s) — both unit at the right layer and integration at the seam if a seam was involved. Note in the retro report which artefact (PRD, ADR, this guide) should have flagged the need for coverage.

### Category G — Async / silent no-op

The test exists and looks like it asserts the right thing, but the assertion never actually fires (unawaited promise, missing return in a test that returns a promise, a `try/catch` that swallows the assertion failure). Maps to anti-pattern **A7**.

**Fix shape:** Make the assertion observable. The Phase 1.3 reproduction test verifies the fix — if your new test goes RED against broken code and GREEN against fixed code, the silent no-op is gone.

### Category H — Integration gap at a seam

Each unit passed in isolation; the composition broke. Maps to anti-pattern **A6**.

**Fix shape:** Add an integration test at the seam. This is often a Project Lesson candidate because seams are codebase-specific (e.g., "in this codebase, every route → service handoff must have an integration test that calls the route handler directly, not via mocks").

---

## Phase 3: Apply the Fix

### 3.1 Write or rewrite the test(s)

For Categories A–E, G: rewrite the existing test(s) you identified in Phase 1.1 so they fail against the buggy code. For Category F: write the missing test from scratch. For Category H: add the integration test in addition to (not instead of) the unit tests.

**The Phase 1.3 RED test counts as the first part of this.** Now ensure:
- The test name describes what it actually checks (A10 discipline).
- The assertion is on a value/effect the implementation must produce, not on a mock the test set up itself.
- The test goes RED against pre-fix code, GREEN against fixed code. Run it both ways to prove this — once with the fix reverted/stashed, once with it applied.

### 3.2 Append to the chronological miss log

Append a one-line entry to `docs/test-suite-misses.md` for **every** retro, regardless of tier:

```markdown
| {YYYY-MM-DD} | {short bug summary} | {Category A-H} | {commit/PR ref of fix} | {Tier 1/2/3} | {link to guide section if Tier 2/3, else `—`} |
```

This log is the audit trail. It's how you discover patterns ("we keep filing Category B misses for our DB layer — that's a Project Lesson").

### 3.3 Decide: does this miss generalise?

Ask: **"If a new agent / engineer were writing a test in this codebase tomorrow, would knowing about this miss in advance change how they write the test?"**

- **No** — this was a one-off (forgot to await one specific promise; specific fixture had a typo): Tier 1. Stop after 3.2. The log entry is the full record.
- **Yes — and it's project-specific** (we have a particular seam, a particular validator pattern, a particular data shape that keeps biting us): Tier 2. Continue to 3.4.
- **Yes — and it applies to any codebase using this kit** (the universal anti-patterns are missing a class of mistake): Tier 3. Continue to 3.5.

### 3.4 Tier 2 — Append a Project Lesson to the Guide

Open `docs/context/Unit_Test_Writing_Guide.md` and append an entry under `## Project Lessons` using this shape:

```markdown
### {YYYY-MM-DD} — {short rule name}

- **Type:** anti-pattern | pattern | boundary
- **Miss log ref:** [{YYYY-MM-DD} — {bug summary}](../test-suite-misses.md#{anchor})
- **Rule:** {1-2 sentence imperative — e.g., "In this codebase, every form has both client and server validation; tests must hit the server validator with a request that bypasses the client validator."}
- **Why:** {1-2 sentences on the bug that motivated this — what slipped past without it}
- **Example:** {short code snippet, max ~8 lines, showing the test shape that obeys the rule}
- **Related anti-patterns:** {A1-A11 references where applicable}
```

The next time `feature-lifecycle.md` Phase 3.3 (or `bugfix.md` Phase 2.1) writes a test, the Unit Test Writing Guide is read first — so this lesson immediately starts mattering.

**Do NOT add lessons by hand outside this protocol.** The discipline is: every Project Lesson traces back to a real miss in the log. That keeps the guide trusted and prevents speculative bloat.

### 3.5 Tier 3 — Propose a Universal Anti-Pattern change to the kit

The universal anti-patterns A1–A11 already cover most generic test mistakes. A Tier 3 finding is *new* — it means the universal section needs a new entry or a sharpening of an existing one.

**Do not silently edit the kit.** Present the proposed change to the user with:
- The exact patch to `docs/context/Unit_Test_Writing_Guide.md` (a new A12, or a rewrite of an existing entry)
- The miss log entry that motivated it
- A confidence statement: "This applies to project X, Y, Z because [reason]"
- An ask for explicit approval before the edit lands

Tier 3 propagations are rare. If you find yourself proposing one frequently, you're probably mis-categorising Tier 2s.

---

## Phase 4: Verify the Gap Is Closed

Before declaring the retro complete:

1. The Phase 1.3 / 3.1 test that was RED against pre-fix code is now GREEN against fixed code. Confirmed by running it both ways.
2. The full test suite passes after the fix.
3. The chronological miss log has the new entry.
4. If Tier 2: the guide has the new lesson with a working link to the miss log entry.
5. If Tier 3: the proposed kit change is presented to the user and awaiting approval.

---

## ⏸️ STOP: Present Retro Summary

After Phases 1–4, present:

```markdown
## Test Suite Retro: {bug summary}

**Miss Category:** {A wrong assertion | B over-mock | C missing scenario | D asserting mock | E test data | F no coverage | G async no-op | H integration gap}
**Tier:** {1 one-off | 2 project lesson | 3 universal}

### What the suite missed
{1-2 sentences — what the existing test asserted vs what it should have, OR "no test covered this code"}

### Test(s) changed or added
- {path/to/test.spec.ts} — {what changed: rewrote assertion / removed mock at X layer / added integration test / etc.}

### Verification
- Failing test confirmed RED against pre-fix code: {yes / no — explain}
- Test now GREEN against fixed code: {yes / no}
- Full suite green: {yes / no}

### Miss log entry
- Appended to `docs/test-suite-misses.md` — line: `{the verbatim row added}`

### Guide update
- **Tier 1:** No guide update — one-off miss.
- **Tier 2:** Project Lesson appended to `docs/context/Unit_Test_Writing_Guide.md` under section "{section name}". The next test writer will see this.
- **Tier 3:** Universal anti-pattern change proposed — AWAITING USER APPROVAL before editing the kit.

### Files changed
- {path/to/test} — {what changed}
- docs/test-suite-misses.md — {one new row}
- docs/context/Unit_Test_Writing_Guide.md — {Tier 2 only: new Project Lesson section}
- {kit files — Tier 3 only, after approval}
```

Wait for user acknowledgement before closing. For Tier 3, wait for **explicit approval** before editing the universal anti-patterns section.

---

## Integration With Other Prompts

### From `bugfix.md`

- **Phase 1.4 — Test Autopsy** is the investigation half of this protocol (Phase 1 here). The bugfix protocol already does Phase 1.1–1.2 inline.
- **Phase 2.1 — Fix the Tests First (TDD)** is the test-writing half (Phase 1.3 + 3.1 here).
- **Phase 2.4** runs this retro to make the autopsy *persistent* — without it, the autopsy is in a bug report no one will read again. The retro is what produces the durable artefacts (miss log + guide update).

When `bugfix.md` reaches Phase 2.4, run Phases 3.2–3.5 of this retro inline (Phases 1 and 2 are already done as part of the autopsy + TDD steps). One commit is fine when the changes are small.

### From `feature-lifecycle.md`

- **Phase 5.1 Gap A** invokes this retro for every bug surfaced during manual testing that the unit suite should have caught.
- Phase 5.1 Gap B (`testing-retro.md`) runs in parallel for the same bug if the browser test library also missed it. Both retros are valid; they cover different surfaces.

### From manual invocation

Paste this file as a prompt, fill in `[INSERT BUG DESCRIPTION + WHICH TEST(S) RAN AND PASSED]`, and run through Phases 1–4 + the STOP block.

---

## Quality Checklist

Before declaring the retro complete:

- [ ] Existing test(s) closest to the bug identified (or "no test covered this" noted)
- [ ] Failing reproduction test written, confirmed RED against pre-fix code
- [ ] Miss categorised into exactly one bucket (A–H) with written justification
- [ ] Test(s) rewritten / added; confirmed GREEN against fixed code; full suite green
- [ ] Row appended to `docs/test-suite-misses.md`
- [ ] Tier decision made (1 / 2 / 3) with reasoning
- [ ] Tier 2: Project Lesson appended to `Unit_Test_Writing_Guide.md` with all six fields (type / log ref / rule / why / example / related)
- [ ] Tier 3: change proposed to user, NOT silently applied
- [ ] Retro summary block presented to the user

If any item is unchecked, the retro is not complete — fix the gap, don't move on.
