# Upgrade Kit Prompt

You are an agent running inside a target project's directory. Your job: upgrade that project's AI Dev Workflow Kit to the current version, with safe stop points and a final sanity check.

This prompt is self-contained. Follow the steps in order. STOP at the marked points and wait for user approval before destructive changes.

---

## Inputs (confirm before starting)

1. **Path to the new kit.** The user invoked you with a path like `/path/to/ai-dev-workflow-kit`. This prompt lives inside that path at `docs/prompts/upgrade-kit.md`. The new kit must contain:
   - `docs/KIT_VERSION`
   - `docs/KIT_CHANGELOG.md` (renamed from `docs/CHANGELOG.md` in v5.6 — older kit versions look at the old name; the upgrade walks both)
   - `docs/upgrading/vX-to-vY.md` migration prompts (for substantive releases)

2. **Path to the target project.** Usually the current working directory. The project should contain `docs/` and/or `.claude/` from a prior kit adoption.

3. **The kit root inside the target — do not assume the repo root.** The kit can be installed at a package subfolder rather than the repo root (monorepos: `agent/docs/`; subfolder installs: `flashcard-app-upgrade/docs/`). Locate it by finding `KIT_VERSION`, not by assuming `./docs/`:
   ```bash
   # Find the kit root within the target (the dir whose docs/ holds KIT_VERSION):
   find . -name KIT_VERSION -path '*/docs/KIT_VERSION' -not -path '*/node_modules/*' 2>/dev/null
   ```
   If it resolves to a subfolder, treat that subfolder as "root" for **every** path this prompt names (`docs/...`, `.claude/...`), and run git commands from the nearest enclosing git root. If `find` returns multiple hits (several packages each carry the kit), confirm with the user which one to upgrade. If it returns none but `docs/prompts/feature-lifecycle.md` exists somewhere, fall back to that file's parent-of-`docs/` as the kit root (a pre-`KIT_VERSION`, pre-v5.3 install).

If either input is missing, STOP and ask the user.

---

## Step 0 — Confirm this is an upgrade, not a fresh adoption

Check if `docs/prompts/feature-lifecycle.md` exists in the **target project** (not the new kit).

- **NO** → This is a fresh adoption, not an upgrade. STOP. Direct the user to `docs/README.md` → "Quick Setup" in the new kit. Do not proceed.
- **YES** → Proceed to Step 1.

---

## Step 0.5 — Working-tree sanity

Before any read-only fingerprinting (let alone any edits), check the target project's git working tree for uncommitted changes that look like an in-flight prior pass of this same upgrade. This catches the case where a previous session got partway through (e.g. retrofitting test plans, applying a migration prompt) and didn't commit, then the next session re-runs the upgrade and re-applies edits on top of partial work.

```
1. Run `git status --porcelain` in the target project. If clean, skip to Step 1.
2. If there are uncommitted changes, run `git diff` against the workspace and grep for upgrade-content signatures from any release in the chain you're about to walk. Cheap signals:
   - Real slot markers being added — match the full form, not the bare token (see Step 2.5 "Marker-matching discipline"): `grep -E '(<!-- KIT:SLOT-BEGIN|# KIT:SLOT-BEGIN) [a-z][a-z0-9-]*'` (v5.4+)
   - `Sub-Agent Supervision Protocol` (v5.7)
   - `default_action_timeout`, `run_hard_cap`, `progress_log` (v5.7 test-plan retrofit)
   - `Expected duration (whole scenario)` (v5.7 scenario template)
   - The header version stamp pattern `<!-- KIT_VERSION: ` if introduced by a future release
3. If any signature matches, the working tree contains in-flight upgrade work.
```

STOP and present what you found:

> "The working tree has uncommitted changes that look like a prior upgrade pass: [list files + matched signatures]. How would you like to proceed?
>   - (a) Commit what's on disk as a checkpoint before I start (recommended — gives a clean rollback point).
>   - (b) Stash and re-apply after the upgrade.
>   - (c) Treat the uncommitted changes as in-flight retrofit and continue — I'll integrate them rather than overwrite. (Best when the prior pass was partial-correct.)
>   - (d) Discard them — only if you're certain they were a botched attempt."

WAIT for a choice. If (a), commit with `chore(kit): checkpoint before kit upgrade`. If (b), stash. If (c), proceed but pass the file list to Step 4's slot-aware editing so the partial-correct content is preserved across re-applies. If (d), discard (`git checkout -- <files>`) only after explicit confirmation.

If the working tree is clean OR no signatures matched (uncommitted changes are unrelated project work), report `Working tree: clean / unrelated changes only — proceeding.` and continue.

---

## Step 1 — Detect the target project's current kit version

Try these methods in order. The first that succeeds is authoritative.

### Method A — Read the version marker

If `docs/KIT_VERSION` exists in the target project, read it. That single-line value IS the current version (e.g., `5.2`). **Then run Step 1.6 (marker-vs-content reconciliation) before trusting it** — a marker can be over-stamped (ahead of the actual file content).

### Method B — Fingerprint

If no `KIT_VERSION` file exists, walk this decision tree against the target project's files. Take the **highest** version supported by **all** matching conditions.

> **Anchor discipline (v5.16; sharpened v5.16.1 — read before trusting any row).** A fingerprint anchor MUST sit on **stack-invariant, slot-free, AND rewrite-invariant** content, or it false-negatives on legitimately-customized projects. Three anti-patterns burned real downstream upgrades and are now banned:
> - **Never anchor on a `[CUSTOMIZE]` slot body.** A project that fills the slot can remove the anchor string and fail *its own* version fingerprint (pre-v5.16, the v5.12 row anchored on `calibration, not commandments` — which lives inside the `parallel-testing-capacity` slot).
> - **Never anchor on `testing-agent.md` (or any file commonly listed *structurally rewritten* in `KIT_DEVIATIONS.md`).** Non-web projects rewrite it wholesale, so the anchor is permanently absent (pre-v5.16, the v5.8 and v5.10 rows anchored here → permanent false-negatives for every native/CLI project).
> - **Never assume vanilla *prose* survives in a rewritable prompt file — `feature-lifecycle.md` included.** This is the v5.16.1 correction. v5.16 re-anchored v5.10 onto `feature-lifecycle.md`'s slot-free `kit v5.10+` prose, reasoning "every project has `feature-lifecycle.md` (Step 0 proved so)." But *having the file* is not *having the kit's vanilla wording*: a heavily-customized project rewrites `feature-lifecycle.md` wholesale (a real Tauri project carries the full v5.10 capability inside a 480-line project-shaped rewrite, under its own wording — the literal `kit v5.10+` string is absent). So a slot-free region of `feature-lifecycle.md` is only *marginally* safer than `testing-agent.md`; it trades "non-web rewrite" for "heavily-customized rewrite." Treat ANY prompt-file prose as rewrite-prone.
>
> Prefer, in order: (1) **the `docs/KIT_CHANGELOG.md` heading `## [vX.Y]`** — stack-invariant, slot-free, AND rewrite-invariant (present verbatim in every install no matter how heavily the project rewrote its prompts); the only fully divergence-proof surface, and the canonical choice for any release whose feature lives in a divergence-prone area. (2) **File/dir presence** (`docs/prompts/reconcile-change.md` exists, `.claude/commands/reconcile.md` exists) — also rewrite-invariant. (3) **A slot-free region of `feature-lifecycle.md`** — ONLY as a last resort, when no changelog-heading or file-presence signal distinguishes the release, and knowing it false-negatives on projects that rewrote that file (those land as `EXPECTED-FAIL (divergent: feature-lifecycle.md)` in Step 6, exactly like a rewritten `testing-agent.md`). When you add a row for a new release, apply this discipline and note the anchor's location class.

| Check | If TRUE | If FALSE |
|---|---|---|
| `docs/prompts/feature-lifecycle.md` contains the string `Phase 0` | at least v2 | v1 (stop) |
| `docs/testing-agents/REGISTRY.md` exists | at least v3 | at most v2 |
| `docs/context/Unit_Test_Writing_Guide.md` exists | at least v4 | at most v3 |
| `docs/prompts/feature-lifecycle.md` contains the string `Phase 3.5` | at least v5 | at most v4 |
| `.claude/agents/code-quality-agent.md` Pre-Test gate block lists items 1–4 (not 1–5) | at least v5.1 | exactly v5 |
| `docs/prompts/feature-lifecycle.md` Phase 4 manual-test fix loop step 2 says `Model: \`opus\`` (not `sonnet`) | at least v5.2 | exactly v5.1 |
| `docs/KIT_VERSION` exists | at least v5.3 | at most v5.2 |
| `.claude/agents/code-quality-agent.md` contains the string `KIT:SLOT-BEGIN` | at least v5.4 | at most v5.3 |
| `.claude/agents/code-quality-agent.md` frontmatter says `model: opus` (not `sonnet`) | at least v5.5 | exactly v5.4 |
| `docs/KIT_CHANGELOG.md` exists (renamed from `docs/CHANGELOG.md` in v5.6) | at least v5.6 | exactly v5.5 |
| `docs/prompts/feature-lifecycle.md` contains the string `Sub-Agent Supervision Protocol` | at least v5.7 | exactly v5.6 |
| `docs/KIT_CHANGELOG.md` contains the heading `## [v5.8]` *(re-anchored v5.16 — was `testing-agent.md`:`REQUIRES_INPUT`, which is absent on non-web projects that rewrite that file)* | at least v5.8 | exactly v5.7 |
| `docs/prompts/reconcile-change.md` exists (or `.claude/commands/reconcile.md` exists) | at least v5.9 | exactly v5.8 |
| `docs/KIT_CHANGELOG.md` contains the heading `## [v5.10]` *(re-anchored v5.16.1 — v5.16 had moved this onto `feature-lifecycle.md`'s slot-free `kit v5.10+` prose, but that file is rewritten wholesale by heavily-customized projects, so the prose anchor still false-negated there; the changelog heading is rewrite-invariant)* | at least v5.10 | exactly v5.9 |
| `docs/prompts/upgrade-kit.md` contains the string `Marker-matching discipline` | at least v5.11 | exactly v5.10 |
| `docs/KIT_CHANGELOG.md` contains the heading `## [v5.12]` *(re-anchored v5.16 — was `calibration, not commandments`, which lives inside the `parallel-testing-capacity` `[CUSTOMIZE]` slot)* | at least v5.12 | exactly v5.11 |
| `docs/prompts/upgrade-kit.md` contains the string `Post-migration completeness reconciliation` | at least v5.13 | exactly v5.12 |
| `docs/prompts/feature-lifecycle.md` contains the string `RETROSPECTIVE GATE` (Phase 5.1) | at least v5.14 | at most v5.13 |
| `docs/prompts/feature-lifecycle.md` contains the string `Gate count is stack-dependent` (Phase 5.8) | at least v5.15 | exactly v5.14 |
| `docs/prompts/feature-lifecycle.md` Phase 5.8 note contains the string `no Test-Suite gate block` | at least v5.15.1 | exactly v5.15 |
| `docs/prompts/upgrade-kit.md` Step 1 contains the string `Anchor discipline` | at least v5.16 | exactly v5.15.1 |
| `docs/prompts/upgrade-kit.md` Step 1 anchor-discipline preamble contains the string `rewrite-invariant` | at least v5.16.1 | exactly v5.16 |
| `docs/prompts/upgrade-kit.md` contains the string `Upgrade retrospective (defect-gated` (Step 6.5) | at least v5.17 | exactly v5.16.1 |
| `docs/prompts/upgrade-kit.md` Step 6.5 contains the string `DEFECT (always surface — mandatory` | at least v5.17.1 | exactly v5.17 |

**Note on `model: inherit`:** Some projects deliberately override the model field to `inherit` so the agent runs on whatever model the orchestrator is using. Treat `inherit` as a valid third state — it satisfies the v5.5 check (the project intentionally overrode the kit default; do NOT flip it to `opus`). Log it as a project override in the customization inventory and proceed. If the project also documents this override in `docs/KIT_DEVIATIONS.md`, the inventory step will surface that automatically.

**Report:** `Detected version: vX (method: KIT_VERSION marker / fingerprint).`

If the fingerprint is ambiguous (e.g., partial hand-edits that don't match any version cleanly), STOP and present what you found. Ask the user which version they believe the project is on, or to confirm the highest-matching version.

### Step 1.6 — Marker-vs-content reconciliation (catch an over-stamped `KIT_VERSION`)

Method A trusts the `KIT_VERSION` marker — but the marker is just a file someone (or some past upgrade) wrote, and it can be **ahead of the actual content**: a prior pass stamped the version without applying the edits, or skipped a step. A real downstream case: a project's marker read `5.5` while git history + content proved the files were still at `5.4` (the v5.4→v5.5 step was never run). Method A alone would have run the whole chain against an inconsistent baseline.

**Whenever Method A succeeds, cheaply confirm the content agrees before proceeding:**

```
1. Take the marker value vX (Method A).
2. Gather corroboration from content: the highest Method-B fingerprint row that matches
   (and a quick `git log --oneline -- docs/prompts/feature-lifecycle.md` if a git history exists —
   the real downstream case was caught precisely because git history disagreed with the marker).
3. Compare:
   - content agrees with vX (>= the marker, or equal)        -> marker trusted; proceed.
   - content is BEHIND the marker (highest corroboration vY < vX) -> OVER-STAMPED MARKER.
```

**On an over-stamped marker, STOP and surface it as a first-class case:**

> "The `KIT_VERSION` marker says **vX**, but the project's actual content only corroborates **vY** (highest file stamp / fingerprint match). The marker is ahead of the files — a prior upgrade likely stamped without applying, or skipped a step. I recommend treating the **real** current version as **vY** and running the migration chain from there (the marker will be corrected to the true target at the end). Proceed on vY? (Or tell me the version you believe is correct.)"

WAIT for confirmation. Once resolved, use the **reconciled** version as the upgrade's starting point — never the bare marker. (This is the inverse of the Step 2.5 *behind-vanilla* check, which finds individual stale files; this finds a wholesale stale *marker*.)

---

## Step 2 — Determine the target version

Read `docs/KIT_VERSION` from the **new kit** (the kit hosting this prompt). That value is the target.

Confirm with the user:

> `Detected current version: vX. Target version: vY. Proceed with upgrade plan?`

WAIT for explicit approval. If denied, STOP.

---

## Step 2.5 — Inventory customizations (slot markers + hand-edits)

**Goal:** before any destructive change, build an explicit list of what makes the project different from a vanilla install. The upgrade applies kit changes; this inventory tells you what must be preserved across them.

> **Marker-matching discipline (v5.11 — read before any slot grep).** A bare `grep -c "KIT:SLOT-BEGIN"` is **wrong** and will mislead you: it matches prose that *documents* the convention (this very prompt, `MAINTAINING.md`, and `docs/AGENTS.md` all contain the literal token in explanatory text) and the `<name>` placeholder, not just real markers. A real slot marker is the full paired HTML-comment (or inline-`#`) form with a **lowercase-kebab name**. Always match that, never the bare token:
> ```bash
> # Count REAL slots in a file (excludes documentation + the <name> placeholder):
> grep -nE '(<!-- KIT:SLOT-BEGIN|# KIT:SLOT-BEGIN) [a-z][a-z0-9-]* (-->)?' "$file"
> ```
> The `[a-z][a-z0-9-]*` name class rejects `<name>` (starts with `<`). This grep is the authoritative "does this file carry real slots?" test used by Slot-marker extraction below, the Step 4 self-referential vanilla check, and the Step 0.5 working-tree scan. **`docs/prompts/upgrade-kit.md` itself returns several bare-token hits and ZERO real markers — it has no slots.**

### Slot-marker extraction (v5.4+ convention)

If the project's files contain `<!-- KIT:SLOT-BEGIN <name> -->` markers (v5.4+ convention) OR `# KIT:SLOT-BEGIN <name>` (the inline-code-block form used in shell snippets):

```
For each kit file in:
  .claude/agents/*.md
  docs/prompts/feature-lifecycle.md
  docs/CLAUDE_SNIPPET.md  (and any project CLAUDE.md derived from it)

1. List all KIT:SLOT-BEGIN <name> occurrences.
2. For each slot, extract the content between BEGIN and matching END markers.
3. Compare that content against the NEW kit's vanilla content for the same-named slot.
4. If different → record as a customization: { file, slot-name, project-content, vanilla-content }.
5. If same → mark slot as vanilla (no action needed during upgrade).
6. LINT — leftover `[CUSTOMIZE]` token in a filled slot. If a slot's body differs from vanilla (it was filled) BUT still contains the literal `[CUSTOMIZE]` token, that token is a cosmetic leftover from install (the author kept it as a label or forgot to strip it). Flag it `customize-token-leftover: <file>:<slot-name>` in the inventory and offer to strip the bare token during the upgrade (it's harmless, but it muddies future "is this slot filled?" checks). Do NOT flag a slot whose body is still vanilla — there the `[CUSTOMIZE]` token is the legitimate unfilled-template marker.
```

### Pre-v5.4 fingerprint extraction

If the project is older than v5.4, slot markers won't be present. Fall back to grep-based extraction:

```
For each kit file (same list as above):
1. Grep for [CUSTOMIZE] markers in the file.
2. For each, extract the surrounding paragraph/block (until the next blank line or heading).
3. Diff against the corresponding [CUSTOMIZE] block in the NEW kit's vanilla version of the same file.
4. Record differences as customizations.
5. Note: pre-v5.4 customizations frequently CONSUMED the [CUSTOMIZE] marker (replaced its line with project content). If the project file has no [CUSTOMIZE] markers but the new kit's vanilla does, the slots were consumed at install — extract content by anchor instead (the heading or preceding sentence that introduces the slot).
```

### Marker backfill — leave the project marker-safe (pre-v5.4 / markerless installs)

Extraction above gets the customizations *out*; this step ensures they land *back* inside real slot markers, so the **next** upgrade is mechanical instead of another fragile anchor hunt. A pre-v5.4 (or otherwise markerless) install has its project specifics baked in with no markers — exactly the "confident drift" risk the kit warns about. Upgrading once is the cheapest moment to fix that permanently.

```
When re-injecting preserved customizations in Step 4 (into a target version that uses slot markers, v5.4+):
1. Standard slots come marker-safe for free — the new vanilla file already carries
   <!-- KIT:SLOT-BEGIN <name> --> … END around each slot, so anchored content dropped into
   the right slot is now wrapped automatically. Verify each one closed correctly.
2. Project customizations that DON'T map to any vanilla slot (a baked-in hand-edit outside the
   kit's slot set — e.g. a project-specific rule added to a list, a rewritten callout):
     a. Wrap it in a fresh paired marker with a new lowercase-kebab name:
        <!-- KIT:SLOT-BEGIN project-<short-name> --> … <!-- KIT:SLOT-END project-<short-name> -->
        (use the inline `# KIT:SLOT-BEGIN` form inside code/shell blocks).
     b. Record it in docs/KIT_DEVIATIONS.md under "Project extensions to kit files" with the slot name.
3. Structurally-divergent files (whole-file rewrites) are NOT backfilled — they have no kit slots to
   align to and are already recorded as divergent. Leave them owned by the project.
```

After backfill, run the authoritative real-marker grep (Marker-matching discipline, above) over every touched file and confirm every preserved customization now sits inside a paired marker. Report `Marker backfill: N customizations now slot-wrapped (was markerless)` so the win is visible. From the next upgrade on, this project extracts mechanically like any v5.4+ install.

### Hand-edit detection (outside any slot)

```
For each kit file the upgrade will touch (per the CHANGELOG entries between current and target version):
1. Run: git log --oneline --follow <file>  (in the target project)
2. If >1 commit, the file has been hand-edited beyond the drop-in.
3. Diff project's version against the equivalent vanilla file in a prior kit version (use a known-good kit ref if available).
4. Record any divergences that are NOT inside a slot — these are unmarked hand-edits the upgrade must preserve specially.
```

### Structural-divergence detection

Some projects rewrite a kit-shipped file end-to-end because the kit's vanilla doesn't fit the project's stack (e.g. the kit's `testing-agent.md` is browser/Playwright-shaped; a native Android project replaces it wholesale with a Gradle/adb/screencap variant). The project file shares no anchors with the kit's vanilla — there are no slots to re-inject and no per-paragraph edits to apply.

```
For each kit file:
1. Read the project's copy and the NEW kit's vanilla copy.
2. Count overlapping heading anchors (h1/h2/h3 strings) and slot-marker names.
3. If overlap < 25%, OR the project file's purpose visibly differs from the kit's (e.g. different stack name in the intro, different output contract), classify as STRUCTURALLY DIVERGENT.
4. For structurally divergent files: do NOT plan slot-wrap or per-paragraph edits. Record once in the inventory and skip the file in Step 4. The project owns this file outright.
5. If `docs/KIT_DEVIATIONS.md` exists in the project and lists the file under "structurally rewritten," the divergence is documented — proceed without surfacing as a surprise. Record each rewrite there in the machine-readable form (file path as a leading backtick code-span) so Step 6 can read the set deterministically (see Step 6's canonical grep and that section's format note).
```

A structurally divergent file is NOT a problem. It's a deliberate project choice. Log it, skip per-file edits for it, continue.

### `docs/README.md` — confirm it's the KIT readme, not the project's product readme (path collision)

`docs/README.md` is a constant trap: the kit ships its setup guide there, but plenty of projects keep their **product** README at the same path (a monorepo package's `agent/docs/README.md`, a library's docs index, etc.). A README-targeting kit edit (e.g. v5.15's Quick Setup steps) must NEVER be applied to a product README.

**Before any edit to `docs/README.md`, confirm identity by content, not path:** the kit README contains a `## Quick Setup` section (and "AI Dev Workflow Kit" near the top). If those signatures are **present** → it's the kit README; edit normally. If **absent** → it's the project's product README; treat every kit-README edit as **`surface-absent`** (the kit README simply wasn't adopted here — many projects fold it into their root `CLAUDE.md` instead) and record it once in `KIT_DEVIATIONS.md` ("kit README not adopted; `docs/README.md` is the product README"). Do NOT overwrite, merge into, or append kit content to a product README.

### Superset detection — project AHEAD of the kit (v5.11)

Some projects **pre-adopt** a capability before the kit ships it, often as a stricter *superset* of what the kit later releases. When the kit then ships its (weaker or equal) version, a naive migration that says "the kit now covers this, remove the duplicate" would **downgrade the project** — stripping the extra strictness the project deliberately built.

This really happens. CoffeeScribe hand-built all three scenario patterns (A/B/C) months before v5.8, with Pattern B promoted to a base-agent rule and its own Reporting-Format section. v5.8's migration even *named* CoffeeScribe in a special-case note — but prescribed "remove the now-duplicate project-side copy since vanilla covers it." The project copy wasn't a duplicate; it was a superset. Following that literally would have dropped Pattern B and the project's extra rule.

```
For each kit file the release MODIFIES (per the CHANGELOG), before applying the kit's additions:
1. Read the project's copy and the NEW kit's vanilla copy of the region the release adds.
2. Ask: does the project's region already CONTAIN everything the kit is adding (semantically),
   plus more? (e.g. the kit adds "verdict X"; the project already has verdict X AND verdicts Y, Z.)
3. If YES → classify the region as SUPERSET. Do NOT apply the kit's addition (it's redundant) and
   do NOT remove the project's extra content. Record it as a project extension (Step 4.6 →
   "Project extensions to kit files", the v5.8 KIT_DEVIATIONS category). The project stays ahead.
4. If the project's region is EQUAL to vanilla → normal apply. If it's MISSING the kit's addition
   → normal apply (the project is behind here; bring it forward).
```

**Numbered-list collisions (a common superset side-effect).** If a project pre-added its own numbered rule/item to a kit list (e.g. testing-agent `Rule 23`, a code-quality gate item) and the kit's release *also* adds a new entry at that number, keep both — but follow the **canonical resolution: the kit-origin (vanilla) rule keeps its vanilla number; the PROJECT'S extension moves to the next free number at the tail.** Update the project's cross-references (its `KIT_DEVIATIONS.md` entry, `db-precondition.md` Step 5 reference) to the renumbered value and record the renumber in `KIT_DEVIATIONS.md`.

Why this direction (not the reverse): keeping the vanilla rule at its vanilla number means **every project has the kit's rule at the same number**, so a future kit edit to "Rule N" lands correctly everywhere. If the project's extension squatted on the vanilla number and pushed the kit rule to the tail, that project's kit-rule would sit at a different number than all others and silently miss future mechanical edits. (Concretely: a project carrying a pre-existing Pattern B `Rule 23` that meets v5.10's vanilla `Rule 23` (parallel isolation) → **parallel isolation stays Rule 23, Pattern B becomes Rule 24.**)

A SUPERSET file/region is NOT a problem and NOT a downgrade target. The project is ahead; keep it ahead.

### Behind-vanilla detection — project missed a PRIOR release's edit (v5.13, opt-in cleanup)

Superset detection finds where the project is *ahead* of the kit. The symmetric case: the project is *behind* vanilla on a line **no step of the current upgrade targets** — it silently missed an earlier release's edit. Example a downstream retro found: a project's `feature-lifecycle.md` still said "see `CHANGELOG.md` at the kit root," a reference the v5.6 rename to `docs/KIT_CHANGELOG.md` should have fixed, but the project upgraded past v5.6 without that line being touched. The per-release edits structurally can't catch this — the line isn't in the version jump you're running.

A cheap inventory-time full-file diff surfaces these:

```
For each kit-shipped file that is NOT structurally-divergent:
  diff the project's copy against the NEW kit's vanilla copy.
  Lines that differ AND are outside any slot AND outside this upgrade's targeted edits
  are 'behind-vanilla' candidates — the project is stale on kit-default content it never customized.
```

**Do NOT auto-apply these** — they're outside the upgrade's contract, and some differences are intentional (that's what slot/divergence classification already established). Present them as an **opt-in list**: *"I also found N lines where this project trails kit vanilla on content not part of this upgrade: [list]. Refresh those too?"* — and apply only what the user approves. This is how the in-project agent catches drift its own past upgrades left behind, on its own terms.

### Inventory report

Produce a structured list:

```
CUSTOMIZATION INVENTORY:
  Slot-based (will be re-injected mechanically):
    - <file>:<slot-name> — <one-line summary of what's customized>
    ...
  Hand-edited outside slots (will need manual reconciliation):
    - <file>:<section/line-range> — <what's different from vanilla>
    ...
  Structurally divergent (project owns the file; per-paragraph edits will be skipped):
    - <file> — <one-line summary of why it diverges (stack, contract, etc.)>
    ...
  Superset — project ahead of the kit (kit additions are redundant; preserve project's extra content):
    - <file>:<region> — <what the project has that exceeds the kit version it's about to receive>
    ...
  Vanilla (no preservation needed):
    - <file>
    ...
```

If `docs/KIT_DEVIATIONS.md` exists in the project, read it and reconcile against the report — anything it pre-declares (deliberately-left-vanilla slots, structurally rewritten files, absent sections) should be marked as already-documented rather than surfaced as a surprise.

Show this to the user. STOP for review. Hand-edits that conflict with planned upgrade edits become Step 4 STOPs later. Structurally divergent files and already-documented deviations do NOT require user response — they're acknowledged and skipped.

---

## Step 3 — Plan the upgrade path

Read the kit's release log from the new kit. In v5.6+ this is `docs/KIT_CHANGELOG.md`; in v5.3–v5.5 it was `docs/CHANGELOG.md` (renamed in v5.6 to disambiguate from the project's feature log at `docs/context/CHANGELOG.md`). The new kit hosts whichever filename matches its own version — read what's there. List every release entry strictly between the project's current version and the target version, in ascending order.

For each release, classify by reading its `### Migration` subsection:

- **`migration-prompt-required`** → the entry links to a `docs/upgrading/vN-to-vM.md` prompt. Run that prompt in Step 4.
- **`inline-edit`** → the entry contains the patch inline (either an `### Edits` block with explicit Find/Replace pairs, or descriptive Changed/Added/Removed sections detailed enough to apply mechanically). Apply in Step 4.
- **`trivial-noop`** → no project-side action needed. Skip during Step 4 but still bump `KIT_VERSION` through this version in Step 5.

If a release's `Migration` line is missing or unclassified (older entries written before v5.4 formalized the convention), default to `migration-prompt-required` if `docs/upgrading/vN-to-vM.md` exists, otherwise treat as `inline-edit` and apply the entry's Changed/Added/Removed sections directly.

### Example for a v3 → v5.4 upgrade

| Step | From → To | Migration class | What to run |
|------|-----------|------|-------------|
| 1 | v3 → v4 | `migration-prompt-required` | `docs/upgrading/v3-to-v4.md` |
| 2 | v4 → v5 | `migration-prompt-required` | `docs/upgrading/v4-to-v5.md` |
| 3 | v5 → v5.1 | `inline-edit` | Apply CHANGELOG v5.1 (Files touched: feature-lifecycle.md, code-quality-agent.md, README.md, CHANGELOG.md) |
| 4 | v5.1 → v5.2 | `inline-edit` | Apply CHANGELOG v5.2 (Files touched: feature-lifecycle.md — 4 locations) |
| 5 | v5.2 → v5.3 | `inline-edit` | Apply CHANGELOG v5.3 (Added: KIT_VERSION, upgrade-kit.md, moved CHANGELOG into docs/) |
| 6 | v5.3 → v5.4 | `inline-edit` | Apply CHANGELOG v5.4 (Added: v1-to-v2.md migration prompt, slot markers across kit files, drift check + CLAUDE.md step in this prompt) |

### Strategy for badly-trailing files: wholesale-refresh + re-inject slots (sanctioned)

When a project is **many versions behind** (e.g. v5.5 → v5.15) and a given kit file is **VANILLA or cleanly slot-customized** (NOT structurally divergent, NOT hand-edited outside slots), you do **not** have to replay every intervening release's edits to that file one at a time. The sanctioned shortcut: **copy the new kit's current version of the file wholesale, then re-inject the project's slot bodies** (extracted in Step 2.5). One move lands every intervening change to that file, far more reliably than N fragile per-release edits — a downstream v5.5→v5.15 jump found this "much cleaner than 10 rounds of per-release edits." Name it in the plan table (`refresh+reinject`) so the user sees the strategy per file.

**Preconditions (ALL must hold):**
- The file is vanilla or slot-customized only — Step 2.5 found no out-of-slot hand-edits and no structural divergence.
- **Slot parity:** the new kit's version of the file carries the same slot names the project's customizations target. A release may have added/renamed/removed slots — reconcile first: a NEW slot gets the kit default (or a project value if obvious); a REMOVED slot's customization is surfaced, not silently dropped.
- The file isn't owned by a `migration-prompt-required` step that does something structural beyond editing it (let that migration prompt own it instead).

For files that are hand-edited **outside** slots, or **structurally divergent**, do NOT wholesale-refresh — use surgical per-release edits, or skip (divergent, project-owned). This shortcut is for the common "trailing but clean" case — which is most files in a big jump.

Present the plan as a numbered table. STOP for approval. Do not begin executing until the user confirms.

---

## Step 3.5 — Drift check (cross-reference customizations against project truth)

**Goal:** catch the case where a project's stored customization has become stale relative to the project's current reality. Example: the testing-agent's intro slot still references `ANTHROPIC_API_KEY` because the v1 install captured that, but the project's CLAUDE.md and ADRs have since moved to `VENICE_API_KEY`. A mechanical re-injection would re-introduce the stale reference.

```
For each customization extracted in Step 2.5:
1. Extract identifiers from the project content: env var names, model names, library names, API hostnames, file paths.
2. For each identifier:
   a. Grep the project's CLAUDE.md, docs/context/*.md, and docs/ard/*.md for the identifier.
   b. If the identifier appears in those files → match. Customization is consistent with project truth.
   c. If the identifier does NOT appear, OR a related identifier with a different name does (e.g., customization has X but CLAUDE.md says "renamed X to Y") → SUSPECTED DRIFT.
3. List every suspected drift to the user with:
   - File and slot/section
   - Identifier in customization
   - Conflicting identifier in CLAUDE.md / ADR / context file (with citation)
4. STOP. Wait for user to either (a) confirm the drift and provide the correct value, (b) confirm the customization is right and CLAUDE.md is stale, or (c) defer the drift to a separate cleanup PR.
```

This step has saved at least one project (SuperNoteOrganiser) from re-introducing `ANTHROPIC_API_KEY` references after the project had migrated to `VENICE_API_KEY` per ADR Decision 18. The cost is low; the cost of NOT doing this is silent reversion of project-level decisions.

If no drifts are flagged, report `No drift detected — N customizations consistent with project context.` and proceed.

---

## Step 4 — Execute upgrades in order

For each planned step:

### Substantive release (`migration-prompt-required`)

Read the relevant `docs/upgrading/vN-to-vM.md` from the new kit and follow it end-to-end. Each migration prompt has its own state-audit + safe-copies + surgical-merge + bootstrapping + verification flow. Respect its stop points.

Pass the customization inventory from Step 2.5 to the migration prompt. Migration prompts are responsible for preserving slot-based customizations during their edits.

**Post-migration completeness reconciliation (v5.13 — MANDATORY; generalizes the v5.11 tooling-file net).** A migration prompt edits the agents/lifecycle files it was written around, but a release's CHANGELOG `### Files touched` usually lists **more** — propagation docs (`README.md`, `AGENTS.md`, `CLAUDE_SNIPPET.md`) and the tooling file (`upgrade-kit.md`) itself — that the prompt's phases don't enumerate. Left alone they go stale silently: the v5.7→v5.8 prompt changed `upgrade-kit.md` in four places but only prescribed one, and **two independent downstream retros found a stale `AGENTS.md`** after migrating (its testing-agent cell still read the pre-v5.8 verdict set). After the migration prompt finishes, reconcile the gap:

```
1. Read the release's CHANGELOG `### Files touched` (for EVERY version in the jump you just ran).
2. Subtract the files the migration prompt actually edited. The remainder are AT-RISK of going stale.
3. For each at-risk file, the in-project agent decides HOW THE CHANGE APPLIES TO THIS PROJECT —
   inspect, don't assume (the kit only tells you the file changed upstream; how it lands depends on
   what THIS project did to that file):
     - VANILLA here (content-diff vs prior vanilla empty; no real slot markers) → apply the release's
       described edit, or wholesale-refresh from the new kit. Log `applied (propagation refresh)`.
     - STRUCTURALLY DIVERGENT (Step 2.5 inventory) → skip; the project owns it. Log `surface-absent`.
     - SLOTTED / HAND-EDITED → apply the kit's change inside the slot, or surface a `conflict` if it
       overlaps a hand-edit. Never clobber.
4. `upgrade-kit.md` is a special instance: use its self-referential wholesale-refresh-if-vanilla check
   (Patch-release subsection below) rather than a line edit.
```

**The principle (per the kit's universal-drop-in design): the kit guides; the in-project agent reconciles the change against its own project.** The same release lands differently in a Next.js app, a Python pipeline, and a native-Android project — the reconciliation above is how each agent works out what "this file changed" means for *its* codebase, instead of the kit prescribing one outcome.

After the migration prompt finishes (and the tooling-file check above), commit any uncommitted changes with `chore(kit): upgrade to vN.M`.

### Patch release (`inline-edit`)

1. Read the CHANGELOG entry for that version.
2. For every file in `### Files touched`, apply the edits described in `### Changed` / `### Added` / `### Removed`.
3. **Slot-aware editing (v5.4+ projects):** if the edit falls inside a `KIT:SLOT-BEGIN`/`KIT:SLOT-END` region in the project's file, AND that slot is in the customization inventory, do NOT apply the edit verbatim from the CHANGELOG. Instead:
   a. Apply the kit's vanilla update inside the slot.
   b. Re-inject the project's customization on top (preserving any project-specific values like test commands, debug-grep patterns, project intro callouts).
   c. If the kit's update and the project's customization can't both apply (the slot's structure changed in a way the customization assumed), STOP and surface the conflict.
4. **Structurally divergent files (Step 2.5):** if a planned edit targets a file the inventory classified as STRUCTURALLY DIVERGENT (e.g. the project's `testing-agent.md` is a wholesale rewrite for a different stack), do NOT apply the edit. Log `surface-absent: <file> — structurally divergent per inventory` and continue. The project owns the file; the kit can't safely edit it.
5. **Surface-absent edits:** if a planned edit targets a paragraph/section/table that doesn't exist in the project's file (e.g. v5.2's "Model split table" rewrite when the project simplified that whole concept away, or v5.5's "Model preamble blockquote" update when the project's `bugfix.md` predates v5.2 and has no preamble), do NOT search-and-fail or STOP. Log `surface-absent: <file>:<section-name> — target paragraph not present in project copy` and continue to the next planned edit. The release's other edits still apply normally; this single edit is a no-op for this project.
6. **Use Edit (find-and-replace), not Write (overwrite).** Overwriting will clobber `[CUSTOMIZE]` content and hand-edits.
7. For `### Added` items (new files like `docs/KIT_VERSION` or `docs/prompts/upgrade-kit.md`), copy from the new kit using `cp -n` (no-clobber) so existing files aren't overwritten. If a project already has the file with different content, STOP and ask.

**Special case — self-referential edits to `upgrade-kit.md`.** A release may include `### Changed` entries to `docs/prompts/upgrade-kit.md` itself — the kit's own upgrade tooling needs to advance with the kit. Surgical Find/Replace against an older project copy will frequently leave the file stale because the older copy lacks the new anchors the release expects to edit. Decide whether the project's copy is vanilla using **two authoritative checks** (v5.11 — corrected; the old "exactly one commit" gate produced false negatives for every project past its first upgrade):

```
a. CONTENT DIFF (authoritative). Diff the project's copy against the PRIOR kit version's
   upgrade-kit.md, sourcing the baseline robustly (test -f first, then fall back to git):
     base="$NEW_KIT_ROOT/archive/v<prior>/docs/prompts/upgrade-kit.md"
     if [ -f "$base" ]; then
       diff "$base" "$PROJECT_ROOT/docs/prompts/upgrade-kit.md"
     else
       git -C "$NEW_KIT_ROOT" show <prior-kit-ref>:docs/prompts/upgrade-kit.md \
         | diff - "$PROJECT_ROOT/docs/prompts/upgrade-kit.md"
     fi
   Empty / whitespace-only diff ⇒ content-vanilla.
b. NO REAL SLOT MARKERS (authoritative). Run the canonical real-marker grep from Step 2.5's
   "Marker-matching discipline" against the project's copy. Zero real markers ⇒ no-slots.
   (upgrade-kit.md ships with zero real markers — only bare-token documentation hits — so a
   genuine hand-edit would have to ADD a real marker. Cheap to confirm.)
```

If **both** a and b pass ⇒ **VANILLA**: replace the project's `docs/prompts/upgrade-kit.md` wholesale with the new kit's copy. Log `applied (wholesale-replace, vanilla)`.

**Do NOT gate on commit count.** `git log --oneline --follow docs/prompts/upgrade-kit.md` returning **more than one commit is normal and expected** for any project that has run ≥1 prior kit upgrade — each upgrade commits this file. Commit count is *informational only*; never let it block a wholesale-refresh that the content diff says is vanilla. **This false-negative is exactly what both downstream v5.7→v5.9 retros hit** (CoffeeScribe and a vanilla-TS-SPA project both had 2 legitimate commits and both tripped the old gate — they recovered only because the other two checks happened to pass).

If the content diff is non-empty **or** a real slot marker is present, the file has genuine hand-edits — fall through to slot-aware editing (item 3) and surface unreconcilable edits as `conflict`.

8. Commit with `chore(kit): upgrade to vN.M`. The commit message body should list any `surface-absent` skips so the retrospective can audit them.

### Trivial-noop release

No file changes. Note in the upgrade log that this version was skipped because `Migration: trivial-noop`.

### Outcome classifications (per-edit, within a release)

Step 4 produces one of four outcomes per planned edit. The first three are normal; only the fourth is a hard stop:

- **`applied`** — edit applied verbatim (or with slot-aware re-injection on top). Default path.
- **`surface-absent`** — the target paragraph/section/table doesn't exist in this project (intentional or pre-existing deviation). Log and continue. Common when a project simplified an upstream concept the kit later iterates on (model-split table, preamble blockquote, etc.).
- **`structurally-divergent`** — the entire file is a project-owned rewrite per Step 2.5 inventory. Skip every edit targeting it for this release. Log once per file per release.
- **`superset-preserved`** — the project's region already contains (a superset of) what the release adds (Step 2.5 superset detection). Skip the kit's now-redundant addition; keep the project's extra content; record under "Project extensions to kit files" in `KIT_DEVIATIONS.md`. NOT a downgrade target. Includes numbered-list collisions resolved by appending at the next free number.
- **`conflict`** — the project's content can't be reconciled with the kit's update (slot structure changed, hand-edit overlaps the target). STOP and surface to the user.

Every release's commit message body must list every non-`applied` outcome so the retrospective and `docs/KIT_DEVIATIONS.md` can stay accurate.

### Failure handling

`conflict` is the only outcome that triggers a STOP. `surface-absent` and `structurally-divergent` are continuation outcomes — they're how a healthy upgrade handles a project's deliberate deviations. If you find yourself STOPping for every absent paragraph, you're treating deliberate deviations as failures; reread Step 2.5 and the deviations doc.

For genuine failures — file not found that should exist, sub-prompt asks a question you can't answer, the project's `KIT_VERSION` advances but a Step 4 edit still didn't apply — STOP and surface. Do not silently retry, do not skip, do not proceed past a partial failure.

---

## Step 4.5 — CLAUDE.md updates

**Goal:** keep the project's root `CLAUDE.md` (if present) in sync with the kit changes that just landed. `CLAUDE.md` is the project-level navigation file for AI agents; if it references outdated gate counts, item numbers, sub-agent shapes, or prompt names, the agents working in the project will be misled.

Check if the target project has a `CLAUDE.md` at its root. If not, skip this step.

If present, scan it for kit-related sections (typically tables labelled "Workflow Prompts", "Sub-Agents", "Skills", "Post-Feature Gates", "Context Files", or sentences referencing specific phase numbers and item counts).

For each release that was applied in Step 4, walk its `### Changed` / `### Added` / `### Removed` sections and check whether CLAUDE.md needs a corresponding edit:

### Mechanical edits (apply directly)

These are derivable from the CHANGELOG without project-specific judgment:

- **Item-count bumps** (e.g., `11 items → 10 items` from v5.1) — find every occurrence of the old count in CLAUDE.md and replace with the new count.
- **Line-number bumps** (e.g., second vulnerability scan moved from line 8 to line 7 in v5.1) — find every occurrence of the old line number AND its surrounding context (`Line 8 (/vulnerability-scanner (2))` → `Line 7 (/vulnerability-scanner (2))`).
- **Skill renames** (e.g., `/simplify` → `/code-review high` from v5.1) — replace every reference; remove old skill rows from any Skills table; add new rows if the kit added skills.
- **New prompt/agent rows** (e.g., v3 added `testing-retro.md`, v4 added `test-suite-retro.md`, v5.3 added `upgrade-kit.md`) — add a row to the Workflow Prompts table if it's tracked there.
- **Gate-count changes** (e.g., v2 went from three gates to four) — find the gate-listing prose and update.

### Stale-fact corrections — apply, then report (v5.11)

A prose sentence in `CLAUDE.md` that an upgrade renders **factually false** is a mechanical correctness fix, *not* a judgment edit — applying it doesn't lock in a decision, it removes a now-wrong claim. Don't STOP for these; correct them and list each in the final report so the user can review.

- The triggering case: a downstream `CLAUDE.md` said "ABORT is **a fourth verdict** alongside PASS/FAIL/SKIP" — true at v5.7, but v5.8 added two more, so the file now defines six. The "fourth verdict" sentence became false. Correct it to the current verdict set (cite the verdict legend in `testing-agent.md`).
- General test: would the sentence make a new reader believe something the post-upgrade code/docs contradict? If yes, and the correct value is unambiguous from the CHANGELOG/agent file, it's a stale-fact correction — apply it.
- The line between this and a judgment edit: if fixing the fact requires a *placement* or *convention* choice (where does the new paragraph go, which gate gets which number), it's a judgment edit — surface it. If it's a wrong value with one right answer, it's mechanical-with-report.

### Judgment edits (surface as STOPs)

These need project-context decisions and should NOT be auto-applied. Surface each as a STOP with a suggested edit and let the user approve or redirect:

- **Table-row splits** (e.g., v5 made code-quality-agent dual-mode — does the project's table want one row or two?)
- **New paragraph placement** (e.g., the v5.2 Opus fix-sub-agent note — where in CLAUDE.md does it go?)
- **Gate-label conventions** (e.g., if the project labels gates `Gate #1` / `Gate #2` etc., the v2 four-gate change requires choosing which gate gets which number)
- **Project-specific cross-references** (e.g., a CLAUDE.md row that says "Line 9 (Tests) is project-shaped: line 9 reports targeted `pytest` results" — when the kit renumbers Line 9 → Line 8, the project-specific re-framing has to come with it)

Apply mechanical edits via Edit, surface judgment edits via the user. Commit with `chore(kit): sync CLAUDE.md to vN.M` once approved.

If the project has NO CLAUDE.md, skip this step but note it in the final report. Recommend the project consider adding one based on `docs/CLAUDE_SNIPPET.md` from the new kit.

---

## Step 4.6 — Update `docs/KIT_DEVIATIONS.md`

**Goal:** persist any new deviations discovered during this upgrade so the NEXT upgrade doesn't re-flag them as surprises.

Check whether `PROJECT_ROOT/docs/KIT_DEVIATIONS.md` exists.

- **If absent:** copy the template from `NEW_KIT_ROOT/docs/KIT_DEVIATIONS.md` (replace the example bullets with actual entries from this upgrade's observations). If this upgrade discovered no deviations, copy the empty template anyway — future upgrades benefit from the file existing.
- **If present:** read it. For every deviation discovered in this upgrade (from Step 2.5 inventory + Step 4 outcomes), check whether the deviation is already documented. If not, add a new entry under the appropriate section.

Categories the upgrade may have populated:

- **Slots intentionally left as kit templates** — any slot in the Step 2.5 inventory whose project content equals the kit's vanilla `[CUSTOMIZE]` placeholder text (i.e., the project never customized it).
- **Files structurally rewritten** — every file Step 2.5 classified as STRUCTURALLY DIVERGENT.
- **Kit sections intentionally absent** — every `surface-absent` outcome logged in Step 4. Group by file.
- **Sub-agent model overrides** — any agent frontmatter `model:` value other than the kit's current default for that agent.

For each new entry, write one-line entries in the same shape as the template's examples. Surface the proposed additions to the user before writing:

> "I noticed [N] new deviations during this upgrade. Adding to `docs/KIT_DEVIATIONS.md`:
>   - [list]
>  Approve?"

If approved, append the entries. Commit with `docs(kit): record new deviations from vX → vY upgrade`.

If the user prefers to handle deviations separately, skip the commit and surface the list as a deferred TODO in the final report.

---

## Step 5 — Stamp the project with the new version

Write the target version to `docs/KIT_VERSION` in the target project (overwrite if it exists, create if not). Single line, format `N.M` (no `v` prefix, trailing newline).

**Note on idempotency.** Substantive migration prompts (`docs/upgrading/vX-to-vY.md` from v5.5 onward) write `docs/KIT_VERSION` themselves as part of their own phases. When the upgrade ran through one or more migration prompts in Step 4, the file already reads the target version by the time control reaches here — this step becomes a no-op and the commit is empty. That is expected; either skip the commit silently or fold the version stamp into the most recent migration commit. **Do not** treat the no-op as a failure. Step 5 still earns its keep on `trivial-noop`-only paths (e.g. v5.1→v5.2) where no migration prompt ran and nothing else updates the file.

Commit (only if there is a real diff to record):

```
chore(kit): mark project at vN.M
```

---

## Step 6 — Sanity check

Re-run **Method B fingerprinting** from Step 1 (do NOT shortcut by reading the file you just wrote). Each fingerprint check should pass for the target version.

**Divergence-aware verdicts (v5.16; deterministic read v5.16.1) — a FAIL on a rewritten file is EXPECTED, not a failure.** Before treating any failed check as a problem, look at the anchor file. Read the rewritten-file set **deterministically — don't eyeball the prose**: each entry in the `## Files structurally rewritten` section of `docs/KIT_DEVIATIONS.md` leads with the file path as a backtick code-span (the machine-readable token — see that section's format note), so the canonical set is:

```bash
# The exact set of structurally-rewritten files this project declares.
# Match only bullets that LEAD with a backtick path (`- `<file>` — reason`); the
# leading-`- `` ` filter skips the section's explanatory `> ` note (whose prose
# contains inline-code like `FAIL`, `feature-lifecycle.md`) AND any `- _example:_`
# template line, so only real entries are returned.
awk '/^## Files structurally rewritten/{f=1;next} /^## /{f=0} f' "$PROJECT_ROOT/docs/KIT_DEVIATIONS.md" \
  | grep -E '^- `' | sed -E 's/^- `([^`]+)`.*/\1/'
```

If the failing check's anchor file is in that set (the project owns it; the kit's vanilla anchor legitimately doesn't exist there), report the check as **`EXPECTED-FAIL (divergent: <file>)`** and continue — it is NOT a skipped step. Only a check whose anchor file is vanilla/slot-customized (i.e., *should* carry the anchor) and still fails indicates a real skipped/mis-applied edit → STOP. Report each check as one of: `PASS` / `EXPECTED-FAIL (divergent)` / `FAIL (real — investigate)`. (This is why the v5.16 anchor-discipline above moved fingerprints OFF divergence-prone files: fewer EXPECTED-FAILs to reason about. Rows that still land on a project-rewritten file are the ones to mark EXPECTED-FAIL here.)

A real FAIL (vanilla anchor file, anchor absent) means an upgrade step was skipped or applied incorrectly. STOP and surface the discrepancy. An all-`EXPECTED-FAIL`-or-`PASS` result is a clean upgrade — do **not** STOP on EXPECTED-FAILs, and do not describe them as "a step was skipped."

If all checks pass, report to the user:

```
Kit upgrade complete: vX → vY
Migrations run:      [list of vN-to-vM.md prompts executed]
Patches applied:     [list of versions whose CHANGELOG entries were applied inline]
Drift resolved:      [N items from Step 3.5]
CLAUDE.md edits:     [N mechanical, M judgment-approved]
Surface-absent:      [N edits skipped because target paragraph/section not present — see commit bodies]
Structurally div.:   [N files skipped because project owns them outright]
KIT_DEVIATIONS.md:   [created / N new entries added / unchanged]
Files modified:      N (commits: <hashes>)
Project is now at the current kit version. Future upgrades will detect this via docs/KIT_VERSION.
```

### Next-chat handoff (only if upgrade is incomplete or paused)

If the upgrade left in-flight work that the user has chosen to finish in a later session — e.g. Phase 3.5.3 test-plan retrofit was deferred, judgment-edit STOPs were skipped pending user input, `surface-absent` items merit a follow-up cleanup PR, or the user explicitly paused mid-chain — produce a literal paste-ready prompt block addressed to the next agent. Format mirrors `feature-lifecycle.md` Phase 5.10:

- Fenced code block.
- Absolute paths for the project root AND the kit root.
- Imperative voice addressed to the next agent (not a meta-plan addressed to the user).
- Explicit stop points.
- Scoped to only the deferred work — do NOT re-run completed phases.

Example shape:

```
Resume the kit upgrade started in a prior session. Project root: /abs/path/to/project (now stamped at vY). Kit root: /abs/path/to/ai-dev-workflow-kit. The vX → vY upgrade completed except for: [list deferred items, e.g. "test-plan retrofit for 16 plans in docs/testing-agents/*-tests.md per Phase 3.5.3"]. Read /abs/path/to/project/docs/KIT_DEVIATIONS.md (entries dated <today>) for the full deferred list. Re-run ONLY the named phase(s) of /abs/path/to/ai-dev-workflow-kit/docs/upgrading/vX-to-vY.md against the deferred items. Do not re-run any other phase, do not re-stamp KIT_VERSION, do not re-fingerprint. Stop after applying changes and committing per phase.
```

If the upgrade fully completed (no deferred items, no judgment STOPs skipped, no `surface-absent` items needing follow-up), omit this block entirely. A handoff prompt with nothing to hand off is noise.

---

## Step 6.5 — Upgrade retrospective (defect-gated; send to the kit maintainer)

After a clean Step 6 you MAY send the kit maintainer a short retrospective. **This section is the canonical definition of that retro** — older migration prompts referenced "the retrospective `upgrade-kit.md` describes" before one was actually defined here; this is it. Sending the *full* retro is a courtesy (the upgrade is already complete at Step 6) — **but surfacing a DEFECT is NOT optional. If you found one, it is reported to the user with the same priority as an in-flight STOP, whether or not you send the rest of the retro.** "Defect-gated, CLEAN is the success state" means *don't manufacture recommendations* — it does **not** mean stay quiet about a real bug. (Two channels already carry defects independently of this retro: a *blocking* defect STOPs the upgrade in-flight, and every non-`applied` outcome is written to the commit body + `KIT_DEVIATIONS.md` per Steps 4/4.6. This retro is the channel for the **non-blocking** defect — the one that didn't halt you but is still real.)

**It is defect-gated, NOT a recommendation slot — and that distinction is the whole point.** An agent handed a section titled "top N recommendations" will invent N of them even on a flawless upgrade; that manufactures downstream churn (every "nice idea" risks becoming a release, and releases are contracts with multiplied cost). So this retro does **not** ask for recommendations. **The success state of an upgrade is ZERO kit-change findings.** Do not pad. (This mirrors the discipline the forward lifecycle's Phase 5.1 retro already enforces — every finding is either acted on or NAMED as deliberately-not-acted-on, and a zero-finding run still prints with every line `none`.)

Report these sections:

1. **Customizations preserved** — the slot / divergent inventory from Step 2.5, confirming nothing was clobbered. (Evidence the upgrade was safe, not a finding.)
2. **Drift caught** — Step 3.5 result. `none` is the common, good answer.
3. **CLAUDE.md edits** — mechanical-vs-judgment counts from Step 4.5.
4. **Findings** — the gate. Classify each, or report `CLEAN`:
   - **DEFECT (always surface — mandatory, STOP-level priority)** — the upgrade produced a *wrong result*, *forced you to deviate* from the prompt, false-STOPped, false-passed a sanity check, or left the project *internally inconsistent*. Something is actually broken in the kit. **This explicitly includes the LATENT, non-blocking defect: the upgrade completed fine, but you noticed a real kit bug it simply didn't trip on this time** — a fingerprint anchor that will false-negate for the next project, a stale or wrong instruction, a result you worked around. (The canonical case: the v5.10 fingerprint anchored on `feature-lifecycle.md` prose — a project that rewrote that file upgraded *successfully*, yet the anchor was provably broken for the next divergent project. Completing the upgrade did NOT make it not-a-defect.) **A defect that didn't block you is still a defect — surface it to the user with the minimal fix; do NOT let `CLEAN` absorb it.** Only defects are candidates for a kit release.
   - **ENHANCEMENT — queue-only** — "this could be nicer" with nothing actually wrong (clearer wording, a convenience, a pre-answered example). List at most a couple, each labelled `ENHANCEMENT — queue-only`. **Enhancements do NOT justify a release on their own**; they sit in the maintainer's backlog until a defect-driven release can carry them.
   - **CLEAN** — write `Findings: CLEAN — no kit changes indicated` **only when you found no defects** (latent ones included) — `CLEAN` means *no defects*, NOT merely *the upgrade finished*. Then stop the findings section there. Resist listing enhancements unless one genuinely nagged you during the run. **A clean upgrade with an empty findings section is exactly what a mature kit looks like — report it proudly, don't apologise for it with invented suggestions.**

---

## Rules

1. **Never overwrite files with `Write`.** Always use `Edit` for in-place changes — projects have `[CUSTOMIZE]` slot content, miss logs, REGISTRY rows, and hand-edits that an overwrite would destroy.
2. **Never skip a step in the upgrade path.** Later steps assume earlier ones ran. If a step is `trivial-noop`, log that and continue — but you still need to bump `KIT_VERSION` through it.
3. **Never silently resolve a conflict.** If a file's content doesn't match what the upgrade expects, STOP and ask. This includes slot-marker mismatches, hand-edits in unexpected locations, and drift flagged in Step 3.5.
4. **Always run Step 3.5 (drift check) before Step 4.** A clean re-port of a stale customization is worse than no upgrade at all — it locks in obsolete project decisions.
5. **Always run Step 4.5 (CLAUDE.md) after Step 4.** A project whose kit is current but whose CLAUDE.md still references old line numbers will confuse every agent that reads it.
6. **Commit per step, not at the end.** Per-step commits make a botched upgrade recoverable via `git reset` to the last good step. One mega-commit means re-running everything.
7. **Test the project after upgrade — but only on user request.** Don't autonomously run `[TEST_COMMAND]` or `[BUILD_COMMAND]` — those are project-specific and the upgrade itself shouldn't touch runtime behaviour. If the user asks, run them.
