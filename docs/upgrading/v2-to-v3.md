# Migration: AI Dev Workflow Kit v2 → v3

> **When to use this:** You have a project (e.g., CoffeeScribe) that's already running an **adapted** v2 copy of the kit — your context files are filled in, your sub-agents have your stack/credentials/auth model baked in, and you've been using `feature-lifecycle.md` for features and `bugfix.md` for bugs. You want to upgrade to v3 without losing customizations.

> **When NOT to use this:** You're starting a brand-new project. In that case, drop the entire v3 kit in fresh — see `docs/README.md` "Quick Setup". This migration prompt is for projects that already adapted the kit.

> **Philosophy:** Surgical merges over wholesale copies. Three categories of files: pure-universal (safe to overwrite), customized-with-`[CUSTOMIZE]`-markers (need surgical edits that preserve customizations), and project-state (existing test plans need bootstrapping into the new registry/miss-log world).

> **Model:** Use **claude-opus-4-7** or your highest-capability model. The merge logic needs careful reasoning.

> **Stop points:** Two — after the State Audit (Phase 1), so you can sanity-check what the migration is about to do; and after Bootstrapping (Phase 4), before the final cleanup commit.

**Inputs needed:**
- **PROJECT_ROOT:** path to the project you're upgrading (e.g., `~/Code/coffeescribe`)
- **NEW_KIT_ROOT:** path to the v3 kit you've dragged in (e.g., `~/Code/coffeescribe/_kit-v3-incoming` — a folder you placed beside your existing `docs/` and `.claude/`)

Both folders must be reachable. The migration agent never touches anything outside `PROJECT_ROOT`.

---

## Phase 1: State Audit (READ-ONLY)

**Goal:** Build a clear picture of what's currently in the project before changing anything.

### 1.1 Detect the current kit version

```
1. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md — first line. Note version: v?, v2, or unversioned.
2. Read PROJECT_ROOT/docs/prompts/bugfix.md — first line. Note version.
3. Read PROJECT_ROOT/docs/prompts/create-testing-agent.md — first line. Note version.
4. Read PROJECT_ROOT/.claude/agents/testing-agent.md — first heading line. Note version.
```

If feature-lifecycle.md is already v3, STOP — the project is already on v3 and doesn't need this migration. Tell the user.

### 1.2 Detect customizations in `.claude/agents/` files

v3 modifies TWO sub-agent files in places that overlap with `[CUSTOMIZE]` regions: `testing-agent.md` and `code-quality-agent.md`. Identify what's been filled in for each:

```
For each of:
  - PROJECT_ROOT/.claude/agents/testing-agent.md
  - PROJECT_ROOT/.claude/agents/code-quality-agent.md

Grep for [CUSTOMIZE] markers.
For each [CUSTOMIZE] section, capture:
  - The marker location (section heading)
  - The customized content the user has added below it
    (testing-agent: credential env vars, auth model, dev server URL
     code-quality-agent: TEST_PROCESS_NAME for pkill, TEST_COMMAND, BUILD_COMMAND, debug-statement grep pattern for the language)
Note these in a CUSTOMIZATIONS list — they MUST survive the merge.
```

The other two sub-agents (`context-docs-agent.md`, `docs-auditor-agent.md`) are NOT touched by v3 — leave them alone.

### 1.3 Detect hand-edits to "universal" prompts

The universal prompts (`feature-lifecycle.md`, `bugfix.md`, `create-testing-agent.md`, `debug.md`) are meant to be used as-is, but some projects hand-edit them. Detect this so the merge doesn't clobber edits:

```
For each of feature-lifecycle.md, bugfix.md, create-testing-agent.md, debug.md:

1. Run: cd PROJECT_ROOT && git log --oneline --follow -- docs/prompts/{file}
2. If the only commit touching the file is the initial kit drop-in commit, the file is unedited
   → Safe to OVERWRITE with v3.
3. If there are subsequent commits to the file, it's been hand-edited
   → MERGE required. Save the project's version as {file}.v2-local before overwriting,
   so the agent can three-way-merge the customizations back in after.

Build a list: { unedited: [...], hand_edited: [...] }
```

### 1.4 Detect existing testing-agent state

```
1. Does PROJECT_ROOT/docs/testing-agents/ exist? (probably yes if any features have shipped)
2. Does PROJECT_ROOT/docs/testing-agents/REGISTRY.md exist? (probably no — it's new in v3)
3. List all *.md files in docs/testing-agents/ EXCLUDING REGISTRY.md.
   For each:
     - Note its filename and feature name
     - Read the file and extract: Routes, Roles/Tiers, Async (y/n)
     - Detect if it already has a "## Miss Log" section (probably no — it's new in v3)
4. Capture this as the BOOTSTRAP_LIST — each entry needs a REGISTRY row + Miss Log section.
```

### 1.5 Detect existing `Testing_Patterns.md`

```
Does PROJECT_ROOT/docs/context/Testing_Patterns.md exist?
- If yes (rare on a v2 → v3 upgrade): keep its contents. v3's create-testing-agent.md will pick them up.
- If no: don't create it now. It's seeded on the first Tier-2 retro.
```

### 1.6 Detect CLAUDE.md state

```
1. Read PROJECT_ROOT/CLAUDE.md (or equivalent).
2. Identify the section that was originally copied from CLAUDE_SNIPPET.md.
3. Note any project-specific customizations alongside it (commands, conventions, skills).
```

---

## ⏸️ STOP: Present State Audit Summary

Present the user with a structured summary BEFORE making any changes:

```markdown
## State Audit — {PROJECT_ROOT}

### Current versions
- feature-lifecycle.md: {v? | v2 | unversioned}
- bugfix.md: {version}
- create-testing-agent.md: {version}
- .claude/agents/testing-agent.md: {version}

### Customizations in .claude/agents/testing-agent.md
{list of [CUSTOMIZE] sections + what's filled in — these MUST survive the merge}

### Universal prompt edit status
- Unedited (safe to overwrite): {list}
- Hand-edited (require merge): {list}

### Existing testing-agent state
- docs/testing-agents/ exists: {y/n}
- REGISTRY.md exists: {y/n}
- Existing test plans to bootstrap: {count + filenames}
- Existing Miss Logs in plans: {count}

### CLAUDE.md
- Customizations alongside the snippet content: {summary}

### Migration plan
- Files to copy fresh: testing-retro.md, REGISTRY.md (if missing), debug.md (if unedited), {other unedited universals}
- Files to surgically merge: .claude/agents/testing-agent.md (preserve {N} customizations)
- Files to three-way merge: {list of hand-edited universals}
- Bootstrap actions: {N} test plans → REGISTRY rows + Miss Log sections
- CLAUDE.md update: add 2 entries (testing-retro.md, REGISTRY.md)
```

**Wait for user approval before proceeding to Phase 2.** This is the last chance to back out without changes on disk.

---

## Phase 2: Safe Copies & Fresh Files (AUTONOMOUS after approval)

### 2.1 Copy brand-new v3 files (no merge possible — they don't exist in the project yet)

```
Copy NEW_KIT_ROOT/docs/prompts/testing-retro.md → PROJECT_ROOT/docs/prompts/testing-retro.md
If REGISTRY.md doesn't exist in PROJECT_ROOT:
  Copy NEW_KIT_ROOT/docs/testing-agents/REGISTRY.md → PROJECT_ROOT/docs/testing-agents/REGISTRY.md
  (clear the example row — Phase 4 will populate it from BOOTSTRAP_LIST)
```

### 2.2 Overwrite unedited universal prompts

For each file in the `unedited` list from 1.3:

```
Copy NEW_KIT_ROOT/docs/prompts/{file} → PROJECT_ROOT/docs/prompts/{file}
```

These are pure-universal — no customizations to preserve.

### 2.3 Stash hand-edited universal prompts for three-way merge

For each file in the `hand_edited` list from 1.3:

```
Copy PROJECT_ROOT/docs/prompts/{file} → PROJECT_ROOT/docs/prompts/{file}.v2-local
Copy NEW_KIT_ROOT/docs/prompts/{file} → PROJECT_ROOT/docs/prompts/{file}
```

Now there's a `{file}.v2-local` backup of the hand-edits. Phase 3.4 will reconcile.

### 2.4 Commit the safe copies

```
cd PROJECT_ROOT
git add docs/prompts/testing-retro.md docs/testing-agents/REGISTRY.md docs/prompts/feature-lifecycle.md docs/prompts/bugfix.md docs/prompts/create-testing-agent.md docs/prompts/debug.md docs/prompts/*.v2-local
git commit -m "chore(kit): copy v3 prompts + stash v2 hand-edits as .v2-local

Phase 2 of kit v2→v3 migration. Universal prompts replaced (no customizations).
Hand-edited prompts stashed as {file}.v2-local for three-way merge in Phase 3."
```

This commit is a checkpoint — if anything goes wrong later, you can roll back to here cleanly.

---

## Phase 3: Surgical Merge of Customized Files (AUTONOMOUS)

### 3.1 Merge `.claude/agents/testing-agent.md`

The project's copy has filled-in `[CUSTOMIZE]` sections; v3 adds non-customizable structural changes. Apply each v3 change BY HAND to the project's customized copy:

**Edit A — Header:** Replace the opening section (from `# Testing Sub-Agent` through the `[CUSTOMIZE]` template note) with the v3 version from NEW_KIT_ROOT. The header doesn't contain user customizations.

**Edit B — Pre-Flight step 1 insertion:** In the existing `### 1. Pre-Flight` block, insert the new step 1 ("Read the test plan file end-to-end, INCLUDING the Miss Log section…") at the top, and renumber the existing steps 1–6 to 2–7. The numbered steps retain any `[CUSTOMIZE]` lines the project has filled in (e.g., specific credential env vars).

**Edit C — Report header:** In the `## Reporting Format` section, add the `**Test Plan File:** \`docs/testing-agents/{feature-name}-tests.md\`...` line directly under `**Date:**` and above `**Roles/Tiers Tested:**`. Don't touch the surrounding lines.

**Edit D — Rule 18:** At the end of the `## Rules` section, append:

```
18. **Read the Miss Log every run.** It is the institutional memory of bugs this plan has missed before. A regression on a Miss Log entry is a Tier-0 failure — surface it loudly in the final report.
```

After applying A–D, verify every `[CUSTOMIZE]` section's filled-in content is still present and untouched.

### 3.2 Merge `.claude/agents/code-quality-agent.md`

v3 converts the code-quality-agent into a dual-mode agent (Pre-Testing Mode + Post-Manual-Testing Mode). The project's copy has `[CUSTOMIZE]` content for `TEST_PROCESS_NAME`, debug-statement grep patterns, `TEST_COMMAND`, and `BUILD_COMMAND` — all that content lives in items 5.6, 5.9, 5.10, and the pre-flight/teardown blocks. v3's structural changes do NOT touch those filled-in lines. Apply each v3 change BY HAND:

**Edit A — Front-matter `description:`** Replace the single-line `description:` value with v3's longer dual-mode description (read it from NEW_KIT_ROOT/.claude/agents/code-quality-agent.md).

**Edit B — Heading + Purpose block:** Replace the opening section (from `# Code Quality Sub-Agent` through the `[CUSTOMIZE]` template note) with v3's version. This includes the new heading `(v2)`, the new dual-mode Modes blockquote, and the "Why two passes?" paragraph.

**Edit C — Role section:** Replace the existing single-paragraph `## Role` section with v3's two-paragraph version (Pre-Testing Mode behavior + Post-Manual-Testing Mode behavior).

**Edit D — Input Contract:** Insert the new `Mode` field as item 1 of the input contract list; renumber the existing items 1–3 to 2–4.

**Edit E — Core Principle section:** Replace the bulleted list with v3's version that adds `(post-test only)` markers to items 3–5 and the new "Mode-shaping rule" paragraph at the end.

**Edit F — Execution Sequence heading:** Replace the heading line with v3's version, and add the "Pre-test mode runs items 5.1 → 5.5..." paragraph below it.

**Edit G — Section headings 5.6 → 5.11:** Append the mode-only markers to each heading:
- 5.6 → `### 5.6 — Debug Cleanup (post-test mode only — skip in pre-test)`
- 5.7 → `### 5.7 — Dead-Code Sweep (post-test mode only — skip in pre-test)`
- 5.8 → `### 5.8 — /vulnerability-scanner (SECOND PASS) — MANDATORY FINAL SECURITY CHECK (post-test mode only)`
- 5.9 → `### 5.9 — Tests (Changed Files Only) (post-test mode only — Phase 4.2's FULL SUITE GATE covers pre-test)`
- 5.10 → `### 5.10 — Build (post-test mode only — Phase 4.2 covers pre-test)`
- 5.11 → `### 5.11 — Process Sweep (Teardown — MANDATORY in BOTH modes, every run)`

The CONTENT inside each section (including all `[CUSTOMIZE]` filled-in commands) stays as-is.

**Edit H — Reporting Format section:** Replace the single block with v3's dual-mode reporting format (Pre-Test Mode 6-item block + Post-Manual-Testing Mode 11-item block). Read v3's full text from NEW_KIT_ROOT.

**Edit I — Rule 8:** Replace the existing rule 8 with v3's version that mentions both gate formats.

After applying A–I, verify every `[CUSTOMIZE]` filled-in line inside items 5.6, 5.9, 5.10, the pre-flight block, and the teardown block is still present and untouched.

### 3.3 (Untouched) other sub-agents

`context-docs-agent.md`, `docs-auditor-agent.md` had no v3 changes — leave them alone.

### 3.4 Three-way merge hand-edited universal prompts

For each file in the `hand_edited` list:

```
1. Read PROJECT_ROOT/docs/prompts/{file}             # the new v3 version (now in place)
2. Read PROJECT_ROOT/docs/prompts/{file}.v2-local    # the project's hand-edited v2 version
3. Compare. Identify what the project added or changed on top of v2.
4. Re-apply those project-specific edits on top of v3.
5. Confirm the v3 changes are preserved alongside the re-applied edits.
6. Delete PROJECT_ROOT/docs/prompts/{file}.v2-local once the merge is verified.
```

If the project's edits conflict semantically with a v3 change (rare — usually edits are in different sections), surface the conflict to the user with both sides shown and ask which to keep.

### 3.5 Commit the surgical merges

```
cd PROJECT_ROOT
git add .claude/agents/testing-agent.md .claude/agents/code-quality-agent.md docs/prompts/*.md
git commit -m "chore(kit): apply v3 changes to customized files

Phase 3 of kit v2→v3 migration:
- testing-agent.md: 4 surgical edits (header, pre-flight Miss Log read, Test Plan File in report, rule 18). All [CUSTOMIZE] content preserved.
- code-quality-agent.md: 9 surgical edits for dual-mode operation (front-matter, heading + Modes block, Role section, Input Contract mode field, Core Principle mode-shaping, Execution Sequence heading, mode markers on 5.6-5.11, dual reporting blocks, rule 8). All [CUSTOMIZE] content preserved.
- {hand_edited universals if any}: three-way merged v3 changes with project edits."
```

---

## Phase 4: Bootstrap Existing Testing Agents (AUTONOMOUS)

The v3 base agent expects every test plan to have a Miss Log section AND a row in REGISTRY.md. Existing test plans don't have either. Backfill both.

### 4.1 Backfill REGISTRY.md from existing test plans

For each test plan in BOOTSTRAP_LIST (from Phase 1.4):

```
1. Read the test plan file.
2. Extract from its Test Configuration / header:
   - feature: filename or declared feature name
   - routes: list from the file
   - roles_required: list from the file
   - viewports: infer from scenarios (look for "375", "mobile", "1440", etc.)
   - async: y if file mentions async operations / timeouts; n otherwise
3. Build a row:
   | `{filename}` | {one-line purpose distilled from the feature name} | {routes} | {roles} | {viewports} | {y/n} | {file mtime as YYYY-MM-DD} | {empty} |
4. Append to PROJECT_ROOT/docs/testing-agents/REGISTRY.md under the Registry table.
```

If two existing test plans overlap heavily in scope, surface this to the user — they may want to consolidate before going further. Don't auto-consolidate.

### 4.2 Append Miss Log section to each existing test plan

For each test plan in BOOTSTRAP_LIST:

```
1. Read the file.
2. If a `## Miss Log` section already exists (unlikely on v2 upgrade): skip.
3. Otherwise, append at the bottom of the file:

---

## Miss Log

> **Read this section on every run as part of pre-flight.** Each entry is a past bug this agent should have caught but didn't. Treat them as scenarios that must continue to pass — if any of them ever resurfaces, the agent's improvement regressed.

> **Populated by:** `docs/prompts/testing-retro.md` (the retro protocol). Never edit manually; always go through the retro so the categorization is consistent.

{No misses yet — this section is seeded empty for the v3 migration. Real entries appended here as retros happen — see testing-retro.md.}
```

### 4.3 Seed the Out-of-Scope Exemptions table (optional)

If the project has known areas where testing agents fundamentally can't observe (background jobs, webhooks with no UI, etc.), the user may already mentally exclude them. Surface this to the user as a question:

```
Are there areas in this project that testing agents cannot meaningfully observe?
Examples to prompt the user:
  - Stripe / billing webhooks
  - Background cron jobs
  - Email delivery confirmation
  - Internal telemetry
For each: add a row to REGISTRY.md's "Out-of-Scope Exemptions" table.
```

This step is optional — the table can be filled in as exemptions arise.

---

## ⏸️ STOP: Present Bootstrap Summary

Show the user the populated REGISTRY.md and the list of test plans that now have Miss Log sections. Ask: does the scope captured for each existing agent look right?

If the user spots a wrong scope or a missing column value, fix it now while context is fresh. Wait for approval before Phase 5.

---

## Phase 5: CLAUDE.md and Final Cleanup (AUTONOMOUS after approval)

### 5.1 Update CLAUDE.md

In PROJECT_ROOT/CLAUDE.md (or your equivalent), update the kit-snippet section:

```
1. In the Workflow Prompts table, add a row for testing-retro.md (copy the row from NEW_KIT_ROOT/docs/CLAUDE_SNIPPET.md).
2. In the Workflow Prompts table, update the bugfix.md and create-testing-agent.md row descriptions to match v3 wording.
3. In the Feature Documentation table, update the docs/testing-agents/ row to mention REGISTRY.md and Miss Log.
4. Leave all [CUSTOMIZE] sections (Commands, Conventions, Skills) untouched.
```

### 5.2 Run a verification sweep

```
1. Grep for all references to "testing-retro.md", "REGISTRY.md", "Testing_Patterns.md" in PROJECT_ROOT and confirm each resolves to an existing file (or is correctly framed as "if it exists" for Testing_Patterns.md).
2. Confirm no .v2-local files remain (Phase 3.4 should have deleted them).
3. Confirm testing-agent.md, feature-lifecycle.md, bugfix.md, create-testing-agent.md headers all show the correct v3-era version numbers.
4. Confirm every file in docs/testing-agents/ (except REGISTRY.md) has a `## Miss Log` section.
5. Confirm every file in docs/testing-agents/ (except REGISTRY.md) has a corresponding REGISTRY.md row.
```

If anything fails, fix it before the final commit.

### 5.3 Final commit

```
cd PROJECT_ROOT
git add -A
git commit -m "chore(kit): complete v2→v3 migration

Phase 4–5 of migration:
- REGISTRY.md populated with {N} bootstrapped rows
- {N} existing test plans gained Miss Log sections
- CLAUDE.md updated for v3 prompts table

Kit is now v3. The testing-agent library is registry-indexed; bugs found in
manual testing or production will run testing-retro.md (auto from bugfix.md
Phase 2.4 and feature-lifecycle.md Phase 5.1) and adapt the library."
```

### 5.4 Smoke test (recommended)

Before declaring the migration done, run a small smoke test to confirm the v3 flow works on this project:

```
1. Pick one existing testing agent.
2. Invoke .claude/agents/testing-agent.md as you normally would.
3. Confirm the agent reads the Miss Log (the v3 pre-flight step) without erroring.
4. Confirm the report block includes the "Test Plan File:" line.
5. If something's off, the migration produced a state the v3 agent doesn't like — diagnose with the user before continuing.
```

The smoke test is optional but cheap, and catches subtle merge errors that the verification sweep misses (e.g., a missing newline or a corrupted YAML block).

---

## Rollback

If something goes wrong:

- After Phase 2 commit: `git reset --hard HEAD~1` rolls back the safe copies, leaving project at v2.
- After Phase 3 commit: `git reset --hard HEAD~2` rolls back both safe copies and merges.
- After Phase 5 commit: `git revert` the migration commits in reverse order — safer than `reset --hard` once you've moved on.

Don't `reset --hard` once you've started using the v3 workflow on real features — by then the migration is part of your history.

---

## Quality Checklist

Before declaring the migration complete:

- [ ] Phase 1 State Audit reviewed and approved by user
- [ ] testing-retro.md and REGISTRY.md exist in PROJECT_ROOT (Phase 2.1)
- [ ] Universal prompts at correct v3 versions (Phase 2.2 + 3.3)
- [ ] `.claude/agents/testing-agent.md` has all 4 v3 edits AND all `[CUSTOMIZE]` content preserved (Phase 3.1)
- [ ] `.claude/agents/code-quality-agent.md` has all 9 v3 edits AND all `[CUSTOMIZE]` content preserved (Phase 3.2)
- [ ] No `.v2-local` files remain (Phase 3.4 cleanup)
- [ ] REGISTRY.md has one row per existing test plan (Phase 4.1)
- [ ] Every existing test plan has a `## Miss Log` section (Phase 4.2)
- [ ] CLAUDE.md updated with v3 prompts table entries (Phase 5.1)
- [ ] Verification sweep passes (Phase 5.2)
- [ ] (Optional) Smoke test passes (Phase 5.4)
- [ ] Migration commits in git history with clear messages
