# Reconcile an Ad-Hoc Change (Reverse Lifecycle)

> **Standalone — use anytime. Not part of the feature lifecycle, and it touches nothing in it.**

> **What this is:** The normal kit flow runs *forwards* — PRD → ADR → tests-first (TDD) → code → Phase 5 reconciliation. This prompt is for the times you worked *backwards*: you opened the project, noticed something while using the app, and just fixed it — a UI tweak, a few small things on one page, or a small feature — with **no PRD, no ADR, and no tests written first.** Now you want the project caught up: the tests TDD would have written, any architecture/PRD/ADR doc the change touches, and every relevant context and user-facing doc — without dragging a one-line fix through the full epic ceremony.

> **What it does, in order:** (1) figure out what you changed, (2) **backfill the tests** (reverse-TDD) **and capture any durable test/code lessons** (the lightweight mirror of Phase 5.1's Gap A / Gap B / Gap C retro), (3) update architecture / PRD / ADR **only if the change touches one**, (4) sync the context docs, (5) sync the user-facing docs (FAQ / Tour / Help Center / Legal), (6) gate + commit.

> **When NOT to use this:**
> - **It's actually an epic / sizeable feature** → run `docs/prompts/feature-lifecycle.md` forwards instead. This prompt is for work small enough that planning-first wasn't worth it.
> - **It's a bug that needs a root-cause hunt** → run `docs/prompts/bugfix.md` — that prompt does hypothesis ranking + a Test Autopsy. If you *already* fixed a small bug ad-hoc and just want the docs/tests caught up, this prompt is fine (the test-backfill step below still asks "why didn't a test catch this?").

> **Model:** Switch the chat to **`opus`** before running this. The reasoning lives in the test-backfill step (deciding what the tests *should* assert, not rubber-stamping the code) and the conditional architecture call. Once those are decided, the doc sync is structured execution but stays in the same chat.

> **Execution mode:** This prompt runs **inline, in this one chat** — it does **not** dispatch sub-agents. Where it reuses `.claude/agents/context-docs-agent.md` and `.claude/agents/docs-auditor-agent.md`, it reads them as **checklists and gate formats** and executes their steps here. (Reusing the agent files by reference — rather than copying their content — means any per-project customization you've made to those agents automatically applies to this prompt too. Skip their sub-agent scaffolding: the `SUB-AGENT RETURN` block, and the RAM-hygiene pre-flight/teardown **unless** a step actually drives a browser.)

---

## Source of truth: what changed, and why

This is the one place this prompt deliberately differs from `feature-lifecycle.md` Phase 5 (which forbids the transcript and mandates git, because by Phase 5 the work spanned many sub-agents and compaction).

- **WHAT changed → `git` is the ground truth.** Always. Drive the file-by-file work from the diff, never from memory of what you think you edited.
- **WHY it changed → this chat, *if the work was done here*.** When you fixed/added the thing in this same conversation, the intent is live and reliable — use it to decide what the tests should assert and what the docs should say. If this prompt is invoked in a **fresh chat** (you did the work in a terminal or a different session), there is no reliable "why" in context — fall back to git only and **ask the user for the intent** before writing tests or docs.

---

## Phase 1: Establish the change set (STOP — one confirmation)

**This is the only stop point.** Get the change set right before touching anything — everything downstream keys off it.

### 1.1 Inspect git

Run all of these and read the output:

```
git status --short                          # uncommitted working-tree changes
git diff                                     # unstaged content
git diff --staged                            # staged content
git log --oneline -10                        # recent commits (did you already commit the work?)
git diff main...HEAD --stat                  # if you branched for this, the whole branch footprint
```

The work you're reconciling is in **one** of these places — uncommitted in the working tree, in the last commit(s), or across a branch vs `main`. Identify which.

### 1.2 Build the change-set summary

From the diff (the **what**) plus this chat (the **why**, if available), assemble:

- **Files changed** — new / modified / deleted (from `git diff --name-status`).
- **What the change does** — one or two sentences, in intent terms ("added a clear-filters button to the transactions page", "fixed the date formatter dropping the timezone").
- **Surface signals** — note any of: new/changed **API routes**, **DB schema / migrations**, **auth/session/RLS** changes, **new pages/routes**, **new buttons/modals/panels**, **new tier-gated behaviour**, **third-party APIs touched**, **new user-data categories stored**. (These feed Phases 3–5; gathering them once here is cheaper than rediscovering them per phase.)
- **Existing docs it touches** — does it extend a feature that already has a `docs/prd/*.md`, `docs/ard/*.md`, or `docs/architecture/*.md`? Grep for the feature name to find out.

### 1.3 Confirm

Present the change-set summary and ask the user:

> "Here's what I'll reconcile: **[summary]**. This is the change set, correct? And should I also: write tests · update architecture/PRD/ADR · sync context docs · sync user-facing docs — or only some of those?"

Let the user trim scope here (a pure CSS tweak may legitimately want docs-only; a logic change should keep the tests). Default to **all four** unless told otherwise. Proceed once confirmed.

---

## Phase 2: Backfill the tests (reverse-TDD) — DEFAULT ON

You normally write the test first and watch it fail. Here the code already exists, so you're working backwards — but the goal is identical: a test that **fails if the behaviour is wrong**, not one that merely echoes whatever the code currently does.

**Read both guides first:**
- **`docs/context/Unit_Test_Writing_Guide.md`** (universal anti-patterns A1–A11 + this project's `## Project Lessons`) — the vocabulary for writing honest tests and the list of this codebase's recurring blind spots.
- **`docs/context/Implementation_Patterns.md`** (universal code anti-patterns I-series + `## Project Lessons`) — the code-side guide. It tells you which fault-classes this codebase is prone to, so when a backfilled test goes red and you fix the code, you fix it cleanly; and it's the vocabulary for the Gap C lesson capture below.

Then, for each new or changed unit of behaviour in the diff:

1. **Find the nearest existing tests.** If there are none for this code, that's a coverage gap (the normal case for ad-hoc work).
2. **Write the test against *intended* behaviour, not observed behaviour.** Assert what the change *should* do (from the chat intent / what the user described), so the test would catch a mistake in the code you just wrote. Do **not** snapshot the current output blindly — that bakes in any bug.
3. **Run the test.**
   - **Green** → the behaviour is right and now protected.
   - **Red** → you've found a real defect in the ad-hoc change. Surface it, fix the code, re-run to green. (This is the payoff of asserting intent rather than current behaviour.)
4. Follow the project's existing test patterns and the guide's anti-patterns — no over-mocking, no asserting the mock, no async no-ops.

**If the change has no unit-testable surface** (pure CSS / copy / layout, browser-observable only): say so explicitly — "no unit-testable surface; behaviour is browser-observable only" — and, if the project has a browser-test library (`docs/testing-agents/REGISTRY.md`), note which agent *should* own this surface and whether it needs a new scenario (you can adapt it via `docs/prompts/create-testing-agent.md`, or just flag it for later). Don't invent meaningless unit tests to fill the line.

**Run the relevant tests (and the build) once more at the end of this phase** so the rest of the reconciliation runs against green code.

### 2.1 Lessons learned (reverse-retro — lightweight)

This is the ad-hoc mirror of `feature-lifecycle.md` Phase 5.1: the same durable-lesson capture, scaled down to a single change. For the change you just reconciled — **especially if it was a bug fix, or a red backfilled test exposed a real defect** — ask the three gap questions and capture any *generalisable* lesson. Skip a gap with a one-line reason if it doesn't apply; don't manufacture lessons for a clean one-off.

- **Gap A — test lesson:** *Why didn't an existing test catch this?* If generalisable, append the lesson to `docs/context/Unit_Test_Writing_Guide.md` (`## Project Lessons`) — same Gap-A discipline as `bugfix.md`.
- **Gap B — browser-test lesson:** if the change is browser-observable and an existing testing-agent *should* have caught it, note the miss on that agent (and/or `docs/context/Testing_Patterns.md`) per `docs/prompts/testing-retro.md`, or flag "no owner" if none covers it.
- **Gap C — implementation/code lesson:** *Was the code an instance of a recurring fault-class, a project gotcha, or an architectural anti-pattern?* (Name the class with the I-series vocabulary — swallowed error, unvalidated boundary, drifted duplication, symptom-at-wrong-layer, etc.) If generalisable, append a Project Lesson to `docs/context/Implementation_Patterns.md` (bug-class / gotcha / architecture), traced to the change. The next implementer reads it before writing code.

A Tier-3 (skill-universal) lesson — generalisable beyond this project — is **proposed, not applied**: surface it for the user's approval rather than editing a universal guide section unilaterally.

---

## Phase 3: Architecture / PRD / ADR — CONDITIONAL

Ad-hoc work usually has no feature ID and no PRD. Don't manufacture ceremony. Decide per case:

- **Change extends a feature that already has docs** (`docs/prd/*.md`, `docs/ard/*.md`, `docs/architecture/*.md`) → **update them to match the new reality.** Read the diff, then rewrite the affected sections so the doc describes the code as it now is. Tick any newly-satisfied items. Note the change with the commit hash. This is the same "docs follow the code" rule as Phase 5.3 of the lifecycle.
- **Change alters how the system works architecturally** (a new data flow, a changed error path, a new integration, a moved component boundary) but has **no** existing doc → offer the user a **lightweight architecture note** (a short `docs/architecture/` entry or an ADR), not a full PRD. Create it only if they want it; otherwise log the decision.
- **Small, self-contained UI/copy/logic tweak that changes no architecture and touches no existing feature doc** → **skip, with a one-line reason.** This is the common and correct outcome for most ad-hoc work. Skipping is fine *here* (unlike the doc-sync phases below) as long as you state why.

Use the PRD/ADR/Architecture rewrite logic in `.claude/agents/docs-auditor-agent.md` §5.5 as the standard for *how* to rewrite when you do update — but the decision to update at all is yours to make and justify.

---

## Phase 4: Context docs sync — DEFAULT ON

Run the **Execution Sequence** of `.claude/agents/context-docs-agent.md` (its §5.1–5.8) **inline in this chat** against the change set from Phase 1. Read each target file, decide against the diff, edit where it applies. The rules carry over verbatim:

- **The changelog entry is mandatory every time** (`docs/context/CHANGELOG.md`, §5.4) — there is no valid "not applicable" on that line.
- For every other file, a skip must name the concrete diff signal you checked and why it doesn't apply — no vague skips.
- If the diff shows a DB / API / auth change that your Phase 1 surface-signals list missed, **stop and reconcile** — the diff is the authority.

**End this phase by printing the verbatim `CONTEXT DOCS GATE:` block** (the format in `context-docs-agent.md` §"Reporting Format"). Don't paraphrase it.

---

## Phase 5: User-facing docs sync — DEFAULT ON

Walk the **decision trees** in `.claude/agents/docs-auditor-agent.md` (§5.1 Help Center, §5.2 FAQ, §5.3 Onboarding Tour, §5.4 Legal) **inline in this chat** against the change set. Answer every question YES/NO **with evidence** — "default is CREATE/UPDATE, not skip"; a blanket "no update needed" without the per-question breakdown is a failure. For every YES, create or update the file. (Tour rule still holds: never add a tour step without also adding the attribute to the JSX — both or nothing.)

(§5.5 PRD/ADR/Architecture in that agent was already handled in Phase 3 above — don't double it.)

If a tree step needs a browser, run the agent's RAM-hygiene pre-flight and teardown around it; otherwise skip that scaffolding.

**End this phase by printing the verbatim `DOCUMENTATION GATE (User-Facing):` block** (the format in `docs-auditor-agent.md` §"Reporting Format"), with every decision-tree answer shown.

---

## Phase 6: Gate + commit

### 6.1 Reconcile gate

Print this composite gate into the chat. It must contain the two verbatim sub-gates from Phases 4 and 5 — no paraphrasing.

```
RECONCILE GATE:

Change set:    [one-line summary] — [N files, from working-tree / last-commit / branch]
Tests:         [Added N tests, all green / Found + fixed M defects / No unit surface — browser-observable, agent: {name}]
Lessons:       [Gap A: {guide entry added | n/a} · Gap B: {agent miss noted | n/a} · Gap C: {Implementation_Patterns entry added | n/a} · Tier-3 proposed: {list | none}]
Architecture:  [Updated {doc} / Lightweight note created / Skipped because {reason}]

<verbatim CONTEXT DOCS GATE block from Phase 4>

<verbatim DOCUMENTATION GATE (User-Facing) block from Phase 5>
```

If any line shows an unresolved failure (a red test you couldn't fix, a missing gate block, a YES with no corresponding edit), reconciliation is **not** complete — resolve it or escalate to the user. Don't commit on a broken gate.

### 6.2 Commit

- If the ad-hoc code change was still uncommitted, commit it together with the test + doc updates; if it was already committed, commit the reconciliation as a follow-up.
- Suggested message: `chore(reconcile): backfill tests + docs for <short change description>` with a body listing what was reconciled (tests added, architecture/PRD/ADR touched or skipped-with-reason, context + user-facing docs updated) and **confirmation that the RECONCILE GATE passed**.
- If you're on a branch and the work is done, offer to merge.

---

## Invoking this prompt

- **By reference (works in any project that has the kit):**
  > Read `docs/prompts/reconcile-change.md` and follow it against the change I just made.
- **As a slash command (optional, per project):** the kit ships no slash commands — but if you want `/reconcile`, drop a one-line wrapper in that project at `.claude/commands/reconcile.md` containing: *"Read `docs/prompts/reconcile-change.md` and follow it against the change I just made."* (This is a project-local convenience, not part of the kit's distributed surface.)
