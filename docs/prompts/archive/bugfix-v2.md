# Bug Fix Protocol (v2)

> **ARCHIVED — superseded by v3.** See `docs/prompts/bugfix.md` for the current version. v2 → v3 added Gap A (unit-test self-improvement via `test-suite-retro.md`) alongside the existing Gap B (browser-test self-improvement via `testing-retro.md`).

> **Standalone — use anytime. Not part of the feature lifecycle.**

> **Philosophy:** Investigate thoroughly, plan the fix, then execute. One stop point — after the investigation and plan, before applying changes.

> **v2 change:** Added Phase 2.4 — auto-runs `testing-retro.md` when the bug surfaced in manual testing or production. Closes the testing-agent gap at the same time as the code gap.

**The Bug:** [INSERT BUG DESCRIPTION]

## Skills to Invoke
- `/vulnerability-scanner` — Check if this is a security issue
- `/tdd` — Write failing test for the bug
- `docs/prompts/testing-retro.md` — Adapt the testing agent that should have caught this (auto-invoked at Phase 2.4 when applicable)

---

## Phase 1: Investigation (AUTONOMOUS)

### 1.1 Bug Report
Create `docs/bugs/Bug_report_[BUG_NAME].md`:

- **Timeline:** When did this appear? What changes preceded it? What's been tried?
- **Feature Logic:** How should it work? What interacts with it?
- **Current vs Expected Behavior**
- **3 Hypotheses:** Ranked by probability
- **Elimination Strategy:** One specific test per hypothesis to confirm or rule out

### 1.2 Context Audit
- Review `docs/context/Context_Index_File.md`, `docs/context/CHANGELOG.md`, and relevant docs
- Flag overlapping or outdated context causing confusion

### 1.3 Execute Diagnostics
- Run all three diagnostic tests from the elimination strategy
- Confirm or rule out each hypothesis with evidence
- Identify the root cause

### 1.4 Test Autopsy — Why Did Existing Tests Pass?

**This step is MANDATORY.** Before planning the fix, you must understand why the test suite didn't catch this bug. A bug that slipped past tests means the test suite has a gap that must be closed — otherwise it will happen again.

Investigate and document:

1. **Which existing tests _should_ have caught this?** Find the tests closest to this functionality. If none exist, that's the gap.
2. **Why didn't they catch it?** Common reasons:
   - Tests assert implementation details instead of user-visible behavior
   - Tests mock too aggressively (e.g., mocking the exact layer where the bug lives)
   - Tests cover the happy path but miss the edge case / error path / boundary condition
   - Tests check the wrong thing (e.g., "component renders" instead of "component shows correct data")
   - Integration between components isn't tested — each unit passes in isolation but fails when composed
   - Test data doesn't reflect real-world data shapes
3. **What test(s) would have caught it?** Describe the specific assertions that were missing.

Document all of this in the Bug Report under a **"Test Autopsy"** section.

### 1.5 Resolution Plan
- **New/Rewritten Tests:** Using the autopsy findings, write tests that:
  - Fail against the current buggy code (proving they catch the bug)
  - Test behavior, not implementation
  - Cover the specific gap identified in the autopsy
- **Fix Strategy:** Step-by-step changes needed
- **Existing Test Fixes:** If existing tests were asserting the wrong thing or mocking incorrectly, list which tests need rewriting — do not leave broken test logic in place
- **Security Check:** Run `/vulnerability-scanner` to assess if this is a security issue
- **Rollback Plan:** Alternative approach if fix fails

---

## ⏸️ STOP: Present Investigation & Fix Plan

Present:
1. **Bug Report summary** — root cause with evidence
2. **Test Autopsy** — why existing tests didn't catch this, which tests need writing/rewriting
3. **Proposed fix** — step-by-step, what files change and why
4. **Security assessment** — is this a security issue?
5. **Rollback plan** if the fix doesn't work

Wait for user approval before applying changes.

---

## Phase 2: Execution (AUTONOMOUS after approval)

### 2.1 Fix the Tests First (TDD)
1. Write/rewrite tests identified in the Test Autopsy — they MUST fail against the current buggy code
2. If existing tests had wrong assertions or over-mocking, rewrite those too
3. Confirm all new/rewritten tests fail (proving they actually test the right thing)

### 2.2 Fix the Code
4. Apply the fix (all steps from the resolution plan)
5. Verify ALL new/rewritten tests now pass
6. Run `[TEST_COMMAND]` — all tests must pass (old and new)
7. Run `[BUILD_COMMAND]` — zero errors

### 2.3 Wrap Up
8. Run `/vulnerability-scanner` on changed code
9. Update Bug Report with solution + test autopsy results
10. Add preventive "Coding Rule" to system instructions if applicable
11. Log in PRD under "Known Issues / Resolved Bugs"
12. Remove ALL diagnostic logging
13. Commit with descriptive message

### 2.4 Testing Agent Retro (AUTO — when applicable)

**Decision:** Did this bug surface in manual testing, production, or anywhere past Phase 4 of the feature lifecycle (i.e., somewhere the testing-agent library was supposed to catch it)?

- **Yes** → Run `docs/prompts/testing-retro.md` against this bug. The retro will:
  1. Identify which testing agent in `docs/testing-agents/REGISTRY.md` owned the scope (or flag "no owner").
  2. Categorize the miss (A scope / B scenario / C assertion / D verification / E setup).
  3. Adapt that agent (or create a new one) — Adapt Mode of `create-testing-agent.md`.
  4. Append a Miss Log entry on the agent.
  5. Decide tier propagation (feature-only / project-pattern / skill-universal).

  Commit the testing-agent change in the same commit as the code fix when small, or as a separate `fix(testing-agent): retro for {bug-id}` commit when substantial. Update `docs/testing-agents/REGISTRY.md` in the same commit if scope changed.

- **No** (bug found at design time, unit-test time, or in code review before Phase 4) → Skip 2.4. The testing-agent library is not at fault.

- **The bug is in an area testing agents cannot observe** (background jobs, webhooks with no UI, etc.) → Skip 2.4. Confirm the area is listed in the **Out-of-Scope Exemptions** table in `REGISTRY.md`; add it if missing.

Report: what changed, what was fixed, what test gaps were closed (code-level), what testing-agent gaps were closed (browser-level via retro), any manual verification needed.
