# Bug Fix Protocol (v4)

> **Standalone — use anytime. Not part of the feature lifecycle.**

> **Philosophy:** Investigate thoroughly, plan the fix, then execute. One stop point — after the investigation and plan, before applying changes.

> **Model:** Switch the chat to **`opus`** before running this prompt. Bug fixes are dominated by reasoning — hypothesis ranking, root-cause analysis, the Phase 1.4 Test Autopsy (8-category miss vocabulary), and tier-propagation decisions in `test-suite-retro.md` / `testing-retro.md`. Once the plan is approved, the implementation portion is short and benefits from the same context, so stay on Opus through to the end rather than switching mid-flow. `testing-retro.md` and the named sub-agents it touches (`testing-agent`, `context-docs-agent`, `docs-auditor-agent`) run on Sonnet per their own frontmatter — that split is intentional and follows the same model split used in `feature-lifecycle.md` (Opus on the critical path, Sonnet on structured-execution sub-agents).

> **v4 change (paired with feature-lifecycle v5 — kit v5.14, Gap C):** Phase 2.4 now runs a **third retro — Gap C (implementation/code lesson)** alongside Gap A. A bug fix is the richest source of code lessons, so its autopsy captures the *fault-class* (named in Phase 2.2) into `docs/context/Implementation_Patterns.md` — the code-side mirror of the test guide — with the same Tier 1/2/3 propagation as Gap A. Phase 2.2 reads `Implementation_Patterns.md` before fixing so the fix doesn't re-introduce a known fault-class.

> **v3 change (paired with feature-lifecycle v4 — Test Suite Self-Improvement):** Phase 1.4 Test Autopsy and Phase 2.4 now invoke `docs/prompts/test-suite-retro.md` for Gap A (unit/integration test honesty) in addition to the existing `testing-retro.md` for Gap B (browser test library). Misses are logged to `docs/test-suite-misses.md`; generalisable lessons land in `docs/context/Unit_Test_Writing_Guide.md` so the next TDD test author sees them. Phase 1.4 now uses an 8-category vocabulary; Phase 2.1 reads the guide before rewriting tests.

> **v2 change:** Added Phase 2.4 — auto-runs `testing-retro.md` when the bug surfaced in manual testing or production. Closes the testing-agent gap at the same time as the code gap.

**The Bug:** [INSERT BUG DESCRIPTION]

## Skills to Invoke
- `/vulnerability-scanner` — Check if this is a security issue
- `/tdd` — Write failing test for the bug
- `docs/prompts/test-suite-retro.md` — Capture the unit-test miss and update `Unit_Test_Writing_Guide.md` if generalisable (Gap A — auto-invoked at Phase 1.4 + 2.4)
- `docs/prompts/testing-retro.md` — Adapt the browser testing agent that should have caught this (Gap B — auto-invoked at Phase 2.4 when applicable)
- `docs/context/Implementation_Patterns.md` — Capture the code fault-class lesson (Gap C — read before fixing at Phase 2.2, appended at Phase 2.4); the code-side mirror of `Unit_Test_Writing_Guide.md`

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

**Read `docs/context/Unit_Test_Writing_Guide.md` first.** The universal anti-patterns A1–A11 are the vocabulary the autopsy will use; the `## Project Lessons` section captures this codebase's recurring blind spots. If a relevant lesson exists, the answer to "why didn't we catch this" may already be in the guide.

Then run **Phase 1 of `docs/prompts/test-suite-retro.md`** (Identify What Should Have Caught This) as part of the investigation:

1. **Which existing tests _should_ have caught this?** Find the tests closest to this functionality. If none exist, that's the gap (Category F — no coverage).
2. **Why didn't they catch it?** Map to one of the eight retro categories:
   - **A wrong assertion** — tests assert implementation details instead of user-visible behaviour
   - **B over-mock** — tests mock too aggressively (the layer where the bug lives is mocked away)
   - **C missing scenario** — tests cover the happy path but miss an edge case / error path / boundary
   - **D asserting the mock** — the mock's return value flows straight to the assertion (tautological)
   - **E test data shape** — test data doesn't reflect real-world data shapes
   - **F no coverage** — no test existed for this code
   - **G async no-op** — the assertion never actually fires (unawaited promise, swallowed error)
   - **H integration gap** — each unit passes in isolation; the composition breaks
3. **What test(s) would have caught it?** Describe the specific assertions that were missing. This becomes the failing reproduction test in Phase 2.1.

Document all of this in the Bug Report under a **"Test Autopsy"** section, including the chosen category and which guide anti-pattern it maps to. The full retro (with miss log + Tier 2 guide update if applicable) runs in Phase 2.4 — but the categorisation happens here so it informs the fix plan.

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

**Read `docs/context/Unit_Test_Writing_Guide.md` before writing or rewriting any test in this step.** The autopsy in Phase 1.4 already categorised the miss; use that category's anti-pattern (A1–A11) to inform the *shape* of the rewritten test. If a Project Lesson in the guide covers this scenario, the rewrite must follow that lesson — that's the whole point of the lessons existing.

1. Write/rewrite tests identified in the Test Autopsy — they MUST fail against the current buggy code (the autopsy's category dictates the fix shape: A → fix the assertion; B → move the mock outward or convert to integration; C → add edge case; etc.)
2. If existing tests had wrong assertions or over-mocking, rewrite those too
3. Confirm all new/rewritten tests fail (proving they actually test the right thing). For async tests, **deliberately break the fix temporarily** and confirm the test goes RED — silent no-ops (Category G) are easy to write by accident.

### 2.2 Fix the Code

**Read `docs/context/Implementation_Patterns.md` before applying the fix.** Its universal code anti-patterns (I-series) + `## Project Lessons` are the vocabulary for the Gap C lesson capture in Phase 2.4, and reading them now keeps the fix from re-introducing a fault-class the codebase already learned about. As you fix, **name the fault-class the bug belonged to** (swallowed error, unvalidated boundary, drifted duplication, symptom-at-wrong-layer, etc.) — that naming feeds Gap C.

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

### 2.4 Retros (AUTO — Gap A + Gap C always; Gap B when applicable; Gap D check)

**Three retros may apply, plus a lightweight context check. Run each that fits.** Gap A (test honesty) and Gap C (code fault-class) always run — a bug fix is the richest source of *both* a test lesson and a code lesson. Gap B (browser-test library) runs when the bug escaped past Phase 4. Gap D (context gap) is a one-question check, not a retro.

#### Gap A — Test Suite Retro (unit/integration)

**Always run** `docs/prompts/test-suite-retro.md` from Phase 3.2 onwards. Phase 1 of the retro was already done as part of Phase 1.4 Test Autopsy (categorisation is in the bug report), and Phase 2.1 wrote the failing test. So the work remaining here is the **persistence half**:

1. **Phase 3.2 of the retro** — append a row to `docs/test-suite-misses.md` recording: date, bug summary, category (A–H), commit ref, tier, guide ref. This happens for every miss, regardless of tier.
2. **Phase 3.3 — tier decision.** Is the lesson generalisable beyond this one bug?
   - **Tier 1 (one-off):** stop after 3.2.
   - **Tier 2 (project lesson):** run Phase 3.4 — append a Project Lesson to `docs/context/Unit_Test_Writing_Guide.md` with the six required fields (type / log ref / rule / why / example / related anti-patterns). The next TDD test in this project sees it automatically.
   - **Tier 3 (universal anti-pattern change):** run Phase 3.5 — propose the change to the user with the exact patch. DO NOT silently edit kit-level files.
3. **Phase 4 — verify the gap is closed.** The Phase 1.3 RED test must be GREEN against the fix, and the full suite must pass.

Commit `docs/test-suite-misses.md` and `docs/context/Unit_Test_Writing_Guide.md` (if Tier 2) alongside the code fix when small, or as a separate `docs(test-suite-retro): {bug-id} ({Category X})` commit when substantial.

#### Gap B — Testing Agent Retro (browser)

**Decision:** Did this bug surface in manual testing, production, or anywhere past Phase 4 of the feature lifecycle (i.e., somewhere the testing-agent library was supposed to catch it)?

- **Yes** → Run `docs/prompts/testing-retro.md` against this bug. The retro will:
  1. Identify which testing agent in `docs/testing-agents/REGISTRY.md` owned the scope (or flag "no owner").
  2. Categorize the miss (A scope / B scenario / C assertion / D verification / E setup).
  3. Adapt that agent (or create a new one) — Adapt Mode of `create-testing-agent.md`.
  4. Append a Miss Log entry on the agent.
  5. Decide tier propagation (feature-only / project-pattern / skill-universal).

  Commit the testing-agent change in the same commit as the code fix when small, or as a separate `fix(testing-agent): retro for {bug-id}` commit when substantial. Update `docs/testing-agents/REGISTRY.md` in the same commit if scope changed.

- **No** (bug found at design time, unit-test time, or in code review before Phase 4) → Skip Gap B. The testing-agent library is not at fault. **Gap A still runs** — the unit suite is always in scope.

- **The bug is in an area testing agents cannot observe** (background jobs, webhooks with no UI, etc.) → Skip Gap B. Confirm the area is listed in the **Out-of-Scope Exemptions** table in `REGISTRY.md`; add it if missing.

#### Gap C — Implementation/Code Lesson

**Always run** (a bug fix is the richest source of code lessons — Gap A asks why the *test* missed it, Gap C asks whether the *code* was an instance of a fault-class the next implementer should avoid). Using the fault-class you named in Phase 2.2:

1. **Record the fault-class** against the I-series vocabulary in `docs/context/Implementation_Patterns.md` (swallowed error / unvalidated boundary / drifted duplication / shared mutable state / missing idempotency / symptom-at-wrong-layer / etc.).
2. **Tier decision** (same structure as Gap A):
   - **Tier 1 (one-off):** a genuine non-recurring slip — no guide entry; note it in the Bug Report as a Tier-1 code finding so it's accounted for, not silently dropped.
   - **Tier 2 (project lesson):** a fault-class, gotcha, or architectural trap this codebase is prone to → append a Project Lesson to `docs/context/Implementation_Patterns.md` (`## Project Lessons`: bug-class / gotcha / architecture), traced to the fix commit / bug ID. The next Phase 3 implementer (and the next bugfix) reads it before writing code.
   - **Tier 3 (universal anti-pattern change):** the fault-class is generalisable beyond this project → propose a new I-series entry to the user with the exact patch. DO NOT silently edit kit-level files.

Commit `docs/context/Implementation_Patterns.md` (if Tier 2) alongside the code fix when small, or as a separate `docs(impl-retro): {bug-id} ({fault-class})` commit when substantial.

#### Gap D — Context Gap Check (lightweight, kit v5.19+)

**One question, not a retro:** *was a missing or unfindable context doc a contributing cause?* — the bug exists because the implementing agent didn't know how a subsystem works (an undocumented contract, invariant, or cross-runtime asymmetry no `docs/context/` file owns), or you had to re-derive that knowledge to fix it.

- **Yes** → append a one-line `[UNCHECKED]` finding to `~/.claude/context-fit-findings.md` under this project's section, naming the homeless area and this bug ID. The full footprint sweep + Necessity Gauntlet runs at the next epic close (`feature-lifecycle.md` Phase 5.1 Gap D) or ad-hoc via the `context-fit` skill (`.claude/skills/context-fit/`) — don't run it inline here.
- **No** (the knowledge existed and was findable; the bug was a plain code/test slip) → skip with a one-line reason. Don't manufacture a context gap for every bug.

Report: what changed, what was fixed, what test gaps were closed (Gap A — code-level via `test-suite-retro.md`, including miss log entry and any guide update), what testing-agent gaps were closed (Gap B — browser-level via `testing-retro.md`), what code lesson was captured (Gap C — `Implementation_Patterns.md` entry or Tier-1 note), whether a context gap was flagged (Gap D — findings-channel line or skip reason), any manual verification needed.
