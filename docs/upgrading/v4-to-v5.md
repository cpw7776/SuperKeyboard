# Migration: AI Dev Workflow Kit v4 → v5

> **When to use this:** You have a project already running an **adapted** v4 copy of the kit — your context files are filled in, your `.claude/agents/` sub-agents have your stack/credentials baked in, `docs/testing-agents/` has real test plans with Miss Logs, `docs/context/Unit_Test_Writing_Guide.md` has project lessons, and `docs/test-suite-misses.md` has historical misses. You want v5's **Code Quality Pre-Testing Gate** without losing any of that.

> **When NOT to use this:** You're starting a brand-new project — drop the v5 kit in fresh, see `docs/README.md` "Quick Setup". OR you're on v3 or earlier — run the relevant migrations in order first (`v2-to-v3.md`, then `v3-to-v4.md`, then this one).

> **Philosophy:** v5 is **small and surgical**. No new files. No bootstrapping. Just two prompts to merge and one sub-agent to update with care because it has filled-in `[CUSTOMIZE]` content. Risk surface is small — biggest hazard is preserving `[CUSTOMIZE]` content in `code-quality-agent.md` while applying the dual-mode structural changes.

> **Model:** Use **claude-opus-4-7** for the merge reasoning.

> **Stop points:** Two — after the State Audit (Phase 1), so you can sanity-check what's about to change; and after the surgical merges (Phase 3), before the final commit lands.

**Inputs needed:**
- **PROJECT_ROOT:** path to the project you're upgrading (e.g., `~/Code/coffeescribe`)
- **NEW_KIT_ROOT:** path to the v5 kit you've placed beside your project (e.g., `~/Code/coffeescribe/_kit-v5-incoming`)

Both folders must be reachable. The migration agent never touches anything outside `PROJECT_ROOT`.

---

## Phase 1: State Audit (READ-ONLY)

**Goal:** Confirm we're upgrading from v4 (not v3 or v5) and inventory anything that needs surgical handling.

### 1.1 Detect current kit version

```
1. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md — first line.
   Expected: "# Feature Lifecycle (v4)"
   - If "v5" or higher: STOP. Already migrated.
   - If "v3" or lower: STOP. Run docs/upgrading/v3-to-v4.md first.
2. Read PROJECT_ROOT/.claude/agents/code-quality-agent.md — first heading line. Note version.
   Expected: "# Code Quality Sub-Agent" (unversioned v1) — v5 will bump to "(v2)".
   If it already says "(v2)" with dual-mode content, the agent is already migrated — proceed to feature-lifecycle.md alone.
3. Confirm v4 artefacts are present:
   - docs/prompts/test-suite-retro.md
   - docs/context/Unit_Test_Writing_Guide.md
   - docs/test-suite-misses.md
   If any is missing, the project is not actually on v4 — STOP and clarify with the user.
```

### 1.2 Detect hand-edits to v4 prompts and the code-quality-agent

`feature-lifecycle.md` is a universal prompt meant to be used as-is, but some projects hand-edit it. `code-quality-agent.md` has `[CUSTOMIZE]` markers that the project has filled in (test process name, test command, build command, debug-statement grep pattern for the language). Detect both:

```
For feature-lifecycle.md:
  cd PROJECT_ROOT && git log --oneline --follow -- docs/prompts/feature-lifecycle.md
  If commits beyond the v3-to-v4 / kit-initial commit exist:
    → Hand-edited. MERGE required. Save as feature-lifecycle.md.v4-local before overwriting.
  Else:
    → Safe to OVERWRITE with v5.

For code-quality-agent.md:
  Read PROJECT_ROOT/.claude/agents/code-quality-agent.md
  Grep for [CUSTOMIZE] markers and capture filled-in content under each — specifically:
    - Front-matter customisations (rare — usually none)
    - Pre-Flight block: [CUSTOMIZE] TEST_PROCESS_NAME pkill targets
    - Item 5.6 Debug Cleanup: [CUSTOMIZE] language-specific debug grep pattern
    - Item 5.9 Tests: [CUSTOMIZE] TEST_COMMAND
    - Teardown 5.11 block: [CUSTOMIZE] same TEST_PROCESS_NAME pkill targets
  Capture all of these as CUSTOMIZATIONS — they MUST survive the merge in Phase 3.2.
```

Build an **EDITS_FOUND** list capturing the hand-edits and the **CUSTOMIZATIONS** list capturing the `[CUSTOMIZE]` content.

### 1.3 Confirm CLAUDE.md state

```
1. Read PROJECT_ROOT/CLAUDE.md.
2. Identify the section originally copied from CLAUDE_SNIPPET.md.
3. The v5 changes to CLAUDE_SNIPPET split the code-quality-agent row into two rows
   (Pre-Testing Mode + Post-Manual-Testing Mode). Note whether the user's CLAUDE.md
   has the original single row.
```

---

## ⏸️ STOP: Present State Audit Summary

Show the user:

```markdown
## State Audit — {PROJECT_ROOT}

### Current versions
- feature-lifecycle.md: {v4 confirmed}
- code-quality-agent.md: {v1 / unversioned — will become v2}

### v4 artefacts present
- docs/prompts/test-suite-retro.md: {y/n}
- docs/context/Unit_Test_Writing_Guide.md: {y/n}
- docs/test-suite-misses.md: {y/n}

### Edit status
- feature-lifecycle.md: {unedited / hand-edited with N commits beyond v4 baseline}
- code-quality-agent.md: {N [CUSTOMIZE] sections with filled-in content}

### Migration plan
- feature-lifecycle.md: {overwrite | three-way merge with .v4-local backup}
- code-quality-agent.md: surgical 9-edit merge preserving {N} [CUSTOMIZE] sections
- CLAUDE.md: update the sub-agents table to split the code-quality-agent row
- No new files, no bootstrapping
```

**Wait for user approval before proceeding to Phase 2.**

---

## Phase 2: Safe Copies (AUTONOMOUS after approval)

### 2.1 Overwrite or stash feature-lifecycle.md

If unedited:
```
Copy NEW_KIT_ROOT/docs/prompts/feature-lifecycle.md → PROJECT_ROOT/docs/prompts/feature-lifecycle.md
```

If hand-edited:
```
Copy PROJECT_ROOT/docs/prompts/feature-lifecycle.md → PROJECT_ROOT/docs/prompts/feature-lifecycle.md.v4-local
Copy NEW_KIT_ROOT/docs/prompts/feature-lifecycle.md → PROJECT_ROOT/docs/prompts/feature-lifecycle.md
```

### 2.2 Commit the safe copy

```
cd PROJECT_ROOT
git add docs/prompts/feature-lifecycle.md docs/prompts/feature-lifecycle.md.v4-local 2>/dev/null
git commit -m "chore(kit): copy v5 feature-lifecycle + stash v4 hand-edits if any

Phase 2 of kit v4→v5 migration. The code-quality-agent surgical merge runs in Phase 3."
```

---

## Phase 3: Surgical Merge of `code-quality-agent.md` (AUTONOMOUS)

### 3.1 The 9 v5 edits to apply

v5 converts the code-quality-agent into a dual-mode agent. The project's copy has `[CUSTOMIZE]` content for `TEST_PROCESS_NAME`, debug-statement grep patterns, `TEST_COMMAND`, and `BUILD_COMMAND` — all inside items 5.6, 5.9, 5.10, and the pre-flight/teardown blocks. v5's structural changes do NOT touch those filled-in lines.

Apply each v5 change BY HAND to the project's `.claude/agents/code-quality-agent.md`. Read NEW_KIT_ROOT/.claude/agents/code-quality-agent.md for the full v5 text of each change.

**Edit A — Front-matter `description:`** Replace the single-line `description:` value with v5's longer dual-mode description.

**Edit B — Heading + Purpose block:** Replace the opening section (from `# Code Quality Sub-Agent` through the `[CUSTOMIZE]` template note) with v5's version. Includes the new heading `(v2)`, the dual-mode Modes blockquote, and the "Why two passes?" paragraph.

**Edit C — Role section:** Replace the existing single-paragraph `## Role` section with v5's two-paragraph version (Pre-Testing Mode behavior + Post-Manual-Testing Mode behavior).

**Edit D — Input Contract:** Insert the new `Mode` field as item 1 of the input contract list; renumber the existing items 1–3 to 2–4.

**Edit E — Core Principle section:** Replace the bulleted list with v5's version that adds `(post-test only)` markers to items 3–5 and the new "Mode-shaping rule" paragraph at the end.

**Edit F — Execution Sequence heading:** Replace the heading line with v5's version, and add the "Pre-test mode runs items 5.1 → 5.5..." paragraph below it.

**Edit G — Section headings 5.6 → 5.11:** Append the mode-only markers to each heading:
- 5.6 → `### 5.6 — Debug Cleanup (post-test mode only — skip in pre-test)`
- 5.7 → `### 5.7 — Dead-Code Sweep (post-test mode only — skip in pre-test)`
- 5.8 → `### 5.8 — /vulnerability-scanner (SECOND PASS) — MANDATORY FINAL SECURITY CHECK (post-test mode only)`
- 5.9 → `### 5.9 — Tests (Changed Files Only) (post-test mode only — Phase 4.2's FULL SUITE GATE covers pre-test)`
- 5.10 → `### 5.10 — Build (post-test mode only — Phase 4.2 covers pre-test)`
- 5.11 → `### 5.11 — Process Sweep (Teardown — MANDATORY in BOTH modes, every run)`

The CONTENT inside each section (including all `[CUSTOMIZE]` filled-in commands) stays as-is.

**Edit H — Reporting Format section:** Replace the single block with v5's dual-mode reporting format (Pre-Test Mode 6-item block + Post-Manual-Testing Mode 11-item block).

**Edit I — Rule 8:** Replace the existing rule 8 with v5's version that mentions both gate formats.

After applying A–I, verify every `[CUSTOMIZE]` filled-in line inside items 5.6, 5.9, 5.10, the pre-flight block, and the teardown block is still present and untouched.

### 3.2 (Conditional) Three-way merge feature-lifecycle.md.v4-local

If a `.v4-local` was stashed in Phase 2.1:

```
1. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md           # v5 (now in place)
2. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md.v4-local  # project hand-edits on top of v4
3. Identify what the project added/changed on top of stock v4 (compare against v4 from NEW_KIT_ROOT/../v4-canonical if available; otherwise read v4 text from the project's git history).
4. Re-apply those project edits on top of v5. The biggest risk area is Phase 3.5 — if the project hand-edited Phase 3 / Phase 4, make sure those edits coexist with the new Phase 3.5 section rather than overwriting it.
5. Delete feature-lifecycle.md.v4-local once the merge is verified.
```

---

## ⏸️ STOP: Show diff before commit

Show the user a `git diff` of `code-quality-agent.md` and (if applicable) `feature-lifecycle.md`. Ask them to confirm:

1. All `[CUSTOMIZE]` filled-in content survived?
2. The dual-mode reporting block is present?
3. The mode markers on items 5.6 → 5.11 are present?
4. (If applicable) feature-lifecycle hand-edits coexist with Phase 3.5?

Wait for approval before Phase 4.

---

## Phase 4: CLAUDE.md and Final Cleanup (AUTONOMOUS after approval)

### 4.1 Update CLAUDE.md

In PROJECT_ROOT/CLAUDE.md, update the sub-agents table to split the code-quality-agent row into two rows (Pre-Testing Mode + Post-Manual-Testing Mode). Copy the v5 rows from NEW_KIT_ROOT/docs/CLAUDE_SNIPPET.md.

Leave all `[CUSTOMIZE]` sections (Commands, Conventions, Skills) untouched.

### 4.2 Verification sweep

```
1. head -1 docs/prompts/feature-lifecycle.md     # must show "# Feature Lifecycle (v5)"
2. head -10 .claude/agents/code-quality-agent.md | grep "(v2)"  # must match
3. Grep for "Phase 3.5" in docs/prompts/feature-lifecycle.md — must find at least one match
4. Grep for "CODE QUALITY GATE (Pre-Test)" in .claude/agents/code-quality-agent.md — must find the reporting block
5. Confirm every [CUSTOMIZE] filled-in line from the State Audit's CUSTOMIZATIONS list still exists in the file
6. Confirm no .v4-local files remain (Phase 3.2 should have deleted)
```

### 4.3 Final commit

```
cd PROJECT_ROOT
git add -A
git commit -m "chore(kit): complete v4→v5 migration

- feature-lifecycle.md → v5 (adds Phase 3.5 — Pre-Testing Code Quality Gate)
- code-quality-agent.md → v2 (dual-mode: Pre-Testing + Post-Manual-Testing)
- CLAUDE.md sub-agents table updated for dual invocation
- All [CUSTOMIZE] content preserved

Kit is now v5. Phase 4 of feature-lifecycle.md cannot start until the
Phase 3.5 Pre-Test gate prints clean. The Phase 5.5 full gate continues
unchanged as Gate #2 in the final four-gate check."
```

### 4.4 Smoke test (recommended)

Pick a recently-completed feature and dry-run Phase 3.5 against its branch:

```
1. Check out the feature branch.
2. Invoke .claude/agents/code-quality-agent.md with Mode=pre-test, the feature name, and the changed-file list.
3. Confirm the agent runs items 1–5, skips items 6–10, runs teardown, and prints the 6-line CODE QUALITY GATE (Pre-Test): block.
4. If anything errors, the merge produced a state v5 doesn't like — diagnose with the user.
```

Optional but cheap. Catches edits that the verification sweep doesn't.

---

## Rollback

If something goes wrong:

- After Phase 2 commit: `git reset --hard HEAD~1` rolls back the safe copy.
- After Phase 4 commit: `git revert` the migration commits in reverse order — safer than `reset --hard`.

---

## Quality Checklist

- [ ] State audit reviewed and approved
- [ ] feature-lifecycle.md at v5
- [ ] code-quality-agent.md at v2 with dual-mode content
- [ ] All `[CUSTOMIZE]` content in code-quality-agent.md preserved
- [ ] (If applicable) feature-lifecycle.md hand-edits three-way-merged onto v5
- [ ] CLAUDE.md sub-agents table updated for dual invocation
- [ ] No `.v4-local` files remain
- [ ] Verification sweep passes
- [ ] (Optional) Smoke test passes
- [ ] Migration commits in git history
