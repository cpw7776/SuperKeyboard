# Migration: AI Dev Workflow Kit v1 → v2

> **When to use this:** You have a project running an **adapted** v1 copy of the kit — `feature-lifecycle.md` is at v1 (no Phase 0, no Phase Commit Discipline, three gates not four), the four sub-agents exist with project-specific `[CUSTOMIZE]` content filled in, and your context files are filled out. You want to upgrade to v2 without losing customizations or hand-edits.

> **When NOT to use this:** You're starting a brand-new project — drop the latest kit in fresh (see `docs/README.md` Quick Setup). Or your project is already at v2 or higher — run the relevant later migration instead (`v2-to-v3.md`, `v3-to-v4.md`, …) or invoke `docs/prompts/upgrade-kit.md` which sequences all of them.

> **Philosophy:** v2 is a major release — it restructures Phase 5, adds Phase 0 and Phase Commit Discipline as new top-level sections, upgrades Plan Mode to multi-option-tradeoff format, promotes test-suite proof to a verbatim gate, and moves from three gates to four. The migration is therefore a **structured rewrite** of `feature-lifecycle.md` plus a small side-file addition. The four sub-agents and the other prompts are **not modified by v2** — they remain on v1 shapes until later migrations touch them.

> **Model:** Use **claude-opus-4-7** or your highest-capability model. The lifecycle rewrite needs careful reasoning to keep the project's hand-edits intact.

> **Stop points:** Two — after Phase 1 (State Audit), so the user can sanity-check the plan before any file is changed; and after Phase 3 (mechanical edits), to review the resulting `feature-lifecycle.md` before the cleanup commit.

**Inputs needed:**
- **PROJECT_ROOT:** path to the project you're upgrading
- **NEW_KIT_ROOT:** path to the new kit (where this prompt lives), e.g. `/path/to/ai-dev-workflow-kit`

Both folders must be reachable. The migration agent never touches anything outside `PROJECT_ROOT`.

---

## Phase 1: State Audit (READ-ONLY)

**Goal:** Build a clear picture of what's currently in the project before changing anything. Confirm the project is genuinely at v1 (not v0 or pre-kit), and surface any hand-edits the migration would risk clobbering.

### 1.1 Confirm v1 baseline

```
1. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md
   - Confirm it does NOT contain the literal string "## PHASE 0:" → if it does, project is already ≥ v2. STOP.
   - Confirm the file has Phase 1 through Phase 5 headings → v1 baseline shape.
   - Note the first commit hash that introduced the file.
2. Read PROJECT_ROOT/docs/prompts/bugfix.md → confirm exists. v2 does not modify it but it should be present.
3. Read PROJECT_ROOT/docs/prompts/create-testing-agent.md → confirm exists. v2 does not modify it.
4. Read PROJECT_ROOT/docs/prompts/debug.md → confirm exists.
5. Confirm PROJECT_ROOT/docs/KIT_VERSION does NOT exist (v5.3+ marker; if present, the project isn't actually at v1).
```

If any of these checks fail, STOP and report — the project's shape doesn't match v1 and this migration could damage it.

### 1.2 Detect hand-edits to feature-lifecycle.md

v2 is going to do a structured rewrite of `feature-lifecycle.md`. If the project has hand-edited the file beyond the original drop-in, a wholesale rewrite would clobber those edits.

```
1. Run: git log --oneline --follow PROJECT_ROOT/docs/prompts/feature-lifecycle.md
2. Count the commits touching the file.
   - 1 commit (the drop-in) → file is clean, mechanical rewrite is safe.
   - >1 commit → file has been hand-edited. Save current as `feature-lifecycle.v1-local.md` for reference, and proceed with extra care at Phase 3 (each Edit must check for hand-edit conflicts).
3. If a commit message says something like "customise feature-lifecycle for our project" or similar, READ that diff before proceeding. Project-specific hand-edits MUST be preserved.
```

### 1.3 Detect customizations in `.claude/agents/` files

v2 does NOT modify the four sub-agents — they stay on v1 shapes through this migration. But capture customizations now so you understand the project's shape:

```
For each of:
  - PROJECT_ROOT/.claude/agents/testing-agent.md
  - PROJECT_ROOT/.claude/agents/code-quality-agent.md
  - PROJECT_ROOT/.claude/agents/context-docs-agent.md
  - PROJECT_ROOT/.claude/agents/docs-auditor-agent.md

Grep for [CUSTOMIZE] markers and adjacent project-specific content.
Note any non-[CUSTOMIZE] hand-edits (sections inserted, blockquotes added, gate format extensions).
```

These customizations are NOT touched by v2 but will matter for later migrations (v2→v3, etc).

### 1.4 Detect filled-in context files

```
List PROJECT_ROOT/docs/context/ — note which template files have been filled in:
  - Context_Index_File.md
  - Project_PDR.md
  - API_REFERENCE.md
  - database_reference_guide.md
  - PRODUCTION_READY.md
  - Project_Authentication.md
  - CHANGELOG.md
```

These are not modified by v2. Confirmed-present-and-filled is sufficient context for the audit.

### 1.5 Surface a State Audit report

Report to the user with:
- Detected version: v1 (confirmed by absence of Phase 0 / KIT_VERSION)
- Files this migration WILL modify: `docs/prompts/feature-lifecycle.md` (rewrite); add `docs/known-test-failures.md` + `docs/known-test-skips.md` (new empty placeholders).
- Files this migration will NOT touch: all four sub-agents, all other prompts, all context files, all project content (PRDs, ADRs, plans, etc.)
- Hand-edit risk: low / medium / high based on §1.2.
- Customization inventory: per-agent summary from §1.3.

**STOP** here and wait for user approval before proceeding to Phase 2.

---

## Phase 2: Drop v2 reference files alongside (safe copies)

**Goal:** Bring the v2 reference content into the project workspace without overwriting anything yet, so Phase 3 can reference both sides.

```
1. cp NEW_KIT_ROOT/docs/prompts/feature-lifecycle.md PROJECT_ROOT/docs/prompts/feature-lifecycle.v2-incoming.md
   (Note: in the current kit this file is at the latest version, not v2. For mechanical edits we want the v2 text. If the new kit has a docs/upgrading/v1-to-v2.md sibling reference file, use that. Otherwise the migration agent must apply the §3 edits using v2's structural intent — adding the named sections — rather than verbatim copying.)
2. Do NOT delete the existing feature-lifecycle.md yet — Phase 3 edits it in place.
```

If `feature-lifecycle.v2-incoming.md` already exists from a prior aborted run, delete it first.

---

## Phase 3: Mechanical edits to feature-lifecycle.md

Apply in order. Each edit has a precise anchor. After every Edit, re-read the file to confirm the change applied to the expected line and didn't shift adjacent content.

If §1.2 detected hand-edits AND an edit's anchor conflicts with a hand-edit, STOP and escalate — manual three-way merge is required for that specific change.

### Edit 1 — Engineering Principles preamble (NEW top-level section)

**Anchor:** the line immediately before `## PHASE 1: Kickoff & Requirements` (or whatever Phase 1 heading the file uses).

**Insert above the anchor:**

```markdown
## Engineering Principles (apply throughout — strongest in Plan Mode)

- **DRY** — flag duplication aggressively; reuse over re-implementation.
- **Well-tested code is mandatory** — better too many tests than too few.
- **Engineered enough** — not fragile or hacky, not over-engineered.
- **Correctness over speed of implementation** — handle edge cases first.
- **Explicit over clever** — clarity beats compactness.
- **Opinionated, not neutral** — when presenting options, recommend one and say why.

---
```

### Edit 2 — Phase 0 (Execution Mode Choice) — NEW section above Phase 1

**Anchor:** same as Edit 1 — the `## PHASE 1:` heading.

**Insert above the anchor:**

```markdown
## PHASE 0: Execution Mode Choice (INTERACTIVE — one question, no deliberation)

**Before Phase 1, ask the user how to run this feature.** Use `AskUserQuestion`.

> **"How do you want to run this feature?"**
>
> 1. **Single-chat mode (default)** — All phases run in this chat with the main agent. Best for small/medium features that fit comfortably in one context window.
>
> 2. **Orchestrator mode** — The main agent acts as a conductor. Phases 2, 3, and the automated portion of Phase 4 run in **sequential sub-agents** with isolated context. Phase 5 reconciliation uses **git diffs per phase commit** as the source of truth for what was actually built. Best for long or multi-phase epics where the manual-test → fix → re-test loop tends to bloat context.

**Rules for orchestrator mode:**
- **Sequential only.** Never run phase sub-agents in parallel — they'd race on the same files.
- **Phase 1 always runs in main** (interactive).
- **Phase 4 STOP (manual testing) and Phase 5 always run in main** (Phase 5 needs the orchestrator's full picture).
- **Manual-test fix loop in orchestrator mode:** each fix is dispatched as a focused fix sub-agent (specific bug + failing test path + scope), commits with `fix(<feature-id>): <bug-summary>`, returns.

Track the chosen mode and apply the **mode-specific behavior** subsection in each phase below.

---
```

### Edit 3 — Phase Commit Discipline (NEW top-level section)

**Anchor:** same as Edits 1 and 2 — the `## PHASE 1:` heading.

**Insert above the anchor:**

```markdown
## Phase Commit Discipline (BOTH MODES — MANDATORY)

**Every phase ends with at least one commit. Commit messages must be detailed enough to drive Phase 5 git-diff reconciliation without re-reading the chat.**

| Phase | Commit message format | Body must include |
|-------|----------------------|-------------------|
| Phase 2 (end) | `docs(<feature-id>): phase 2 — planning artifacts` | Paths to PRD, ADR, plan snapshot. Architecture decisions made. |
| Phase 3 (incremental) | `feat(<feature-id>): <prd-task-id> <short summary>` | What changed, which PRD task it implements, any deviation. |
| Phase 3 (end) | `feat(<feature-id>): phase 3 — implementation complete` | Summary, tasks skipped, tradeoff decisions that flipped. |
| Phase 4 (automated) | `fix(<feature-id>): phase 4 — automated test fixes` (only if fixes were needed) | Tests that failed, root cause, fix applied. |
| Phase 4 (manual fix loop) | `fix(<feature-id>): <bug-summary>` (one per fix) | Bug, failing-test reproduction, root cause, scope. |
| Phase 5 (end) | `docs(<feature-id>): phase 5 — reconciliation + retrospective` | Plan-vs-reality summary, confirmation that all FOUR gates passed. |

**Body is mandatory** for Phase 2-end, Phase 3-end, and Phase 5-end. **Reference PRD task IDs.** **Flag deviations explicitly** with `DEVIATION:`.

---
```

### Edit 4 — Replace Plan Mode (Phase 2.1) with multi-option tradeoff format

**Anchor:** the existing `### 2.1 Enter Plan Mode` heading (or equivalent — search for "Plan Mode" in Phase 2).

**Replace** the entire 2.1 section (from `### 2.1` through the next `### 2.2` heading) with:

```markdown
### 2.1 Enter Plan Mode

**Immediately after scope approval, enter plan mode (`/plan`).** This is the fast alignment gate before you invest time writing documents.

**In plan mode, produce — in this exact structure:**

#### 1. High-level task breakdown
Major implementation steps in execution order (DB migration → API routes → components → tests → docs).

#### 2. Architecture decisions — multi-option tradeoff format
For each non-trivial architectural decision (data model shape, integration boundary, state-management approach, third-party choice, auth pattern, etc.), produce this block:

> **Decision:** [name]
>
> **Why it matters:** [1–2 sentences on the consequences of getting this wrong]
>
> **Options:**
> - **Option A — [name]:** [description]
>   - Effort: [low/med/high — why]
>   - Risk: [low/med/high — what could go wrong]
>   - Impact: [low/med/high — what improves]
>   - Maintenance cost: [low/med/high — long-term burden]
> - **Option B — [name]:** [description]
>   - Effort / Risk / Impact / Maintenance: ...
> - **Option C — Do nothing / defer:** [if reasonable]
>
> **Recommendation:** [Option X] — [reasoning, opinionated, not neutral]

#### 3. Performance flags (planning-time)
Flag these BEFORE writing code:
- **N+1 / loop-in-query risks**
- **Expensive I/O** (cross-region, large payloads, sync that could be async)
- **Memory hotspots** (large in-memory collections, unbounded caches)
- **Caching opportunities**
- **Latency budgets**

#### 4. Risk flags
Anything uncertain, complex, or likely to change during implementation.

#### 5. Estimated scope
Rough numbers: files touched, new files, tests needed, migrations.

Present this plan for alignment. **Once approved, exit plan mode to begin writing documents.**
```

### Edit 5 — Replace Phase 4.2 with FULL SUITE GATE

**Anchor:** the existing `### 4.2 Automated Tests` heading (or equivalent).

**Replace** the entire 4.2 section with:

```markdown
### 4.2 Automated Tests — FULL SUITE GATE (mandatory)

This is the gate that targeted-files-only runs cannot replace. The whole suite runs, and proof gets pasted into the transcript.

1. **Pre-flight cleanup.** Kill lingering dev servers, MCP servers, test runners (`ps aux | grep -E "node|test-runner|chrom" | grep -v grep` and `pkill` what doesn't belong).

2. **Run `[TEST_COMMAND]` — full suite, no path argument** (no targeted file lists). Capture the **verbatim** final summary lines. Examples:
   - Vitest / Jest: `Test Files  X passed | Y skipped (Z)` and `Tests  A passed | B skipped | C todo (D)`
   - Pytest: `=== A passed, B skipped, C deselected, D warnings in T s ===`

   **Paste the verbatim summary lines into the transcript.** "All green" without these lines does NOT satisfy the gate.

3. **Run `[BUILD_COMMAND]`.** Confirm zero errors and exit code 0.

4. **Failure handling.** If any test fails: fix the code (not the test, unless the test logic is wrong), re-run, re-paste the summary lines. **Pre-existing failures are NOT an exemption** — either (a) fix them in a separate pre-flight commit, or (b) get explicit user approval to log them in `docs/known-test-failures.md` with reason + follow-up task.

5. **Skip-count drift handling.** If skipped count is higher than the baseline in `docs/known-test-skips.md`, document the new skips in that file with reason, or remove them.
```

### Edit 6 — Insert Phase 5.3 (Rewrite PRD/ADR/Architecture to Match Reality)

**Anchor:** the existing `### 5.3 Code Quality` heading (or equivalent — v1's 5.3 is typically Code Quality / Security).

**Insert BEFORE the anchor** (so the new 5.3 is the rewrite step, and v1's old 5.3 becomes the new 5.5):

```markdown
### 5.3 Rewrite PRD / ADR / Architecture to Match Reality

**The PRD, ADR, and Feature Architecture docs must be the source of truth for the feature — they must match the actual code, not the original plan.**

After manual testing and any resulting fixes, rewrite these docs:

- **PRD:** Rewrite task descriptions to reflect what was actually implemented. Mark completed items (`- [x]`). Note deviations from the original plan with reasoning.
- **ADR:** Update technical approach, data flow, and schema to match the actual implementation. Keep rejected options as historical record.
- **Feature Architecture file:** Rewrite any sections where implementation differs from original design.

**This is not box-ticking — it's a rewrite pass.** The plan doc preserves the journey; these docs preserve the destination.
```

### Edit 7 — Insert Phase 5.4 (Pre-merge Full-Suite Re-run)

**Anchor:** end of new Edit-6 content, before the (now-renumbered) Code Quality section.

**Insert:**

```markdown
### 5.4 Re-run the Full Test Suite (mandatory pre-merge)

**Even if Phase 4.2 was clean, re-run the full suite after:**
- Any post-manual-testing fix
- The PRD/ADR/Architecture rewrites in 5.3

Re-run `[TEST_COMMAND]` and `[BUILD_COMMAND]`. **Paste the verbatim summary lines into the transcript** — these become Gate #1 in 5.8. Full suite, no path arguments, no "tests pass" without proof.
```

### Edit 8 — Renumber v1's remaining 5.x sections

v1's old structure was typically: 5.1 Test Autopsy / 5.2 Plan Reconciliation / 5.3 Code Quality / 5.4 UI / 5.5 Docs / 5.6 Git.

After Edits 6+7, the structure becomes: 5.1 / 5.2 / **5.3 (NEW Rewrite)** / **5.4 (NEW Re-run)** / 5.5 (was 5.3) / 5.6 (was 5.4) / 5.7 (was 5.5) / 5.8 (NEW: four-gate check below) / 5.9 (was 5.6 Git).

For each section after the inserts, find its old number and rename to the new number. Cross-references (`See 5.3 for ...`) within the file must also be updated.

### Edit 9 — Replace gate-output rule (three gates → four gates)

**Anchor:** the existing section listing the gates (often near the end of Phase 5, sometimes called "Pre-Merge Checklist" or "Gate Output Required").

**Replace** with:

```markdown
### 5.8 Gate Output Required Rule — cannot proceed to git until all FOUR gates are pasted

Before Phase 5.9 (Git), the main transcript must contain, in order:
1. The verbatim **Test-Suite Summary lines** from the Phase 5.4 re-run.
2. The verbatim `CODE QUALITY GATE:` block from code-quality-agent.
3. The verbatim `CONTEXT DOCS GATE:` block from context-docs-agent.
4. The verbatim `DOCUMENTATION GATE (User-Facing):` block from docs-auditor-agent.

**Enforcement:** If any gate is missing, truncated, paraphrased, or shows an unresolved failure, Phase 5 is NOT complete. Re-invoke the relevant sub-agent or escalate to the user.

"I ran the checks and everything was fine" is NOT acceptable — the gate blocks are the proof.
```

### Edit 10 — Update Summary of Stop Points

**Anchor:** the existing "Summary of Stop Points" table (or equivalent).

**Update** to add Phase 0 as a row at the top (it's a one-click choice, not a deliberation stop, but should appear in the table for visibility). Other rows unchanged.

---

## Phase 4: Side-file additions

```
1. Create PROJECT_ROOT/docs/known-test-failures.md with header:
   # Known Test Failures (Baseline)
   This file logs intentionally-failing tests that should NOT block merges. Each entry must have a date, reason, and follow-up task reference.

2. Create PROJECT_ROOT/docs/known-test-skips.md with header:
   # Known Test Skips (Baseline)
   This file logs intentionally-skipped tests. Each entry must have a date, reason, and follow-up task reference.

3. Both files start empty (header only). Phase 4.2 (FULL SUITE GATE) references them when handling pre-existing failures and skip-count drift.
```

**STOP** here. Show the user the resulting `feature-lifecycle.md` diff and the two new empty files. Wait for approval before the cleanup commit.

---

## Phase 5: Cleanup and commit

```
1. Delete PROJECT_ROOT/docs/prompts/feature-lifecycle.v2-incoming.md (the scratch reference copy).
2. If §1.2 saved a feature-lifecycle.v1-local.md backup, keep it — the user may want it later. Don't delete without permission.
3. Commit:
   chore(kit): upgrade to v2

   Per docs/upgrading/v1-to-v2.md.
   Adds Phase 0 (execution mode), Engineering Principles preamble, Phase Commit Discipline,
   Plan Mode multi-option tradeoff format, Phase 4.2 FULL SUITE GATE, Phase 5.3 (PRD/ADR rewrite),
   Phase 5.4 (pre-merge full-suite re-run), four-gate output rule.
   Adds docs/known-test-failures.md and docs/known-test-skips.md (baseline files for the FULL SUITE GATE).
4. Report to user: upgrade complete. Recommend running v2-to-v3.md next if the goal is the current kit version.
```

---

## Failure modes and customization preservation

- **Hand-edited feature-lifecycle.md from §1.2.** If a hand-edit conflicts with one of the Edit anchors above, STOP at that Edit. Surface the conflict: show the user the hand-edit, show what v2 wants to insert/replace, ask how to reconcile (preserve hand-edit / overwrite with v2 / manual three-way merge).

- **Anchor not found.** If a `### N.M` heading isn't where v2 expects it (e.g., the project renamed sections), STOP. Don't guess — surface the missing anchor and ask the user where the equivalent section lives.

- **Sub-agent customizations.** v2 doesn't modify the sub-agents, so customizations there pass through untouched. They DO matter for later migrations (especially v3, which modifies testing-agent and code-quality-agent). Note them in the State Audit report for the next migration.
