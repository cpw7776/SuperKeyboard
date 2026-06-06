# Migration: AI Dev Workflow Kit v3 → v4

> **When to use this:** You have a project already running an **adapted** v3 copy of the kit — your context files are filled in, your `.claude/agents/` sub-agents have your stack/credentials baked in, your `docs/testing-agents/` library has real test plans with Miss Logs, and you've been using `feature-lifecycle.md` (v3) + `bugfix.md` (v2) for features and bugs. You want v4's **Test Suite Self-Improvement** (Gap A) without losing any of that.

> **When NOT to use this:** You're starting a brand-new project — drop the v4 kit in fresh, see `docs/README.md` "Quick Setup". OR you're on v2 or earlier — run `docs/upgrading/v2-to-v3.md` first, then this one.

> **Philosophy:** v4 is mostly **additive**, not destructive. Three brand-new files (no merge), two surgical edits to `feature-lifecycle.md` and `bugfix.md` (preserving any project hand-edits), one new context file with no customisation surface, and one new chronological log seeded empty. The risk surface is small. The biggest hazard is overwriting hand-edited prompts.

> **Model:** Use **claude-opus-4-7** for the merge reasoning.

> **Stop points:** Two — after the State Audit (Phase 1), so you can sanity-check what's about to change; and after the surgical merges (Phase 3), before the bootstrapping commit lands.

**Inputs needed:**
- **PROJECT_ROOT:** path to the project you're upgrading (e.g., `~/Code/coffeescribe`)
- **NEW_KIT_ROOT:** path to the v4 kit you've placed beside your project (e.g., `~/Code/coffeescribe/_kit-v4-incoming`)

Both folders must be reachable. The migration agent never touches anything outside `PROJECT_ROOT`.

---

## Phase 1: State Audit (READ-ONLY)

**Goal:** Confirm we're upgrading from v3 (not v2 or v4), and inventory anything that needs surgical handling.

### 1.1 Detect current kit version

```
1. Read PROJECT_ROOT/docs/prompts/feature-lifecycle.md — first line.
   Expected: "# Feature Lifecycle (v3)"
2. Read PROJECT_ROOT/docs/prompts/bugfix.md — first line.
   Expected: "# Bug Fix Protocol (v2)"
3. Read PROJECT_ROOT/.claude/agents/testing-agent.md — first heading line. Note version.
4. Read PROJECT_ROOT/docs/prompts/testing-retro.md — confirm it exists.
   (If it doesn't, the project is on v2 — STOP and run v2-to-v3.md first.)
```

If feature-lifecycle.md is already v4, STOP — the project is already on v4 and doesn't need this migration. Tell the user.

### 1.2 Detect hand-edits to the v3 prompts

`feature-lifecycle.md` and `bugfix.md` are universal prompts meant to be used as-is, but some projects hand-edit them. Detect this so the merge doesn't clobber edits:

```
For each of feature-lifecycle.md, bugfix.md:

1. Run: cd PROJECT_ROOT && git log --oneline --follow -- docs/prompts/{file}
2. If the only commit touching the file is the v3 kit drop-in / v2-to-v3 migration commit, the file is unedited
   → Safe to OVERWRITE with v4.
3. If there are subsequent commits modifying the file, it's been hand-edited
   → MERGE required. Save the project's version as {file}.v3-local before overwriting,
   so the agent can three-way-merge the customisations back in after.
```

Build an **EDITS_FOUND** list capturing every hand-edited prompt and the commits responsible.

### 1.3 Confirm v4 new files don't already exist

These three files are NEW in v4. If they already exist in PROJECT_ROOT, the user has either pre-staged the migration or has their own equivalent:

```
- PROJECT_ROOT/docs/context/Unit_Test_Writing_Guide.md
- PROJECT_ROOT/docs/prompts/test-suite-retro.md
- PROJECT_ROOT/docs/test-suite-misses.md
```

For each:
- **Does not exist** → safe to copy from NEW_KIT_ROOT.
- **Exists and is identical to NEW_KIT_ROOT version** → no-op.
- **Exists and differs** → present diff to the user and ask which to keep. Do not silently overwrite.

### 1.4 Detect existing Project Lessons or test-suite miss notes

Some projects may have ad-hoc "things to remember when writing tests" docs in `docs/context/` or `docs/`. Search for them so they can be bootstrapped into the new guide if appropriate:

```
1. ls PROJECT_ROOT/docs/context/ | grep -iE 'test|tdd|unit'
2. ls PROJECT_ROOT/docs/ | grep -iE 'test-miss|test-fail|test-retro'
3. Search recent bug reports in docs/bugs/ for "Test Autopsy" sections and capture the lessons inline.
```

Build a **BOOTSTRAP_CANDIDATES** list — content from these files that could become Project Lessons in the new guide. Do NOT auto-import yet; surface the list to the user at the Phase 1 stop point.

---

### ⏸️ STOP: Audit Summary

Present the user with:

```markdown
## v3 → v4 Migration Audit

**Current version:**
- feature-lifecycle.md: v3 ✅ (proceeding)
- bugfix.md: v2 ✅
- testing-retro.md: present ✅

**Hand-edited prompts (need surgical merge):**
{EDITS_FOUND list — or "none, prompts are unedited"}

**New v4 files:**
- docs/context/Unit_Test_Writing_Guide.md: {does not exist | matches | differs}
- docs/prompts/test-suite-retro.md: {does not exist | matches | differs}
- docs/test-suite-misses.md: {does not exist | matches | differs}

**Bootstrap candidates (existing notes that could become Project Lessons):**
{BOOTSTRAP_CANDIDATES list — or "none"}

**Plan:**
1. Save .v3-local copies of any hand-edited prompts.
2. Copy three new files into PROJECT_ROOT.
3. Surgically merge v4 changes into feature-lifecycle.md and bugfix.md, preserving hand-edits.
4. (Optional, with user approval) Bootstrap any existing test-lesson notes into Unit_Test_Writing_Guide.md's ## Project Lessons section as retro-style entries.
5. Commit.

Proceed?
```

Wait for explicit approval.

---

## Phase 2: Safe Copies

For each hand-edited prompt in EDITS_FOUND:

```
cp PROJECT_ROOT/docs/prompts/{file} PROJECT_ROOT/docs/prompts/{file}.v3-local
```

These `.v3-local` files are the **source of truth for any hand-edits** during Phase 3's surgical merge. They get deleted at the end of Phase 5.

---

## Phase 3: Surgical Merge

### 3.1 Copy the three new files

These are pure additive — no merge logic needed:

```
cp NEW_KIT_ROOT/docs/context/Unit_Test_Writing_Guide.md PROJECT_ROOT/docs/context/
cp NEW_KIT_ROOT/docs/prompts/test-suite-retro.md PROJECT_ROOT/docs/prompts/
cp NEW_KIT_ROOT/docs/test-suite-misses.md PROJECT_ROOT/docs/
```

If any of these already existed and differed (Phase 1.3), apply the user's Phase 1 decision now.

### 3.2 Merge feature-lifecycle.md

**If unedited:** overwrite directly with v4.

```
cp NEW_KIT_ROOT/docs/prompts/feature-lifecycle.md PROJECT_ROOT/docs/prompts/feature-lifecycle.md
```

**If hand-edited:** three-way merge. The conceptual base is the v3 version (`docs/prompts/archive/feature-lifecycle-v3.md` in NEW_KIT_ROOT). The two heads are the project's `.v3-local` and the new v4. Standard merge logic:

- The v4 changes vs v3 are scoped to: header / version notes block, Phase 2.4 PRD section (added guide-reading paragraph), Phase 3.0 orchestrator input contract (added guide path line), Phase 3.3 TDD cycle (restructured with new step 0 and confirm-RED step 2), Phase 5.1 Gap A (rewritten to invoke `test-suite-retro.md`).
- If the user's hand-edits are in **other sections** (e.g., they edited Phase 1.4 stack research, Phase 4.3 mobile checks), preserve them verbatim and apply only the v4 changes to the scoped sections.
- If the user's hand-edits **overlap** with the v4 changes (rare — most likely if they pre-emptively added their own TDD-step modifications), flag each conflict and ask the user line-by-line. Do not auto-resolve.

### 3.3 Merge bugfix.md

Same logic as 3.2. The v4 changes vs v2 are scoped to: header / version note, Skills to Invoke section (added `test-suite-retro.md` line), Phase 1.4 Test Autopsy (rewritten with 8-category vocabulary), Phase 2.1 Fix the Tests First (added guide-reading paragraph and async deliberate-break check), Phase 2.4 (split into Gap A + Gap B subsections).

### 3.4 Verify merges

Read each merged file and confirm:
- Header version is now v4 (or v3 for bugfix.md).
- The new v4 sections are present.
- Any hand-edits found in Phase 1.2 are still present.

---

### ⏸️ STOP: Merge Review

Show the user a summary:

```markdown
## v3 → v4 Merge Summary

**Files updated:**
- docs/prompts/feature-lifecycle.md (v3 → v4) — {merged with N hand-edits preserved | overwritten, no hand-edits}
- docs/prompts/bugfix.md (v2 → v3) — {merged with N hand-edits preserved | overwritten, no hand-edits}

**Files added:**
- docs/context/Unit_Test_Writing_Guide.md
- docs/prompts/test-suite-retro.md
- docs/test-suite-misses.md

**Bootstrap candidates from Phase 1.4:**
{list — or "none"}

Next: bootstrap any candidates into Unit_Test_Writing_Guide.md's Project Lessons section (Phase 4), then commit.

Proceed?
```

Wait for approval.

---

## Phase 4: Bootstrap Existing Lessons (Optional)

If BOOTSTRAP_CANDIDATES from Phase 1.4 has any entries, walk through each one with the user. For each candidate:

1. Show the original content.
2. Propose a Project Lesson entry in the v4 format (type / log ref / rule / why / example / related anti-patterns).
3. If the candidate doesn't map cleanly to a universal anti-pattern, recommend it as a fresh project-specific rule and ask the user to confirm.
4. Append approved entries to `docs/context/Unit_Test_Writing_Guide.md` under `## Project Lessons`.

**Also seed `docs/test-suite-misses.md` with retroactive rows** for any historical bugs the user wants on the audit trail. Mark these with `(retroactive — bootstrapped)` in the bug column so they're not confused with retros that ran the full protocol.

If BOOTSTRAP_CANDIDATES is empty, skip Phase 4.

---

## Phase 5: Verification & Cleanup

### 5.1 Verification sweep

Read these files end-to-end and confirm internal consistency:

- `docs/prompts/feature-lifecycle.md` — references to `test-suite-retro.md`, `Unit_Test_Writing_Guide.md`, `test-suite-misses.md` all resolve (paths exist).
- `docs/prompts/bugfix.md` — same.
- `docs/context/Unit_Test_Writing_Guide.md` — present, includes anti-patterns A1–A11 and an empty (or bootstrapped) `## Project Lessons` section.
- `docs/test-suite-misses.md` — present with table header.

Update `docs/context/Context_Index_File.md` to include the new files if it isn't auto-rebuilt during the next feature lifecycle.

### 5.2 CLAUDE.md update

If the project's `CLAUDE.md` references the kit (most do — they copy from `CLAUDE_SNIPPET.md`), update the Documentation System section to mention:
- The new context file (`Unit_Test_Writing_Guide.md`)
- The new prompt (`test-suite-retro.md`)
- The new log file (`test-suite-misses.md`)

Use the v4 `docs/CLAUDE_SNIPPET.md` as the reference — copy the new lines, don't wholesale replace CLAUDE.md.

### 5.3 Cleanup

```
rm PROJECT_ROOT/docs/prompts/*.v3-local
```

Only after the merge is confirmed and committed.

### 5.4 Commit

```
git add docs/
git commit -m "chore(kit): upgrade v3 → v4 (Test Suite Self-Improvement / Gap A)"
```

Include in the commit body: which prompts were merged (vs overwritten), how many bootstrap entries were added (if any), and a pointer to `docs/upgrading/v3-to-v4.md` so the history is traceable.

---

## Out-of-scope for this migration

- **Sub-agents in `.claude/agents/`** — none of them change in v4. No merge needed.
- **Testing-agent library in `docs/testing-agents/`** — unchanged. The browser-test self-improvement loop (Gap B) is identical to v3.
- **`docs/context/Testing_Patterns.md`** — if it exists, leave it alone. It's the Tier-2 file for Gap B (browser tests), distinct from the new `Unit_Test_Writing_Guide.md` (Tier-2 for Gap A, unit tests).

---

## What to expect after migration

On the **next feature** the project runs through `feature-lifecycle.md`:
- Phase 2.4 PRD authoring will cite anti-pattern IDs (A1–A11) on `STOP: AI Test` markers.
- Phase 3.3 will read `Unit_Test_Writing_Guide.md` before every failing test, and confirm RED before implementing.
- Phase 5.1 Gap A will (if any bug surfaced) invoke `test-suite-retro.md`, append a row to `test-suite-misses.md`, and — if the miss generalises — append a Project Lesson to the guide.

On the **next bugfix** the project runs through `bugfix.md`:
- Phase 1.4 will categorise the miss into one of 8 buckets (A–H).
- Phase 2.1 will read the guide before rewriting tests.
- Phase 2.4 will run both retros — Gap A always, Gap B when the bug escaped past Phase 4.

The first few retros will feel slow because the `## Project Lessons` section is empty. After 3–5 retros, project-specific patterns start to accumulate and the guide starts paying back. That's the whole point.
