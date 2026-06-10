# Kit Changelog

All notable changes to the AI Dev Workflow Kit. The kit ships as a single versioned bundle — every file in `ai-dev-workflow-kit/` is on the version listed below, regardless of when it was last touched. Per-file version stamps (e.g. `# Feature Lifecycle (v5)`) are no longer used; the heading shows the document's name and this file is the source of truth for which release it belongs to.

> **Naming note:** this file lives at `docs/KIT_CHANGELOG.md` (renamed from `docs/CHANGELOG.md` in v5.6) to disambiguate it from `docs/context/CHANGELOG.md` — the project's feature-by-feature change log maintained by the `context-docs-agent` sub-agent. They serve different purposes: this one tracks kit releases; the other one tracks project features. Two files, same basename was a documented confusion source pre-v5.6.

Format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning is loosely SemVer — major versions add or remove phases / sub-agents, minor versions extend existing phases without restructuring, patches absorb upstream renames or fix wording.

Migration guides (mechanical "how to upgrade your project" instructions) live in `docs/upgrading/vX-to-vY.md` and are referenced from each release below.

### Required entry structure (since v5.4 — formalized for mechanical upgrade)

Every release entry below MUST have these subsections so `docs/prompts/upgrade-kit.md` can route them correctly:

- **`### Added` / `### Changed` / `### Removed`** — what changed conceptually.
- **`### Files touched`** — exact paths the release modified.
- **`### Migration`** — one of three classifications on the first line, followed by per-step detail:
  - `trivial-noop` — no project-side action needed (e.g. release is doc-only inside the kit's source repo).
  - `inline-edit` — the patch can be applied mechanically from this entry alone (no separate migration prompt needed). When this classification is used, an **`### Edits`** block MUST follow with one `Find:` / `Replace:` pair per file edit, each with 2–3 lines of surrounding anchor context.
  - `migration-prompt-required` — the change is too structural for an inline patch. The entry MUST link to a `docs/upgrading/vN-to-vM.md` prompt that walks the migration end-to-end.

The upgrade-kit prompt reads `### Migration` to decide whether to apply the entry inline (`inline-edit`), invoke the linked prompt (`migration-prompt-required`), or skip safely (`trivial-noop`). Backfilled entries for v2–v5 list "Files touched" and a Migration classification where derivable; the v2 entry specifically routes to the new `v1-to-v2.md` prompt added in v5.4.

---

## How to use this file

The kit has two adoption modes:

**1. New project — fresh adoption.** Follow `docs/README.md` → Quick Setup. You'll land on the latest version automatically. This changelog is informational only; you don't need to read prior entries.

**2. Existing project on an older kit version — upgrade adoption.** Use the automated upgrade prompt:

> **In a new Claude Code chat opened in your project's directory, paste:**
>
> > Upgrade this project's AI Dev Workflow Kit. The new kit is at `/path/to/ai-dev-workflow-kit`. Read `/path/to/ai-dev-workflow-kit/docs/prompts/upgrade-kit.md` and follow it.

The upgrade prompt is self-contained — it detects your project's current version (via `docs/KIT_VERSION` if present, or by fingerprinting unmarked legacy projects), reads this changelog to plan the upgrade path, runs each relevant `docs/upgrading/vN-to-vM.md` migration prompt or applies inline patch-release edits, stamps your project with the new `KIT_VERSION` at the end, and sanity-checks the result by re-fingerprinting. Per-step commits make a botched upgrade recoverable with `git reset`.

### Doing it manually (if you'd rather not delegate to an agent)

1. **Find your project's current version.** Read `docs/KIT_VERSION` if present; otherwise fingerprint (see the fingerprint decision tree in `docs/prompts/upgrade-kit.md` Step 1).
2. **Read every changelog entry between your version and the latest**, bottom-up.
3. **For releases with a `docs/upgrading/vX-to-vY.md`**: run that prompt — state audit → safe copies → surgical merge → bootstrapping → verification, with stop points. **Don't overwrite files** — your project has `[CUSTOMIZE]` slots, miss logs, registry rows, and possibly hand-edits an overwrite would clobber.
4. **For patch releases without a migration prompt**: scan "Files touched" + "Changed/Added/Removed" in the entry; apply the edits directly. Self-contained — no extra context needed.
5. **Multi-version jumps**: do them in order. Don't skip — later migrations assume earlier ones ran.
6. **Stamp the project** at the end: write the new version to `docs/KIT_VERSION` and commit.

Either path: every project, regardless of age, can land on the latest kit version by walking forward from wherever they are. No project gets left behind, and no project gets a destructive overwrite of its accumulated state.

---

## [v5.19] — 2026-06-10

**Minor — Gap D (context-fit): the retro learns about the project's CONTEXT, not just its tests and code.** The kit's self-improvement loop had three gaps (A test suite, B testing-agent, C implementation) — all correctness-centric. Nothing asked *"did the agents have the right project context, or did they re-derive knowledge by hand that should have a durable home?"* Gap D closes that: a **read-only** sweep of the epic's own session transcripts (main agent + Task sub-agents + parallel panes — captured from the persisted JSONL, NOT hooks, which can't see sub-agent calls) clusters re-read files into subsystems **stack-agnostically** (relative to the project root — validated against a Swift/SPM false-clean), checks `docs/context/` coverage, and proposes context docs / scoped indexes through a 7-question Necessity Gauntlet (**default No**; the Gauntlet judges by reading the actual docs — a clean run asks the human NOTHING). **Accept = write + register**: the doc is authored from a real code read-pass and added to `context-docs-agent`'s `target-files` slot (scope-annotated) so Phase 5.7a maintains it from then on — created context can never silently rot. A **self-diagnostic** marks degenerate sweeps ⚠ INCONCLUSIVE (a broken run can never report "clean" — the v5.17.1 rule applied to a new mechanism), and every run bookends the **autonomous findings channel** (`~/.claude/context-fit-findings.md`: consume maintainer Advice at start, append one finding — good or bad — at end; the maintainer sweeps it via their review pass). Anti-bloat is conservative: dormant ≠ bloat, default keep, retire only dead-scope/duplicates, human-approved. Proven before shipping on three real projects (CoffeeScribe → authored+registered `Research_Agent_Context.md`; Transcribble → genuinely CLEAN; flashcard-desktop → caught the cross-runtime env/API-key gap behind a failed epic).

### Added
- **`.claude/skills/context-fit/`** — the Gap D engine (new propagation-surface skill, opt-in/removable like `cmux-orchestrator`, but with **no user-level canonical copy — the in-repo skill IS the implementation**; same ensure-present/never-clobber upgrade rule): `SKILL.md` (trigger + run recipe), `run.py` (one-command mechanical pass), `footprint.py` / `candidates.py` (transcript sweep + stack-agnostic clustering + coverage check + self-diagnostic), `context-fit-analysis.md` (the judgment procedure: findings-channel bookends, Gauntlet, consolidate, route, cards, write+register), `Indexing_Guide.md` (scoped indexes `docs/context/indexes/<area>.md`: Scope discipline, self-bootstrap templates, anti-rot, re-index triggers), `index_lint.py` (mis-file lint, exit 1 = gate-suitable).
- **`feature-lifecycle.md` Phase 5.1 Gap D** — epic-level context-fit sweep (steps 10–12, after the per-finding Gap C machinery): mechanical pass → judgment pass → on accept, write + REGISTER. Skill-absent installs print `Gap D: skill absent, skipped` rather than failing the phase.
- **Fingerprint `v5.19` row** (`upgrade-kit.md` Step 1) — anchored on the `## [v5.19]` `KIT_CHANGELOG.md` heading.

### Changed
- **`RETROSPECTIVE GATE` format (Gate #1) — 6 items → 7.** New **item 4 "Gap D — context-fit (epic-level sweep)"** with three lines: `Sweep: [clean | N cards proposed | ⚠ INCONCLUSIVE | skill absent, skipped]`, `Accepted → written + REGISTERED…`, `Finding appended to ~/.claude/context-fit-findings.md`. Former items renumber: Planning 4→**5**, Tier-3 5→**6**, deliberately-NOT-lessoned 6→**7**. The accounting rule now reads "named in item 7" and "Tier-3 proposals (item 6)", and notes Gap D sits OUTSIDE the per-finding reconciliation (one epic-level sweep, its line populated but not counted against `Findings swept: N`). The zero-finding note's "NAMED line in item 6" → "item 7". *(Step 4.5 CLAUDE.md sync: any CLAUDE.md text describing the retro gate's items must add Gap D and renumber accordingly — the kit's own `CLAUDE_SNIPPET.md` line is the reference wording.)*
- **`bugfix.md` 2.4** — heading now "(AUTO — Gap A + Gap C always; Gap B when applicable; **Gap D check**)"; new **"Gap D — Context Gap Check"** subsection after Gap C (one question: *was a missing/unfindable context doc a contributing cause?* Yes → one-line `[UNCHECKED]` finding to the findings channel naming the homeless area + bug ID; No → skip with a reason). The closing Report line adds the Gap D clause.
- **`reconcile-change.md` 2.1** — "three gap questions" → "four"; new **Gap D bullet** (same one-question check); the gate's `Lessons:` line adds `Gap D: {context-gap finding logged | n/a}`.
- **`docs/AGENTS.md`** — gate #1 description adds Gap D; "Optional project skills" section adds the `context-fit` entry (no global-canonical copy; ensure-present/never-clobber).
- **`docs/CLAUDE_SNIPPET.md`** — gate #1 description adds Gap D (the reference wording for Step 4.5 CLAUDE.md sync).
- **`docs/README.md`** — adoption tree + folder table list `.claude/skills/context-fit/`.

### Files touched
- `.claude/skills/context-fit/` — `SKILL.md`, `run.py`, `footprint.py`, `candidates.py`, `context-fit-analysis.md`, `Indexing_Guide.md`, `index_lint.py` (all new)
- `docs/prompts/feature-lifecycle.md` (Phase 5.1 Gap D + gate renumber)
- `docs/prompts/bugfix.md` (2.4 Gap D check)
- `docs/prompts/reconcile-change.md` (2.1 Gap D bullet + gate line)
- `docs/AGENTS.md`, `docs/CLAUDE_SNIPPET.md`, `docs/README.md` (propagation)
- `docs/prompts/upgrade-kit.md` (v5.19 fingerprint row)
- `docs/upgrading/v5.18-to-v5.19.md` (new migration prompt)
- `docs/KIT_VERSION` (5.18 → 5.19), `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.18-to-v5.19.md`. Anchored insertions into three rewrite-prone prompt files (role-based fallbacks included), a gate-format renumber that must propagate to CLAUDE.md, and the new skill directory (ensure-present, never-clobber; a project that deliberately removed it per `KIT_DEVIATIONS.md` is not re-seeded). Python 3 is required only when the sweep actually runs (stdlib-only; no packages).

---

## [v5.18] — 2026-06-08

**Minor — new optional capability + new propagation surface: ship a `cmux-orchestrator` skill so every project (and future project) can run orchestrator mode, without a CLAUDE.md-section propagation problem.** Maintainer need: parallel "orchestrator mode" (several epics as separate Claude sessions tiled in one cmux workspace) was documented only as a CLAUDE.md section the maintainer had hand-added to *some* projects — stale projects on older kit versions never got it. A skill is the right primitive (an action with a trigger, self-advertising via its description) and a **user-level (global) skill** at `~/.claude/skills/cmux-orchestrator/` makes the propagation problem *disappear*: it's available in every project on that machine regardless of kit version. The kit also ships an in-repo copy so the capability **travels to other machines** that lack the global skill. Confirmed against Claude Code skill resolution: personal (global) overrides project, no collision; a skill file is read once per session (so self-improvement edits take effect next session). No lifecycle/phase/sub-agent/gate change — this is additive infrastructure.

### Added
- **`.claude/skills/cmux-orchestrator/SKILL.md`** — the kit-shipped (snapshot) copy of the orchestrator skill. Recipe for combining the existing `cmux*` operation skills into orchestrator mode: hard rules (one workspace, balanced equal tiling, named tabs), the proven per-task launch flow (worktree + `cmux new-surface`/`send`/`send-key` + `split-off`), verify/monitor commands. Carries a **sync-&-fallback** preamble (global is canonical and overrides this copy where installed; this copy is the travel fallback when global is absent) and a **self-improvement** section (append verified fixes to its Troubleshooting section; takes effect next session). `allowed-tools` scoped to `Bash(cmux *)`, `Bash(cp *)`, `Bash(cmp *)`, `Bash(git worktree *)`, `Bash(ln *)`, `Read`, `Write`.
- **`.claude/skills/` as a propagation surface** — documented in `docs/README.md` adoption tree and `docs/AGENTS.md` ("Optional project skills"). Opt-in/removable, like the `mobile-android` capability slot.
- **Fingerprint `v5.18` row** (`upgrade-kit.md` Step 1) — anchored on the `## [v5.18]` `KIT_CHANGELOG.md` heading (rewrite- AND removal-invariant; file-presence was rejected because the skill is removable and would false-negate). This is the first new row to rely on the v5.17.2 guarantee that Step 5 keeps the project's changelog copy current.

### Changed
- **`docs/README.md`** adoption tree now lists `.claude/skills/cmux-orchestrator/` with a one-line note that the global copy overrides the in-repo one where present.
- **`docs/AGENTS.md`** gains an "Optional project skills (`.claude/skills/`)" section documenting the skill, its global-overrides-project precedence, the travel-fallback role, and the ensure-present/never-clobber upgrade rule.

### Files touched
- `.claude/skills/cmux-orchestrator/SKILL.md` (new)
- `docs/README.md` (adoption tree)
- `docs/AGENTS.md` (optional-skills section)
- `docs/prompts/upgrade-kit.md` (v5.18 fingerprint row)
- `docs/KIT_VERSION` (5.17.2 → 5.18)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`inline-edit`. Two project-facing actions:
1. **Add the skill (ensure-present, never-clobber).** If `.claude/skills/cmux-orchestrator/SKILL.md` is **absent**, create it from the new kit: `mkdir -p .claude/skills/cmux-orchestrator && cp -n <new-kit>/.claude/skills/cmux-orchestrator/SKILL.md .claude/skills/cmux-orchestrator/SKILL.md`. If **present**, leave it untouched — a project's copy may have been refreshed from the user's newer global skill, so the kit snapshot must NOT overwrite it. If the project recorded the skill as intentionally removed in `docs/KIT_DEVIATIONS.md` (opt-out), do not re-seed it and do not surface it as missing (per the opt-in-capability rule).
2. **Self-referential `upgrade-kit.md`** edit (the v5.18 fingerprint row) — use Step 4's special case: wholesale-refresh if content-vanilla, else add the one row.

This is an **opt-in capability**: a project that doesn't use cmux orchestration can omit/remove the skill; the upgrade must not flag it as missing. The user-level global skill (`~/.claude/skills/cmux-orchestrator/`) is installed once per machine, outside the kit, and is the canonical self-updating copy — it is NOT part of this or any project's repo.

### Edits
- **`.claude/skills/cmux-orchestrator/SKILL.md`** — new file; `cp -n` from the new kit (never clobber an existing copy). See Migration step 1.
- **`docs/README.md`** — in the adoption tree under `.claude/`, change `commands/` from a `└──` leaf to `├──` and append a `skills/` branch with `cmux-orchestrator/`.
- **`docs/AGENTS.md`** — after the "Skills the kit invokes" section, add the "Optional project skills (`.claude/skills/`)" section.
- **`docs/prompts/upgrade-kit.md` — self-referential; do NOT line-edit a vanilla copy.** If content-vanilla (diff against `archive/v5.17.2/docs/prompts/upgrade-kit.md`) with no real slot markers → **wholesale-replace**. Else add the `## [v5.18]` fingerprint row after the `v5.17.2` row.

---

## [v5.17.2] — 2026-06-07

**Patch — close a latent self-undermining gap: the upgrade never refreshed the project's `KIT_CHANGELOG.md`, yet v5.16/v5.16.1 made the `## [vX.Y]` changelog heading the canonical fingerprint anchor.** Surfaced by a downstream v5.14→v5.17.1 upgrade retro (a Native-Android project) and confirmed against source. The fingerprint strategy bets on the project's changelog copy containing the heading it anchors on — Step 1 calls it "the only fully rewrite-invariant surface" and steers all new rows onto it — but **nothing in the upgrade kept that copy current.** Step 3 reads the changelog from the *new kit*; the inline-edit path treats every release's `KIT_CHANGELOG.md (this entry)` as maintainer-side and never propagates it; and the one safety net (Step 4 post-migration completeness reconciliation) runs **only** on the `migration-prompt-required` path — so a pure inline-edit jump leaves the project's changelog frozen at its adopt-version. Latent today (every changelog-anchored row is ≤ v5.12, present in any current copy), but the moment a future release follows the kit's own stated preference and anchors a new row on a near-target heading, Step 6 re-fingerprinting would throw a **real FAIL** on a clean upgrade. The retro's project was only saved because its jump happened to include migration prompts (the net fired, commit `f4eecb3`). **Fix is to the upgrade tooling only; no lifecycle/phase/sub-agent/downstream-behaviour change.**

### Changed
- **`upgrade-kit.md` Step 5 now refreshes `docs/KIT_CHANGELOG.md` wholesale from the new kit, on every path** (renamed heading: "Stamp the project with the new version *(and refresh the changelog copy)*"). The changelog is a pure kit reference — stack-invariant, slot-free, never hand-edited — so it is always safe to overwrite, exactly like `KIT_VERSION`. Step 5 always runs (including the trivial-noop-only and pure-inline-edit paths), which is why the refresh lives here rather than in a path-gated Step 4 branch. Handles the pre-v5.6 `docs/CHANGELOG.md` name for not-yet-renamed projects.
- **`upgrade-kit.md` Step 4 post-migration completeness reconciliation** — added item 5 naming `KIT_CHANGELOG.md` as a special instance (always lands in the at-risk remainder via "this entry," but is refreshed wholesale in Step 5; never diff it region-by-region, never treat a stale copy as a `conflict`). Keeps the two propagation paths consistent.
- **`upgrade-kit.md` Step 1 anchor-discipline preamble** — the `## [vX.Y]` changelog-heading preference (anchor option 1) now states explicitly that it is only sound *because* Step 5 keeps the project's copy current, with a "do not weaken it" note for the next maintainer who adds a changelog-anchored row.
- **`MAINTAINING.md` rule 12** — same invariant recorded on the maintainer side: a changelog-heading fingerprint anchor depends on the Step 5 changelog refresh; the two must move together.

### Added
- **Fingerprint `v5.17.2` row** (`upgrade-kit.md` Step 1) — anchored on the new Step 5 heading string `refresh the changelog copy` (a tooling-file anchor; `upgrade-kit.md` is never rewritten).

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 5 changelog refresh; Step 4 net item 5; Step 1 preamble note; v5.17.2 fingerprint row)
- `MAINTAINING.md` (rule 12 — maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.17.1 → 5.17.2)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`inline-edit`. The only project-facing file is `docs/prompts/upgrade-kit.md` — **self-referential**, so use Step 4's special case (wholesale-refresh if vanilla; canonical content-diff + no-real-markers check). No downstream behaviour change; the effect is that from this upgrade on, every project's `KIT_CHANGELOG.md` copy is kept current, so the kit's preferred changelog-heading fingerprint anchor actually resolves.

### Edits
- **`docs/prompts/upgrade-kit.md` — self-referential; do NOT line-edit a vanilla copy.** If content-vanilla (diff against `archive/v5.17.1/docs/prompts/upgrade-kit.md`, or the v5.17.1 ref) with no real slot markers → **wholesale-replace**, log `applied (wholesale-replace, vanilla)`. If hand-edited, apply four edits: (a) Step 5 heading → add "*(and refresh the changelog copy)*" and the wholesale-`cp` refresh paragraph after the `KIT_VERSION`-write sentence; (b) Step 4 post-migration completeness reconciliation → add item 5 for `KIT_CHANGELOG.md`; (c) Step 1 anchor-discipline preference option (1) → add the "only sound because Step 5 refreshes the copy" note; (d) add the `v5.17.2` fingerprint row after the `v5.17.1` row. Role: Step 5 is the load-bearing one — the project's changelog must be refreshed on every path.

---

## [v5.17.1] — 2026-06-07

**Patch — close a feedback gap v5.17 itself opened: make surfacing a found DEFECT mandatory, so "CLEAN is the success state" can't suppress a real bug.** Maintainer question right after v5.17 shipped: *"how do they surface the bugs that are found if they don't report back?"* v5.17 made the upgrade retro defect-gated to stop manufacturing churn — correct — but it also made the whole retro **optional** ("you MAY send… a courtesy, not a gate") and framed `CLEAN` as success. Right for killing enhancement padding; wrong for the **latent, non-blocking defect** — the upgrade completes, the agent notices a real kit bug it didn't trip on (the v5.10-anchor case: a project that rewrote `feature-lifecycle.md` upgraded *successfully*, yet the anchor was provably broken for the next project). An over-cautious reading of "CLEAN is the win, don't manufacture" could let that get absorbed into CLEAN instead of surfaced. This is a defect in v5.17's mechanism, not an enhancement. No lifecycle change.

### Changed
- **`upgrade-kit.md` Step 6.5 — DEFECT surfacing is now mandatory (STOP-level priority), independent of whether the courtesy retro is sent.** Three clarifications: (1) the opening now distinguishes "sending the full retro is optional" from "surfacing a DEFECT is not optional," and names the two retro-independent channels that already carry defects (a *blocking* defect STOPs in-flight; every non-`applied` outcome is written to the commit body + `KIT_DEVIATIONS.md` per Steps 4/4.6) — this retro is the channel for the **non-blocking** defect. (2) The DEFECT bullet explicitly includes the latent/non-blocking case ("a defect that didn't block you is still a defect — do NOT let `CLEAN` absorb it"), with the v5.10 anchor as the worked example. (3) `CLEAN` now means *no defects found* (latent included), **not** *the upgrade finished*.

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 6.5 mandatory-defect-surfacing + latent-defect carve-out; v5.17.1 fingerprint row)
- `docs/KIT_VERSION` (5.17 → 5.17.1)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`inline-edit`. The only project-facing file is `docs/prompts/upgrade-kit.md` — **self-referential**, so use Step 4's special case (wholesale-refresh if vanilla; canonical content-diff + no-real-markers check). No downstream behaviour change; the effect is that the next upgrade's retro can't let a real bug exit as CLEAN.

### Edits
- **`docs/prompts/upgrade-kit.md` — self-referential; do NOT line-edit a vanilla copy.** If content-vanilla (diff against `archive/v5.17/docs/prompts/upgrade-kit.md`, or the v5.17 ref) with no real slot markers → **wholesale-replace**, log `applied (wholesale-replace, vanilla)`. If hand-edited: in Step 6.5, (a) change the opening so surfacing a DEFECT is mandatory/STOP-level while the full retro stays optional; (b) extend the DEFECT bullet to include the latent/non-blocking defect ("a defect that didn't block you is still a defect; do NOT let CLEAN absorb it"); (c) make CLEAN mean "no defects found," not "the upgrade finished." Role: the Findings classification list in Step 6.5.

---

## [v5.17] — 2026-06-07

**Minor — make the upgrade retrospective defect-gated, so a clean upgrade stops manufacturing change.** Driven by a maintainer observation, not a downstream retro: *"every kit upgrade comes back with a retrospective that we have to change something — surely not every time. Are we forcing them?"* We were. The kit solicited a retro with a **"top recommendations" section** (`docs/upgrading/v5.4-to-v5.5.md`), and an LLM handed a "top N recommendations" slot fills it even on a flawless upgrade — manufacturing downstream churn, since each "nice idea" risks becoming a release and releases are contracts with multiplied cost. Worse, the retro was a **phantom**: that prompt said "the retrospective `upgrade-kit.md` describes," but `upgrade-kit.md` Step 6 only ever defined a completion report. Evidence the slot over-produced: across the two v5.15→v5.16 retros, 8 recommendations → 2 real defects; the rest were enhancements / already-decided / already-covered. **This release flips the default: the success state of an upgrade is ZERO kit-change findings, reported proudly.** No lifecycle/phase/sub-agent change.

### Added
- **`upgrade-kit.md` Step 6.5 — the canonical, defect-gated upgrade retrospective.** Replaces the phantom reference. Sections: customizations preserved · drift caught · CLAUDE.md edits · **Findings**, where each finding is classified **DEFECT** (wrong result / forced deviation / false-STOP / false-pass / inconsistency → candidate for a release) or **ENHANCEMENT — queue-only** (nothing broken → backlog, never a release on its own), and a clean run reports `Findings: CLEAN — no kit changes indicated` and stops. Explicitly mirrors the forward lifecycle's Phase 5.1 discipline (every finding acted-on or NAMED-as-not; a zero-finding run still prints with every line `none`) — **this is a parity-restoring change: the upgrade retro was the lone retro lacking that anti-manufacturing discipline; the three self-improvement flows (feature-lifecycle / bugfix / reconcile) already had it, and are untouched.**

### Changed
- **`MAINTAINING.md` release checklist item 1 — release-decision gate before the bump.** Decide whether a finding earns a release AT ALL: defect → ship; enhancement → queue (never cut a release for enhancements alone). Names the slot-filling failure mode and the "weigh findings by what broke, not how many were listed" rule. Patch definition broadened to include "narrow defect fix to the upgrade tooling" (matching how v5.15.1 / v5.16.1 were actually used).
- **`docs/upgrading/v5.4-to-v5.5.md` retrospective pointer** — was the lone migration prompt still soliciting "top recommendations"; now points to the defect-gated Step 6.5 and tells a clean upgrade to report `CLEAN`, not a suggestion list. (The v5.5-specific factual questions are kept — they ask what happened, not for recommendations.)

### Housekeeping
- **Backfilled `archive/v5.16/.claude/`** — the v5.16.1 release archived `archive/v5.16/docs/` but omitted `.claude/` (prior archives include both). Corrected; `archive/v5.16.1/` captured complete (docs + `.claude`).

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 6.5 defect-gated retro; v5.17 fingerprint row)
- `docs/upgrading/v5.4-to-v5.5.md` (retrospective pointer)
- `MAINTAINING.md` (release-decision gate — maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.16.1 → 5.17)
- `docs/KIT_CHANGELOG.md` (this entry)
- `archive/v5.16/.claude/` (backfill), `archive/v5.16.1/` (new snapshot) — maintainer-only

### Migration
`inline-edit`. The only project-facing file is `docs/prompts/upgrade-kit.md` — **self-referential**, so use Step 4's special case (wholesale-refresh if vanilla; canonical content-diff + no-real-markers check). `docs/upgrading/v5.4-to-v5.5.md` is a kit-shipped migration prompt the project also carries a copy of — refresh it if vanilla, else `surface-absent`. No downstream behaviour change; the effect is that the *next* upgrade this project runs reports CLEAN instead of inventing recommendations.

### Edits
- **`docs/prompts/upgrade-kit.md` — self-referential; do NOT line-edit a vanilla copy.** Per Step 4's "self-referential edits to upgrade-kit.md" special case: if content-vanilla (diff against `archive/v5.16.1/docs/prompts/upgrade-kit.md`, or the v5.16.1 ref) with no real slot markers → **wholesale-replace**, log `applied (wholesale-replace, vanilla)`. If hand-edited: add the new `## Step 6.5 — Upgrade retrospective (defect-gated …)` section (role: between Step 6's next-chat-handoff and `## Rules`) and the `v5.17` fingerprint row.
  - **Find (`docs/upgrading/v5.4-to-v5.5.md`):** the "Retrospective" paragraph soliciting `… missing migration content, top recommendations, effort breakdown). Send it to the kit maintainer …`
  - **Replace:** the defect-gated pointer to Step 6.5 (`a clean upgrade reports Findings: CLEAN … do not manufacture recommendations`). If the project doesn't carry this migration prompt (already past v5.5), `surface-absent`.

---

## [v5.16.1] — 2026-06-07

**Patch — fix a latent false-negative that v5.16 itself introduced, plus a conformance gap, both surfaced by the v5.15→v5.16 upgrade retros (a Tauri project + a Native-Android project).** v5.16's headline fix re-anchored misfiring fingerprint rows OFF divergence-prone files. For v5.10 it moved the anchor onto `feature-lifecycle.md`'s slot-free `kit v5.10+` prose, reasoning "every project has `feature-lifecycle.md` (Step 0 proved so)." The Tauri retro proved that reasoning wrong: a heavily-customized project rewrites `feature-lifecycle.md` *wholesale* (a 480-line project-shaped rewrite carrying the v5.10 capability under its own wording — the literal `kit v5.10+` string absent). So the v5.10 anchor still false-negated; v5.16 merely traded "non-web rewrite of `testing-agent.md`" for "heavily-customized rewrite of `feature-lifecycle.md`." **This is the same anchor-fragility class v5.16 set out to kill — `feature-lifecycle.md` is rewrite-prone just as `testing-agent.md` is.** Separately, the Android retro had to reconstruct the v5.15.1 "already on v5.15" action list from changelog prose because that entry — though classified `inline-edit` — shipped without the mandatory `### Edits` block. **All changes are to the upgrade tooling + a historical changelog entry; no lifecycle/phase/sub-agent change.**

### Changed
- **Fingerprint `v5.10` row re-anchored to the `## [v5.10]` `KIT_CHANGELOG.md` heading** (`upgrade-kit.md` Step 1) — rewrite-invariant, matching how v5.8 and v5.12 were fixed in v5.16. The changelog heading is present verbatim in every install no matter how heavily the project rewrote its prompts.
- **Anchor-discipline preamble gains a third banned anti-pattern + a reordered preference list** (`upgrade-kit.md` Step 1; `MAINTAINING.md` rule 12). Third anti-pattern: *never assume vanilla prose survives in a rewritable prompt file — `feature-lifecycle.md` included.* Preference order is now (1) `KIT_CHANGELOG.md` heading [the only fully rewrite-invariant surface] → (2) file/dir presence → (3) a slot-free region of `feature-lifecycle.md` ONLY as a last resort (it `EXPECTED-FAIL (divergent)`s on projects that rewrote that file). v5.16 had `feature-lifecycle.md` regions at #1.
- **Step 6 sanity reads the structurally-rewritten set deterministically** (`upgrade-kit.md`) — a canonical `awk`+`grep` over the `## Files structurally rewritten` section's backtick-wrapped paths, instead of "eyeball the prose." Codifies the kit's own "machine-readable beats prose-derived for anything an upgrade branches on" lesson for the divergence-aware verdict.
- **`docs/KIT_DEVIATIONS.md` "Files structurally rewritten" section formalized as a machine-readable surface** — the leading backtick code-span is the parseable token; added a load-bearing note (mirroring the `Gate count:` line) and an explicit reminder that `feature-lifecycle.md` belongs there too if rewritten wholesale, not only `testing-agent.md`.

### Added
- **The missing `### Edits` block on the `## [v5.15.1]` entry** (this file) — the v5.4 required-entry-structure says an `inline-edit` release MUST carry Find/Replace pairs; v5.15.1 shipped the "already on v5.15" path as prose only, forcing downstream agents to reconstruct it. Now enumerated with anchor context.
- **Fingerprint `v5.16.1` row** (`upgrade-kit.md` Step 1) — anchored on the new `rewrite-invariant` string in the anchor-discipline preamble (a tooling-file anchor; `upgrade-kit.md` is never rewritten).

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 1 preamble third anti-pattern + reordered preference; v5.10 row re-anchored; v5.16.1 row added; Step 2.5 + Step 6 deterministic structurally-rewritten read)
- `docs/KIT_DEVIATIONS.md` (Files-structurally-rewritten machine-readable format note)
- `MAINTAINING.md` (rule 12 sharpened — maintainer-only, not shipped)
- `docs/KIT_CHANGELOG.md` (this entry + the v5.15.1 `### Edits` backfill)
- `docs/KIT_VERSION` (5.16 → 5.16.1)

### Migration
`inline-edit`. The only project-facing file is `docs/prompts/upgrade-kit.md`, which is **self-referential** — use Step 4's special case (wholesale-refresh if vanilla; the canonical content-diff + no-real-markers check). The `docs/KIT_DEVIATIONS.md` change is to the *template's* explanatory format note only — refresh it if the project kept the kit boilerplate, else `surface-absent`. No lifecycle change; the win is that the *next* upgrade fingerprints `v5.10` correctly on a project that rewrote `feature-lifecycle.md`.

### Edits
- **`docs/prompts/upgrade-kit.md` — self-referential; do NOT line-edit a vanilla copy.** Per Step 4's "self-referential edits to upgrade-kit.md" special case: if the project's copy is content-vanilla (diff against `archive/v5.16/docs/prompts/upgrade-kit.md`, or the v5.16 ref) with no real slot markers → **wholesale-replace** with the new kit's copy (`applied (wholesale-replace, vanilla)`). Only if the copy is hand-edited, apply the three changes surgically: (a) re-anchor the `v5.10` fingerprint row to `## [v5.10]`; (b) add the third anti-pattern + reorder the preference list in the Step 1 anchor-discipline preamble; (c) add the deterministic `awk`+`grep` to Step 6's divergence-aware verdict. Each has a role-based location (the v5.10 row in the Method-B table; the preamble blockquote above the table; the divergence-aware paragraph in Step 6) — match by role if the literal text drifted.
  - **Find:** `docs/KIT_DEVIATIONS.md` "Files structurally rewritten" section header + its `> Format: ` blockquote.
  - **Replace:** the machine-readable format note (backtick code-span = parseable token; load-bearing for Step 6; `feature-lifecycle.md` belongs here too). If the project rewrote this section's prose, log `surface-absent` and skip — the Step 6 grep works against any bullet list whose paths are backtick-wrapped.

---

## [v5.16] — 2026-06-07

**Minor — upgrade-system robustness (the convergent cluster from three same-week v5.14→v5.15 retros) + a native-mobile worked example.** Venice Logger, Personal Agent, and a Fossify-based Android app independently surfaced the same class of upgrade-machinery defects: detection that false-negatives on legitimately-customized projects, sanity checks that false-STOP on deliberately-divergent files, and brittle migration anchors. Same lineage as v5.6/v5.11/v5.13. **All changes are to the upgrade tooling + prompt guidance — no lifecycle/phase/sub-agent change.**

### Changed
- **Fingerprint anchor discipline (`upgrade-kit.md` Step 1) — re-anchored three misfiring rows.** A fingerprint MUST sit on stack-invariant, slot-free, non-divergence-prone content. Fixed: **v5.12** anchored on `calibration, not commandments` — which lives *inside* the `parallel-testing-capacity` `[CUSTOMIZE]` slot, so any project that filled the slot failed its own fingerprint → re-anchored to the `## [v5.12]` changelog heading. **v5.8 / v5.10** anchored on `testing-agent.md` (REQUIRES_INPUT / Parallel-Run Mode) — permanently absent on non-web projects that rewrite that file → re-anchored to the `## [v5.8]` changelog heading and to `feature-lifecycle.md`'s slot-free `kit v5.10+` prose. Added an "Anchor discipline" preamble banning slot-bound and divergence-prone anchors going forward.
- **Step 6 sanity is now divergence-aware.** A fingerprint whose anchor file is listed *structurally rewritten* in `KIT_DEVIATIONS.md` reports **`EXPECTED-FAIL (divergent)`**, not `FAIL → STOP`. Both Android runs hit misleading "a step was skipped" STOPs on their rewritten `testing-agent.md`. Verdicts are now `PASS` / `EXPECTED-FAIL (divergent)` / `FAIL (real)`.
- **`create-testing-agent.md` Stack Adaptation gains a native-mobile worked example.** Two Android projects re-derived the same set (divergent testing-agent, parallel N/A, Pattern B N/A-unless-Room, gate count depends on whether a JVM suite exists). Pre-answered as an *illustration, not an enforced profile* — keeps "adapt, don't dictate" while sparing the next Android/iOS project the re-derivation. Explicitly warns **not** to presume four gates (Fossify has a JVM suite → five; Venice Logger is suite-less → four).

### Added
- **`upgrade-kit.md` Step 1.6 — marker-vs-content reconciliation.** When the `KIT_VERSION` marker (Method A) is *ahead* of what the content corroborates (highest fingerprint match / git history), it's an **over-stamped marker** (a prior pass stamped without applying, or skipped a step — Fossify's marker read 5.5 while content was 5.4). Surfaced as a first-class STOP; the upgrade proceeds from the *reconciled* version, not the bare marker.
- **`upgrade-kit.md` Step 3 — sanctioned "wholesale-refresh + re-inject slots" strategy** for badly-trailing files (a v5.5→v5.15 jump found it far cleaner than replaying 10 per-release edits), with explicit slot-parity preconditions; named `refresh+reinject` in the plan table.
- **`upgrade-kit.md` Step 2.5 — two lints:** a `customize-token-leftover` lint (a filled slot whose body still contains the literal `[CUSTOMIZE]` token), and a **`docs/README.md` identity check** (confirm it's the kit README via a `## Quick Setup` signature, else treat kit-README edits as `surface-absent` — product READMEs collide at the same path constantly).
- **`MAINTAINING.md` — three release-author rules:** (10) migration-prompt anchors must pair an exact anchor with a role-based semantic fallback (a brittle "after the Modes block" broke on a hand-edited file); (11) every migration ships a verification grep-triplet, stack-aware for stack-dependent assertions; (12) fingerprint rows must be re-anchored onto stack-invariant, slot-free content.

### Deferred (not in this release)
- **Per-file `KIT_FILE_VERSION` stamp** (Personal Agent rec 2). Valuable for O(1) per-file classification + behind-vanilla detection, BUT it reverses the kit's documented "the bundle is one version; per-file version stamps are no longer used" decision (`KIT_CHANGELOG.md` intro + `feature-lifecycle.md` header). That reversal deserves its own deliberate decision rather than riding in on a robustness release — queued in `MAINTAINER_LOG.md`. Step 1.6 (over-stamped marker) was implemented WITHOUT depending on it (corroborates via fingerprint + git history).

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 1 anchor discipline + 3 re-anchored rows + v5.16 row; Step 1.6 marker reconciliation; Step 2.5 two lints; Step 3 refresh+reinject; Step 6 divergence-aware sanity)
- `docs/prompts/create-testing-agent.md` (native-mobile worked example in Stack Adaptation)
- `docs/upgrading/v5.14-to-v5.15.md` (Phase 3 anchor fallback — retrofit of rule 10)
- `MAINTAINING.md` (rules 10–12; maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.15.1 → 5.16)
- `docs/upgrading/v5.15.1-to-v5.16.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.15.1-to-v5.16.md`. Almost entirely refreshes of two tooling/prompt files (`upgrade-kit.md`, `create-testing-agent.md`) — wholesale-refresh if vanilla, surgical if hand-edited. No downstream lifecycle change; the win is that the *next* upgrade this project runs is more robust.

---

## [v5.15.1] — 2026-06-07

**Patch — fix a correctness bug that v5.15 itself introduced: the gate-count migration could corrupt a *correct* four-gate (suite-less) project.** Surfaced same-week by three downstream upgrade retros (Venice Logger + Personal Agent + a Fossify-based Android app, all on the v5.14→v5.15 path). v5.15's migration said "grep `code-quality-agent.md` for `four ... final gates` → fix to five" with **no suite-less guard** — but a suite-less project that legitimately runs **four** gates carries the *textually identical* string as a correct value, not the v5.14 wording bug. A literal run would have "fixed" a correct file into a wrong one, and the paired Phase 5 sanity grep (`no "four" remains`) would then false-fail on the right answer. This is the **same bug-class as P1.3, inverted** — and the deeper lesson is that any count-based bugfix/sanity grep over a *stack-dependent* value must branch on the stack signal, never assert one stack's value as universal.

### Changed
- **`docs/upgrading/v5.14-to-v5.15.md` (the migration) — gate-count bugfix is now suite-less-guarded.** Phase 1 determines the project's gate count (FIVE = has a standalone suite / FOUR = suite-less) **before** the bug check; the `four → five` fix and the "no four remains" sanity grep apply **only to five-gate projects**. For four-gate projects, "four" is the correct sanctioned count and is left untouched (the inverse sanity assertion holds). The migration now delivers the v5.15.1 patch level (stamps `5.15.1`); there is no separate v5.15→v5.15.1 hop.
- **`docs/prompts/feature-lifecycle.md` Phase 5.8 — the fold note was self-contradictory and is rewritten.** It previously said "runs **four** gates" but then "paste into **Gate #2's slot**" / "the other **four** gates remain" (implying five slots). Corrected: a suite-less project's four gates are Retrospective + Code Quality + Context Docs + Documentation, with **no Test-Suite gate block** at all; the build/sideload proof + any suite lesson **fold** to a project-designated landing (default: the Retrospective Gate's Gap A; or a project-named build gate). The fold target is now parameterized, not hard-coded to a non-existent "Gate #2 slot." Propagation one-liners in `AGENTS.md` / `CLAUDE_SNIPPET.md` / `README.md` aligned to match.
- **`MAINTAINING.md` self-drift step (8) — new rule:** count-based bugfix/sanity greps MUST be stack-aware. The bug and the legitimate value can be textually identical; branch on the machine-readable `Gate count:` line in `KIT_DEVIATIONS.md` before applying or asserting a count.

### Added
- **`docs/KIT_DEVIATIONS.md` — a machine-readable `Gate count:` section** (`five` default / `four (suite-less)`). Upgrades read this line to branch deterministically instead of re-deriving suite-less-ness from prose every time (downstream projects reported hand-deriving it across v5.10, v5.14, v5.15). This is the load-bearing flag that lets a gate-count migration tell a stale `four` apart from a correct `four`.
- **`docs/prompts/upgrade-kit.md` — v5.15.1 fingerprint row** (`feature-lifecycle.md` Phase 5.8 contains `no Test-Suite gate block`).

### Files touched
- `docs/upgrading/v5.14-to-v5.15.md` (suite-less guard on Phase 1 bug check, Phase 4 bugfix, Phase 5 sanity greps; stamps 5.15.1)
- `docs/prompts/feature-lifecycle.md` (Phase 5.8 fold note rewrite)
- `docs/AGENTS.md`, `docs/CLAUDE_SNIPPET.md`, `docs/README.md` (fold one-liners aligned)
- `docs/KIT_DEVIATIONS.md` (NEW machine-readable `Gate count:` section)
- `docs/prompts/upgrade-kit.md` (v5.15.1 fingerprint row)
- `MAINTAINING.md` (self-drift rule — count-greps must be stack-aware; maintainer-only)
- `docs/KIT_VERSION` (5.15 → 5.15.1)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`inline-edit` — **projects on v5.14 or earlier:** run the corrected `docs/upgrading/v5.14-to-v5.15.md`; it now lands you safely on v5.15.1 regardless of gate count (the `### Edits` below do NOT apply to you — the migration prompt owns the edits). **Projects already on v5.15** (stamped `5.15`): the bug never corrupted you if you upgraded carefully, but apply the `### Edits` below (four vanilla-wording refreshes + add the `Gate count:` section + re-stamp). No structural change.

### Edits
> Applies ONLY to a project already stamped `5.15`. All four wording edits are to **vanilla, slot-free** prose — if any target string is absent (the project rewrote that file/section), log `surface-absent` and skip it; the gate-count fold semantics are unchanged either way. Match by role (the named section heading) if the literal text drifted.

1. **`docs/AGENTS.md`** — under the `## The five gates (Phase 5.8 — no merge without all five)` heading.
   - **Find:** `> Stack-dependent: a project with **no standalone automated test suite** runs **four** — Gate #2 folds into the Phase 4.2 build/sideload proof and any suite lesson goes to the retro's Gap A. See `feature-lifecycle.md` Phase 5.8 for the fold rule. "Five" is the default; "four" is the sanctioned reduction for suite-less stacks.`
   - **Replace:** `> Stack-dependent: a project with **no standalone automated test suite** runs **four** (Retrospective + Code Quality + Context Docs + Documentation) — there is **no Test-Suite gate block**; the build/sideload proof + any suite lesson fold into the retro's Gap A (or a project-named build gate). See `feature-lifecycle.md` Phase 5.8 for the fold rule. "Five" is the default; "four" is the sanctioned reduction for suite-less stacks (set `Gate count: four (suite-less)` in `KIT_DEVIATIONS.md`).`

2. **`docs/CLAUDE_SNIPPET.md`** — the suite-less blockquote under the "five verbatim gate blocks" sentence.
   - **Find:** `> Suite-less stacks (native-mobile sideload, CLI build, data-app smoke run) run **four**: Gate #2 (Test-Suite Summary) folds into the Phase 4.2 build/sideload proof and any suite lesson goes to the retro's Gap A. See `feature-lifecycle.md` Phase 5.8.`
   - **Replace:** `> Suite-less stacks (native-mobile sideload, CLI build, data-app smoke run) run **four** (Retrospective + Code Quality + Context Docs + Documentation) — no Test-Suite gate block; the build/sideload proof + any suite lesson fold into the retro's Gap A (or a project-named build gate). See `feature-lifecycle.md` Phase 5.8; flag it with `Gate count: four (suite-less)` in `KIT_DEVIATIONS.md`.`

3. **`docs/README.md`** — the suite-less line inside the Phase-5 ASCII flow.
   - **Find:** `         (four on suite-less stacks — Gate #2 folds into the build/sideload proof).`
   - **Replace:** `         (four on suite-less stacks — no Test-Suite gate; build/sideload proof folds into Gap A).`

4. **`docs/prompts/feature-lifecycle.md`** — the Phase 5.8 `> **Gate count is stack-dependent …**` blockquote.
   - **Find:** the blockquote ending `… and record any suite-level lesson the missing runner would have carried under the Retrospective Gate's **Gap A**. This is a fold, not a skip — the other four gates remain mandatory, and the proof lines are still pasted verbatim. Do **not** scaffold an empty suite just to reach five, and do **not** drop the proof to justify four. (The kit's stamped count is "five"; "four" is the sanctioned reduction for suite-less stacks, recorded once in `docs/KIT_DEVIATIONS.md`.)`
   - **Replace:** the rewritten blockquote whose end-state reads `… runs **four** gates: Retrospective + Code Quality + Context Docs + Documentation. There is **no Test-Suite gate block** in the final paste …` and parameterizes the fold target (default Gap A; or a project-named build gate), ending `… set the machine-readable `Gate count: four (suite-less)` line in `docs/KIT_DEVIATIONS.md` so upgrades branch on it deterministically and never mistake a *correct* "four" for the historical v5.14 "four-gates" wording bug.)` (Copy the current vanilla blockquote from the new kit's `feature-lifecycle.md` Phase 5.8 verbatim — it is slot-free.)

5. **`docs/KIT_DEVIATIONS.md`** — add the machine-readable `## Gate count (machine-readable)` section (copy from the new kit's `docs/KIT_DEVIATIONS.md`) if absent. Default line: `Gate count: five (kit default — standalone automated suite present)`; suite-less projects set `four (suite-less)`.

6. **`docs/KIT_VERSION`** — re-stamp to `5.15.1`.

---

## [v5.15] — 2026-06-06

**Minor — operationalize stack-universality at the kit's birth points, make the gate count honestly stack-dependent, and harden the adopt/upgrade flows for non-root and pre-marker installs.** Driven by real cross-project adoption feedback: 8 projects brought to v5.14 in one batch (native Android, Tauri desktop, Capacitor mobile, Python/Streamlit, Next.js, Vite SPA) — see `retrospectives/2026-06-06-cross-project-v5.14/`. The recurring signal: **6/8 projects are not web apps, yet the kit's templates default to "a web app driven through a browser"** and every non-web project had to hand-remap the testing-agent, the parallel-testing axes, and the auth/login slots. The kit already *preached* stack-portability in prose (`feature-lifecycle.md` Phase 4's "universal concept, browser-default primitives") but never *operationalized* it at the two places a project's testing setup is born — `create-testing-agent.md` and the adoption flow. v5.15 closes that gap with a **Stack Adaptation** layer (browser = default, not assumption) rather than a rigid stack-profile taxonomy — keeping the kit's "adapt, don't dictate" principle. It also fixes a genuine v5.14 vanilla inconsistency (a leftover "four gates") and acknowledges that suite-less stacks legitimately run **four** gates, not five.

**No new phases, sub-agents, or restructuring** — every change is guidance/wording added to existing prompts and templates.

### Added
- **`create-testing-agent.md` — a "Stack Adaptation" section** (read before generating). Declares the browser vocabulary a *default*, classifies the testing surface (`web` / `mobile-native` / `desktop` / `python-data-app` / `cli-library`), and gives a substitution table (how you drive it · what "one step" is · how you observe · pass-evidence · `dev_server`/`credentials_source` equivalents · parallel-isolation axis). Steps 2 and 5 gained pointers so a generator entering mid-file can't miss it. The existing browser-shaped body is untouched — it's now explicitly the web default to substitute, not a checklist to force.
- **`README.md` Quick Setup step 0.5 — "Classify the stack and pick the kit root"** (do first; it drives testing-agent shape, which slots are N/A, and the gate count) **and step 6 — "Bootstrap a minimal test harness"** for fresh projects that have nothing for the tests-first lifecycle to run on day one (with an explicit "skip if the stack has no automated suite — run four gates" branch).
- **`feature-lifecycle.md` Phase 5.8 — explicit stack-dependent gate-count rule.** Five gates with a standalone automated test suite; **four** without — Gate #2 (Test-Suite Summary) *folds* into the Phase 4.2 build/sideload proof and any suite lesson goes to the Retrospective Gate's Gap A. A fold, not a skip: the proof lines are still pasted, the other four gates remain mandatory, and the reduction is recorded once in `KIT_DEVIATIONS.md`. Propagated as one-line notes to `AGENTS.md`, `CLAUDE_SNIPPET.md`, `README.md`.
- **`upgrade-kit.md` — "Marker backfill" subsection (Step 2.5)** so a pre-v5.4 / markerless install comes out of the upgrade marker-safe: preserved customizations that don't map to a vanilla slot get wrapped in fresh `project-<name>` markers and recorded in `KIT_DEVIATIONS.md`, making the *next* upgrade mechanical instead of another fragile anchor hunt.
- **`upgrade-kit.md` — kit-root detection (Inputs §3)**: locate the kit by finding `docs/KIT_VERSION`, not by assuming the repo root, so monorepo (`agent/docs/`) and subfolder installs work; treat that dir as "root" for every path.

### Changed
- **`.claude/agents/code-quality-agent.md` — BUGFIX:** one leftover `"NOT one of the four Phase 5.8 final gates"` → `"five"`. The v5.13→v5.14 migration updated the two `Gate #2 → Gate #3` references but missed this third spot; downstream projects that matched vanilla preserved the inconsistency. (Caught independently by two projects.)
- **`.claude/commands/kit-adopt.md`** (maintainer-local launcher) — step 1 now leads with stack-classification + kit-root choice; new step 8 bootstraps a test harness.

### Files touched
- `docs/prompts/create-testing-agent.md` (Stack Adaptation section + Step 2/Step 5 pointers)
- `docs/prompts/feature-lifecycle.md` (Phase 5.8 stack-dependent gate-count note)
- `docs/README.md` (Quick Setup 0.5 stack+root, step 6 bootstrap harness, five/four-gate note)
- `docs/AGENTS.md` (four-on-suite-less note under "The five gates")
- `docs/CLAUDE_SNIPPET.md` (four-on-suite-less note under Post-Feature Gates)
- `docs/prompts/upgrade-kit.md` (Inputs §3 kit-root detection; Step 2.5 Marker-backfill subsection; Step 1 fingerprint row for v5.15)
- `.claude/agents/code-quality-agent.md` (four → five bugfix)
- `.claude/commands/kit-adopt.md` (maintainer-local — stack-first + bootstrap step)
- `docs/KIT_VERSION` (5.14 → 5.15)
- `docs/upgrading/v5.14-to-v5.15.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.14-to-v5.15.md`. Most edits are slot-free additive guidance, but the gate-count change touches `feature-lifecycle.md` Phase 5.8 and four propagation surfaces, and the `code-quality-agent.md` bugfix must reach projects that matched buggy vanilla. The migration wholesale-refreshes vanilla files, applies the four→five fix wherever the buggy string survives, and is **especially relevant to non-web projects** — it points them at the new Stack Adaptation section and lets them record a sanctioned **four-gate** reduction in `KIT_DEVIATIONS.md` (retiring any ad-hoc local note that the lifecycle "doesn't fit our stack").

---

## [v5.14] — 2026-06-06

**Minor — the Phase 5 self-improvement retro becomes an unconditional, merge-blocking Retrospective Gate (now the FIRST of five Phase 5.8 gates), and gains a third bucket: Gap C — implementation/code lessons.** Phase 5.1's Gap A (unit/integration lessons via `test-suite-retro.md`) and Gap B (testing-agent lessons via `testing-retro.md`) were **conditional** ("If manual testing revealed bugs or issues…") and were NOT among the gates that block merge at Phase 5.8. In practice whole epics shipped with their manual-test misses un-retro'd: the bugs got fixed, but the durable lessons never got written — because the step was skippable, unlike the Documentation Gate, which always runs precisely *because* it's an enforced gate. v5.14 makes the retro **unconditional** (a sweep over every manual-test finding ∪ every `fix(` commit of the phase, with a recorded per-finding decision) and enforces it with a verbatim `RETROSPECTIVE GATE:` block in the same gate-block idiom as the Documentation Gate. The gate count rises **four → five** everywhere, with the Retrospective Gate as Gate #1 (chronologically earliest). **No new sub-agent** — the main agent (which already owns all of Phase 5) authors the block. The **"deliberately NOT lessoned" accounting line** is the crucial design choice: a polish-only finding becomes a NAMED line with a reason, not a silent skip — which lets teams keep the gate on a polish-only epic instead of (correctly, under the old design) wanting to skip it.

**The Gap C addition closes a long-standing asymmetry:** the *test* ecosystem had two self-improving, read-before-you-work guides (`Unit_Test_Writing_Guide.md` for unit/integration, `Testing_Patterns.md` for browser tests), while the *code* ecosystem had **zero** — implementation lessons scattered into ADRs, per-feature plan docs, and test-miss side-notes, and were never read cross-feature before the next implementation. v5.14 adds **`docs/context/Implementation_Patterns.md`** — the code-side mirror of the test guide (universal anti-patterns I1–In + an append-only `## Project Lessons` section) — and wires Phase 3 to **read it at the IMPLEMENT step of every TDD cycle**, the same way the RED step reads the test guide. Gap C of the Phase 5.1 autopsy captures durable code lessons (recurring bug-class, project gotcha, architectural anti-pattern) into it, with the same Tier-1/2/3 propagation as Gap A.

### Added
- **`docs/context/Implementation_Patterns.md` (NEW seed guide)** — universal implementation anti-patterns (I1–I11: silent error-swallowing, unvalidated boundaries, drifting duplication, shared mutable state, primitive obsession, missing idempotency, unbounded resources, implicit time/order/locale assumptions, inconsistent partial-failure state, symptom-at-wrong-layer, breaking shared contracts) + an append-only `## Project Lessons` section (empty seed, appended only by the Phase 5.1 Gap C autopsy). Deliberately stack-agnostic — applies to web/CLI/library/pipeline/mobile alike.
- **`feature-lifecycle.md` Phase 5.1 Gap C — implementation/code-lesson autopsy.** For each finding, name the *fault-class* (not "bug in X") and decide tier propagation into `Implementation_Patterns.md`: Tier 1 one-off (named on the gate, no guide entry), Tier 2 project lesson (appended), Tier 3 universal anti-pattern (proposed, awaits user approval). New `RETROSPECTIVE GATE:` line: `Gap C — implementation/code lessons: [guide entries added | none because ___]`.
- **`feature-lifecycle.md` Phase 3 reads `Implementation_Patterns.md` before writing code** — added at the IMPLEMENT step of the 3.3 TDD cycle (and to the orchestrator-mode Phase 3 sub-agent input contract), mirroring the existing test-guide read at the RED step.
- **Gap C lands in all THREE retro-running flows (retro-parity).** Beyond the lifecycle, the other two flows that run the self-improvement retro get the same Gap C:
  - **`bugfix.md` (now v4)** — Phase 2.4 adds a third retro, **Gap C (implementation/code lesson)**, run *always* (a bug fix is the richest source of code lessons): capture the fault-class named in Phase 2.2 into `Implementation_Patterns.md` with the same Tier 1/2/3 propagation as Gap A. Phase 2.2 reads the code guide before fixing.
  - **`reconcile-change.md` (the ad-hoc reverse-lifecycle mirror)** — a new Phase 2.1 "Lessons learned (reverse-retro — lightweight)" with all three gaps (A test / B browser / C code), Phase 2 reads the code guide too, and the `RECONCILE GATE` gains a `Lessons:` line; the `/reconcile` wrapper summary matches.
  - **Rule going forward (encoded as a mandatory `MAINTAINING.md` "retro-parity check" step): any kit change to the retro / lessons / Gap machinery must land in all three flows — `feature-lifecycle.md`, `bugfix.md`, and `reconcile-change.md` — scaled to each flow's weight.** `Implementation_Patterns.md` is appended by the Gap C step of all three.

### Changed
- **`feature-lifecycle.md` Phase 5.1 — retro is now UNCONDITIONAL.** The "If manual testing revealed bugs or issues" trigger is replaced by an unconditional sweep: enumerate the union of every manual-test finding (incl. ABORTs routed in as bugs) and every `fix(` commit of the phase (`git log --grep="^fix(" <phase-2-end-commit>..HEAD`), then make a recorded decision per finding — close it via Gap A and/or Gap B, or name it on the gate's "deliberately NOT lessoned" line. The existing Gap A / Gap B autopsy content is unchanged beneath the new framing.
- **`feature-lifecycle.md` Phase 5.1 — new verbatim `RETROSPECTIVE GATE:` block (§5.1 Gate Output).** Enumerates, for the whole epic: Gap A (unit-guide lessons + `docs/test-suite-misses.md` rows, or "none needed because ___"), Gap B (testing-agent Miss Log entries + `docs/context/Testing_Patterns.md` additions, or "none needed because ___"), planning/process (plan-doc retrospective, or "none"), Tier-3 (skill-universal) proposals awaiting user approval (list or none), and findings deliberately NOT lessoned (each NAMED with a UX-polish / forward-scope / out-of-scope reason). A zero-finding run still prints the block (`Findings swept: 0`, all lines `none`). Includes an accounting rule: closed + named must equal swept.
- **`feature-lifecycle.md` Phase 5.8 — Retrospective Gate added as the FIRST merge-blocking gate;** count raised four → five in the 5.8 header + ordered list, the 5.9 Phase-5 commit-body confirmation, the Phase Commit Discipline Phase-5 row, the Phase 3.5.2 "not one of the N final gates" note, and the appendix flow diagram (inline `Gate #N` labels renumbered, test-suite re-run shifts #1→#2, code-quality #2→#3, context-docs #3→#4, docs-auditor #4→#5).
- **Propagation docs refreshed to five gates** — `docs/AGENTS.md` (flow line, sub-agent gate-number column, "Gates #1/#2 aren't sub-agents" note, "The five gates" section), `docs/CLAUDE_SNIPPET.md` (Post-Feature Gates section + Pre-Test "not one of the N" note + gate-number cell), `docs/README.md` (feature-lifecycle blurb, Phase-5 flow diagram, "Five gates total" list), and `.claude/agents/code-quality-agent.md` (its block is now Gate #3 of five).

### Files touched
- `docs/prompts/feature-lifecycle.md` (Phase 5.1 unconditional sweep + `RETROSPECTIVE GATE:` block + Gap C autopsy; Phase 3.3 + Phase 3.0 input contract read `Implementation_Patterns.md`; Phase 5.8 first-gate addition + count; Phase 3.5.2, 5.4, 5.9, Phase Commit Discipline row, appendix diagram renumber)
- `docs/context/Implementation_Patterns.md` (NEW seed guide — code-side mirror of the test guide)
- `docs/prompts/bugfix.md` (now v4 — Phase 2.2 reads the code guide; Phase 2.4 adds Gap C retro, always-run, Tier 1/2/3; header + skills-list updated)
- `docs/prompts/reconcile-change.md` (Phase 2 reads the code guide; NEW Phase 2.1 lessons-learned reverse-retro with Gap A/B/C; `RECONCILE GATE` gains a `Lessons:` line)
- `.claude/commands/reconcile.md` (wrapper summary mentions lessons capture)
- `docs/AGENTS.md` (flow line + sub-agent table gate numbers + "five gates" section + Gap C in gate-1 description)
- `docs/CLAUDE_SNIPPET.md` (Post-Feature Gates five-gate list + sub-agent table + Pre-Test note + Gap C)
- `docs/README.md` (lifecycle blurb **v4 → v5 feature-tier label** + Phase-5 flow diagram + five-gate list + Gap C)
- `.claude/agents/code-quality-agent.md` (Gate #2 → Gate #3 of five, two spots)
- `docs/prompts/upgrade-kit.md` (Step 1 fingerprint rows for v5.12 / v5.13 / v5.14 — table was frozen at v5.11)
- `MAINTAINING.md` (release-author rule — NEW mandatory "reconcile-mirror check" step; maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.13 → 5.14)
- `docs/upgrading/v5.13-to-v5.14.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.13-to-v5.14.md`. The change lands in `feature-lifecycle.md` Phase 5 + Phase 3 (slot-free regions — applies to vanilla and slot-customized projects alike), a **new context guide** (`Implementation_Patterns.md`), four propagation docs, one agent file, and three `upgrade-kit.md` fingerprint rows. The migration drops in the new guide (or, if the project already has its own code/implementation guide, points Gap C at that one instead), wholesale-refreshes any vanilla file, and applies the Phase 5.1/5.8/Gap-C + gate-count edits around customizations otherwise. **Projects that already carry a local "mandatory retrospective gate" entry in `KIT_DEVIATIONS.md` can RETIRE that deviation after upgrading — it is now vanilla** (the migration prompts for this explicitly).

---

## [v5.13] — 2026-06-04

**Minor — generalized propagation safety net + behind-vanilla detection (the v5.12-deferred convergent finding).** Both v5.11 downstream upgrades independently ended with a **stale `AGENTS.md`** because migration prompts edit the agents/lifecycle they're written around but skip the release's *propagation* docs (`AGENTS.md`/`CLAUDE_SNIPPET.md`/`README.md`) — the same defect class v5.11 fixed for `upgrade-kit.md`, but that safety net only covered one file. v5.13 generalizes it to **every** file the release's CHANGELOG Files-touched lists, and adds a symmetric "behind-vanilla" detector for drift earlier upgrades left behind. All tooling (`upgrade-kit.md`); no lifecycle/agent/context change. **Framing is deliberately universal:** the kit tells the in-project agent *that* a file changed upstream; the agent reconciles *how it applies to its own project* (vanilla → refresh, structurally-divergent → skip, slotted/hand-edited → merge or surface) — the same change lands differently in a Next.js app vs a Python pipeline vs a native-Android project.

### Added
- **Post-migration completeness reconciliation in `upgrade-kit.md` Step 4** (generalizes the v5.11 tooling-file net). After a migration prompt finishes, diff the release's full CHANGELOG `### Files touched` against what the prompt actually edited; for each remaining at-risk file (propagation docs especially), the in-project agent classifies it against ITS OWN state (vanilla / structurally-divergent / slotted-hand-edited) and applies, skips, or surfaces accordingly. Closes the stale-`AGENTS.md` class for all future releases — and the v5.12→v5.13 migration runs it as a one-time backfill to repair the existing stale propagation files.
- **Behind-vanilla detection in `upgrade-kit.md` Step 2.5** (symmetric to superset detection). A cheap inventory-time full-file diff vs new vanilla surfaces lines where the project trails kit-default content that NO current-upgrade step targets (it missed an earlier release's edit — e.g. a pre-v5.6 `CHANGELOG.md`-at-kit-root reference). Presented as an **opt-in cleanup list**, never auto-applied (some divergences are intentional). Lets the in-project agent catch its own historical drift on its own terms.

### Changed
- **`MAINTAINING.md` "How to add a release" step 6** — the v5.11 "migration prompts must prescribe `upgrade-kit.md` refresh" rule is now backed by the generalized Step 4 reconciliation (which covers all propagation files automatically); the author-side rule is retained as belt-and-braces, with a pointer to the generalized net.

### Files touched
- `docs/prompts/upgrade-kit.md` (Step 4 generalized post-migration completeness reconciliation; Step 2.5 behind-vanilla detection)
- `MAINTAINING.md` (release-author note — maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.12 → 5.13)
- `docs/upgrading/v5.12-to-v5.13.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.12-to-v5.13.md`. Wholesale-refreshes `upgrade-kit.md` if vanilla (carries both new sections). Crucially, it runs the **new reconciliation as a one-time backfill**: it walks the CHANGELOG Files-touched for v5.9–v5.12 against the project's actual files and repairs any propagation doc (notably `AGENTS.md`) left stale by the earlier migrations — so a project that already upgraded to v5.11/v5.12 gets its stale propagation files fixed here. Behind-vanilla candidates are offered as opt-in cleanup. The in-project agent decides every application against its own customization state — the migration guides, it does not prescribe.

---

## [v5.12] — 2026-06-04

**Minor — parallel-testing adaptiveness + two convergent-retro fixes.** Validated by the first two real v5.11 upgrades (CoffeeScribe = Next/Supabase/tiered-auth; a vanilla-TS-SPA = no-auth/JSON): both completed, and v5.11's fixes proved out in the wild (the vanilla heuristic correctly passed a 3-commit `upgrade-kit.md`; the stale-fact category applied 2 corrections on first run; superset preservation kept Pattern B intact). The retros surfaced three issues this release closes. **Deferred to v5.13** (logged): generalizing the post-migration safety net from `upgrade-kit.md` to ALL propagation files (both projects ended up with a stale `AGENTS.md`), and a symmetric "behind-vanilla" detector.

### Changed
- **`feature-lifecycle.md` §4.1p — ADAPT, don't dictate (user feedback).** The concurrency `N` is now explicitly **derived at runtime** from the host machine + project, never a fixed number. The cap is reframed as *your machine's comfortable ceiling* with hardware examples (16 GB → 1–2, 32 GB → 3, 64 GB+/Apple-silicon-Max → 4–6), not a kit default. Numbers are "calibration, not commandments — the memory probe is the source of truth." New **"Surface the recommendation; the machine's owner decides"** step codifies the *"run N / fewer / sequential?"* quick-pick (a project did this by instinct on first real use) as the expected behaviour, skippable only when a standing ceiling is recorded in the `[CUSTOMIZE]` slot; plus self-correction (drop N if contention ABORTs cluster). Credential pool wording loosened to "however your project supplies them — env vars, tier accounts, fixtures."
- **Rule-numbering CANONICAL resolution fixed (CoffeeScribe retro — confirmed contradiction).** v5.10/v5.11 shipped contradictory guidance: the `v5.9-to-v5.10` migration said "append the kit's parallel rule at 24, keep Pattern B at 23," while `db-precondition.md` + `v5.10-to-v5.11` said the reverse. Now consistent everywhere on the canonical rule (`upgrade-kit.md` Step 2.5): **kit-origin rules keep their vanilla number; project extensions move to the tail.** So parallel isolation = vanilla Rule 23, Pattern B = Rule 24, identically across every touchpoint — which keeps the kit's rule at the same number on every project so future "Rule N" edits land correctly. A project upgraded under the old reversed guidance (parallel=24/Pattern B=23) swaps on next touch.
- **`upgrade-kit.md` Step 1 fingerprint table extended through head (both retros).** Was frozen at v5.8; added rows for v5.9 (`reconcile-change.md`/`.claude/commands/reconcile.md`), v5.10 (`Parallel-Run Mode`), v5.11 (`Marker-matching discipline`). A marker-less legacy project can now be fingerprinted to the true latest version.

### Files touched
- `docs/prompts/feature-lifecycle.md` (§4.1p adapt-don't-dictate rewrite — Step 2 intro + slot CUSTOMIZE header + cap reframing + new surface-to-user step)
- `docs/prompts/upgrade-kit.md` (Step 2.5 canonical collision direction; Step 1 fingerprint rows v5.9–v5.11)
- `docs/upgrading/v5.9-to-v5.10.md` (collision step corrected to canonical: parallel=23, Pattern B=24)
- `docs/patterns/db-precondition.md` (already canonical from v5.11 — unchanged this release; referenced for consistency)
- `docs/KIT_VERSION` (5.11 → 5.12)
- `docs/upgrading/v5.11-to-v5.12.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.11-to-v5.12.md`. The §4.1p adaptive guidance lands partly **outside** the `parallel-testing-capacity` slot (Step 2 intro + the surface-to-user step), so it propagates even to projects that already filled the slot; the in-slot template wording is for future fills only. The tooling/migration files (`upgrade-kit.md`, `v5.9-to-v5.10.md`) wholesale-refresh if vanilla. The migration also **detects the old reversed rule-numbering** (parallel=24/Pattern B=23) and offers the canonical swap — the same fix the standalone CoffeeScribe prompt does, in case a project hasn't run it.

**Minor — upgrade-system robustness (two convergent downstream retros).** Two independent v5.7→v5.9 upgrades — CoffeeScribe (Next/Supabase/tiered-auth) and a vanilla-TS-SPA (no-auth/JSON-files) — converged on the same `upgrade-kit.md` defects. This release fixes them plus three more findings. Both upgrades *succeeded* (the agents recovered), so these were latent traps, not active breakage — except the stale-tooling one, which is a live defect (projects upgraded via the v5.7→v5.8 migration prompt carry a `upgrade-kit.md` missing 3 of 4 v5.8 improvements). Nothing here changes the forward lifecycle; it's all upgrade tooling + the Pattern B opt-in.

### Changed
- **`upgrade-kit.md` self-referential vanilla heuristic corrected (the headline bug — hit by BOTH retros).** The old "exactly one commit in history ⇒ vanilla" sub-check false-negatived for *every* project past its first upgrade (each upgrade commits the file). Now **two authoritative checks** decide vanilla — content-diff vs prior-vanilla + zero real slot markers — and **commit count is explicitly informational only, never a gate.** Both downstream projects tripped the old gate (2 legit commits each) and recovered only by luck.
- **`KIT:SLOT-BEGIN` marker-matching discipline added (hit by BOTH retros).** A new callout at the top of Step 2.5 defines the canonical real-marker grep — `grep -nE '(<!-- KIT:SLOT-BEGIN|# KIT:SLOT-BEGIN) [a-z][a-z0-9-]* (-->)?'` — that excludes the prose documenting the convention and the `<name>` placeholder. The bare `grep -c "KIT:SLOT-BEGIN"` returns false hits on `upgrade-kit.md`/`MAINTAINING.md`/`AGENTS.md` (which all *mention* the token). Step 0.5 and the Step 4 self-ref check now reference it.
- **Migration-release tooling-file safety net (the live defect — SPA retro).** Step 4's substantive-release path now MANDATES, post-migration, the self-referential `upgrade-kit.md` wholesale-refresh-if-vanilla whenever the release's Files-touched lists `upgrade-kit.md` — so a migration prompt that forgets its own tooling file no longer strands projects on stale tooling.
- **"Project ahead of the kit" superset detection (CoffeeScribe retro).** New Step 2.5 subsection + a `superset-preserved` Step 4 outcome: when a project's region already contains (a superset of) what a release adds, the kit's addition is skipped as redundant and the project's extra content is preserved + recorded as an extension — instead of the old "remove the now-duplicate" guidance that would *downgrade* the project. Includes the **numbered-list-collision rule** (a pre-existing project `Rule 23` and the kit's new `Rule 23` both survive — one becomes `Rule 24`).
- **Step 4.5 "stale-fact correction" CLAUDE.md edit category (CoffeeScribe retro).** A `CLAUDE.md` sentence an upgrade renders factually false (e.g. "ABORT is a fourth verdict" after v5.8 made it six) is mechanical-with-report, not a judgment STOP.
- **Pattern B no-SQL auto-skip + rule-number fix in `docs/patterns/db-precondition.md` (SPA retro + v5.10 collision).** New "When Pattern B does NOT apply" section: pre-detect absence of any DB-execute MCP tool / SQL backend and default-skip with a `KIT_DEVIATIONS.md` note (no opt-in round-trip for JSON/file/localStorage projects). Pattern B's documented rule number moved 23 → **24** because v5.10's parallel-isolation rule now occupies vanilla Rule 23 — the doc now says "append at the next free number," not a hardcoded 23.
- **`docs/upgrading/v5.9-to-v5.10.md`** — Phase 2 testing-agent merge gains the explicit Rule-23 collision/superset step (so CoffeeScribe's pre-existing Pattern B Rule 23 survives its v5.10 upgrade).
- **`MAINTAINING.md` "How to add a release" step 6** — three new author-side rules: migration prompts that touch `upgrade-kit.md` must prescribe its refresh; `archive/v<prior>/` diffs must `test -f` then fall back to `git show`; numbered-list additions must carry the collision step.
- **`feature-lifecycle.md` §4.1p universality scoping** (carried in v5.11; refines the v5.10 parallel-testing feature before it propagates). Added a "Scope & portability" note making explicit that the *concept* (capacity math + parallel-safety classification) is stack-agnostic while the *primitives* (agent-browser session, dev-server port, test login) are browser-testing defaults. Non-web projects with a structurally-divergent testing-agent are told to map the three isolation axes to their stack (`pytest -n` + temp DB, `go test` separate build dirs, isolated simulators) or keep Phase 4 sequential. Closes a "reads as web-only" gap caught in a universality audit.

### Files touched
- `docs/prompts/feature-lifecycle.md` (§4.1p "Scope & portability" universality note)
- `docs/prompts/upgrade-kit.md` (Step 0.5 marker grep; Step 2.5 marker-matching discipline + superset detection + inventory category; Step 4 self-ref vanilla heuristic rewrite + post-migration tooling safety net + `superset-preserved` outcome; Step 4.5 stale-fact category)
- `docs/patterns/db-precondition.md` (no-SQL auto-skip section; Pattern B rule 23 → 24 + next-free-number guidance)
- `docs/upgrading/v5.9-to-v5.10.md` (Rule-23 collision/superset step in Phase 2)
- `MAINTAINING.md` (release-author rules — maintainer-only, not shipped)
- `docs/KIT_VERSION` (5.10 → 5.11)
- `docs/upgrading/v5.10-to-v5.11.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.10-to-v5.11.md`. The shipped changes are to `upgrade-kit.md` and `db-precondition.md` (both typically vanilla in adopting projects). The migration wholesale-refreshes `upgrade-kit.md` if vanilla (this release is itself the first exemplar of the new "migrations refresh their tooling file" rule), refreshes `db-precondition.md` if present and unmodified, and bumps `KIT_VERSION`. A project that hand-edited either file falls through to slot-aware/`conflict` handling. **This release improves how FUTURE and re-run upgrades behave; a project already on v5.10 takes it as a tooling refresh.**

---

## [v5.10] — 2026-06-04

**Minor — capability-gated parallel browser testing.** On a capable machine, Phase 4 can now run multiple testing agents **concurrently** instead of back-to-back, cutting verification wall-clock. This is the kit's one sanctioned parallel sub-agent dispatch — safe because testing agents are read-only (edit no code) and each is fully isolated on its own **browser session + dev-server port + test user**. Two correctness guards make it sound: (1) the testing-agent's RAM-hygiene and teardown were global `pkill`/`close --all` — fine sequentially, fatal in parallel (an agent's teardown would kill its siblings) — so both are now **mode-scoped**; (2) the real clash isn't a shared database, it's **shared user identity** — two agents acting as the same user at once corrupt each other — so concurrency is capped by the count of distinct test logins and the orchestrator hands each agent its own. Default behaviour is unchanged: plans are sequential unless explicitly marked `parallel_safe`, and a single-login project never parallelizes.

### Added
- **`§0.5 Parallel-Run Mode` in `.claude/agents/testing-agent.md`** (v3.1 → v3.2). Three isolation rules when invoked with a `SESSION` + `PORT` + assigned user: browser isolation via `AGENT_BROWSER_SESSION`/`--session` on every call; server isolation to the assigned port only; identity isolation to the assigned test user only (borrowing another agent's user → `BLOCKED_NEEDS_FIXTURE`). Plus **Rule 23** restating the lane discipline.
- **Mode-scoped RAM Hygiene (§0) and Teardown (§8) in `testing-agent.md`.** Solo mode keeps the global sweep. Parallel mode: skip the global `agent-browser|chromium` lingering check (siblings own those), and on teardown close only your session (`agent-browser close`, never `--all`) and free only your port (`lsof -ti tcp:{PORT} | xargs kill`), never global chromium `pkill`. The orchestrator owns one pre-batch and one post-batch global sweep.
- **`4.1p Parallel testing mode` in `docs/prompts/feature-lifecycle.md`.** Capability gate + concurrency math: `N = min(memory_allowed, eligible_plan_count, cap)`, with the distinct-login count capping **only `distinct-login` plans** — `read-only` and `isolated-backend` plans (and therefore no-auth projects: CLI tools, public/read-only apps, single-user local apps) are bounded by memory alone, not logins. A `[CUSTOMIZE] parallel-testing-capacity` slot holds per-agent GB budget (default 3), cap (default 4), and the project test-user pool (or `no auth — login cap N/A`). Distinct-user allocation (same-exclusive-user plans serialized across batches), per-agent session/port assignment, concurrent dispatch with `Run mode: parallel` + `SESSION:` + `PORT:` + `Assigned test user:` fields, per-agent gate collection under the Sub-Agent Supervision Protocol, and pre-/post-batch global sweeps. Falls back to the existing sequential flow whenever `N < 2`.
- **`parallel_safe` + `parallel_isolation` plan-config fields and a classification decision guide in `docs/prompts/create-testing-agent.md`** (v3 → v3.1). The guide walks: read-only → `true`/read-only; writes confined to the acting user's own data → `true`/distinct-login; per-port isolated DB → `true`/isolated-backend; writes to shared exclusive state with no isolation → `false` (sequential). New Quality Checklist item; default is `false` (positive claim required to flip).
- **`docs/upgrading/v5.9-to-v5.10.md`** — migration prompt (slot-aware: these are customizable files; preserve project hand-edits and existing test plans, which stay sequential until marked).

### Changed
- **`feature-lifecycle.md` sequential-only rules clarified** (Phase 0 orchestrator rules, Phase 3.1 Resource Management bullet, Appendix). "Sequential only" now explicitly means **file-mutating** Phase 2/3/5 sub-agents; read-only Phase 4 testing agents are the sanctioned exception when Phase 4.1's capability gate allows.
- **Version stamps:** `testing-agent.md` v3.1 → v3.2; `create-testing-agent.md` v3 → v3.1 (internal agent versions; user-facing kit version is v5.10).
- **`docs/README.md`** (create-testing-agent line v2 → v3.1 + parallel note), **`docs/AGENTS.md`** (testing-agent Output cell: full verdict set + parallel capability), **`docs/CLAUDE_SNIPPET.md`** (create-testing-agent row: parallel_safe note).

### Files touched
- `.claude/agents/testing-agent.md` (header v3.1 → v3.2 + new blockquote; description frontmatter; §0 RAM Hygiene solo/parallel split; new §0.5 Parallel-Run Mode; §1 pre-flight port note; §1 step 6 session-scoped close; §8 Teardown solo/parallel split; Rule 23)
- `docs/prompts/feature-lifecycle.md` (Phase 0 sequential-only rule; Phase 3.1 parallel-agent bullet; new §4.1p Parallel testing mode with `[CUSTOMIZE] parallel-testing-capacity` slot; Appendix sequential note)
- `docs/prompts/create-testing-agent.md` (header v3 → v3.1; Step 5 config `parallel_safe`/`parallel_isolation`; classification decision guide; Quality Checklist item)
- `docs/KIT_VERSION` (5.9 → 5.10)
- `docs/README.md`, `docs/AGENTS.md`, `docs/CLAUDE_SNIPPET.md` (propagation)
- `docs/upgrading/v5.9-to-v5.10.md` (NEW)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.9-to-v5.10.md`. The edits land in customizable, slot-bearing files (`testing-agent.md`, `feature-lifecycle.md`, `create-testing-agent.md`), so they can't be blind Find/Replace — a project may have hand-edited RAM-hygiene/teardown or carry the `[CUSTOMIZE]` resource-management slot. The prompt does a slot-aware merge, adds the new `[CUSTOMIZE] parallel-testing-capacity` slot (prompting for the project's test-user pool), and leaves all existing test plans `parallel_safe`-unset (→ sequential) so behaviour is unchanged until the user marks plans. A project that doesn't want parallel testing can take the additive isolation safety net (which only activates in parallel mode) and simply never mark a plan.

---

## [v5.9] — 2026-06-04

**Minor — ad-hoc reconciliation: a reverse-lifecycle prompt + the kit's first project-level slash command.** Driven by a maintainer need: not all work is an epic. Sometimes you open the project, notice something while using the app, and just fix it — a UI tweak, a few things on one page, or a small feature — with no PRD, no ADR, and no tests-first. The forward lifecycle (`feature-lifecycle.md`) is overkill for that, but the work still needs catching up: tests TDD would have written, any architecture/PRD/ADR doc it touches, and every relevant context + user-facing doc. v5.9 adds a standalone prompt that does exactly that pass — the tail of Phase 5, decoupled from the epic and runnable any time after unplanned work — plus a `/reconcile` command to launch it. Purely additive: no existing phase, agent, or context file changes.

### Added
- **`docs/prompts/reconcile-change.md`** (NEW). Standalone *reverse-lifecycle* prompt. Six phases: (1) establish the change set from `git` + this chat, with one confirmation stop; (2) **backfill tests (reverse-TDD)** — assert *intended* behaviour (not observed), so a red test surfaces a real defect in the ad-hoc change rather than rubber-stamping it; reads `Unit_Test_Writing_Guide.md` first; handles the no-unit-surface (pure UI) case honestly; (3) **architecture / PRD / ADR — conditional**, updates an existing feature doc if the change touches one, offers a lightweight note for new architectural behaviour, skips small self-contained tweaks with a logged reason; (4) **context docs sync** — runs `context-docs-agent`'s Execution Sequence inline (mandatory changelog entry, evidence-backed skips); (5) **user-facing docs sync** — walks `docs-auditor-agent`'s Help Center / FAQ / Tour / Legal decision trees inline; (6) composite `RECONCILE GATE` + commit. Runs **inline (no sub-agent dispatch)** to match single-chat working style, and reuses the two doc-agents **by reference** so per-project `[CUSTOMIZE]` work flows through automatically.
- **`.claude/commands/reconcile.md`** (NEW — and the **first file the kit ships under `.claude/commands/`**). Thin project-level slash command: switches to `opus`, reads `docs/prompts/reconcile-change.md`, and follows it against the change just made. Resolves the prompt by project-relative path, so `/reconcile` works in whatever kit-equipped project it's run in.
- **`docs/upgrading/v5.8-to-v5.9.md`** — migration prompt. Purely-additive path: state audit (confirm v5.8, confirm neither new file exists, note whether `.claude/commands/` exists yet), copy the two files (creating `.claude/commands/` if absent), bump `KIT_VERSION`. Two stop points; `sonnet`-sufficient.

### Changed
- **`docs/README.md`** — §1 copy-folders tree now shows `.claude/commands/` (with `reconcile.md`); §5 "Start using the prompts" table gains a `reconcile-change.md` row; "Universal (use as-is)" list gains a `reconcile-change.md` bullet; "Folder Purpose Reference" table gains a `.claude/commands/` row.
- **`docs/AGENTS.md`** — the prompt-map table gains a `reconcile-change.md` row.
- **`docs/CLAUDE_SNIPPET.md`** — Workflow Prompts table gains a `reconcile-change.md` row; the standalone-prompt usage section notes `/reconcile` and that it runs inline (not dispatched).

### Files touched
- `docs/prompts/reconcile-change.md` (NEW)
- `.claude/commands/reconcile.md` (NEW; first project-level command — new `.claude/commands/` surface)
- `docs/upgrading/v5.8-to-v5.9.md` (NEW)
- `docs/KIT_VERSION` (5.8 → 5.9)
- `docs/README.md` (copy tree + prompts table + universal list + folder reference)
- `docs/AGENTS.md` (prompt-map table)
- `docs/CLAUDE_SNIPPET.md` (Workflow Prompts table + standalone-prompt usage note)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — `docs/upgrading/v5.8-to-v5.9.md`. The change is purely additive (two new files dropped in, plus a version bump), but it ships as a migration prompt rather than `inline-edit` because: (a) the `### Edits` Find/Replace format models surgical edits to existing files, not new-file additions; (b) `.claude/commands/` is a brand-new directory most v5.8 projects won't have, so the copy needs an explicit `mkdir -p`. No customized file is touched, so there is nothing to merge and no overwrite risk. Projects that don't want `/reconcile` (e.g. a shape where ad-hoc reconciliation doesn't apply) can skip both files and still stamp `5.9`.

---

## [v5.8] — 2026-05-27

**Minor — verdict expansion + scenario patterns: codify Pattern A/C in kit vanilla, ship Pattern B as opt-in, plus four upgrade-kit-prompt improvements.** Driven by the CoffeeScribe v5.4→v5.7 upgrade retrospective. The patterns themselves were live in CoffeeScribe (`docs/prompts/create-testing-agent.md` → "Required Scenario Patterns") and were found to be generic enough to ship to every adopting project — but only after pairing the generator-side additions with the matching runtime contract in the base `testing-agent.md`. The retrospective also surfaced four upgrade-tooling gaps that ship in the same release because their fix sites overlap.

### Added
- **`BLOCKED_NEEDS_FIXTURE` verdict in `.claude/agents/testing-agent.md`** (v3 → v3.1). Pattern A guard: scenario rendered correctly but the submit/action step could not be exercised because of a missing fixture, credential, or DB precondition. NOT a PASS; NOT a SKIP. Added to: description frontmatter, Role section, verdict legend (Step 8 of Execution Protocol), Test Execution Loop verdict-recording step, Reporting Format Totals line, new Blocked section in Reporting Format, new Rule 22.
- **`REQUIRES_INPUT` verdict in `.claude/agents/testing-agent.md`** (v3 → v3.1). Pattern C guard: scenario produced a finding the agent cannot categorize as PASS or FAIL without orchestrator/human judgment. Forces ambiguity into the verdict layer instead of leaving it as Reliability Notes prose (which parent agents miss because they act on verdicts). Verdict shape: finding + 2–3 interpretations + recommended default + disambiguating question. Same locations as BLOCKED_NEEDS_FIXTURE, plus new Requires Input section in Reporting Format, plus new Rule 21.
- **"Required Scenario Patterns" section in `docs/prompts/create-testing-agent.md`** (v2 → v3). Patterns A (end-to-end submit assertion) and C (REQUIRES_INPUT for ambiguity) are now required for every generated test plan. Pattern B (DB preconditions) is referenced as opt-in via `docs/patterns/db-precondition.md`. Section inserted between Step 7 (Update the Registry) and the Quality Checklist.
- **Pattern A enforcement in the scenario template** — Step 3 template's Pass criteria now has explicit `Render:` AND `Submit:` lines (was a single UI line). New `BLOCKED_NEEDS_FIXTURE triggers` and `REQUIRES_INPUT triggers` callouts in the template alongside the existing `ABORT triggers`.
- **Two Quality Checklist items in `docs/prompts/create-testing-agent.md`** — one for Pattern A render-+-submit coverage, one for Pattern C ambiguity authorization.
- **`docs/patterns/db-precondition.md`** (NEW directory + file). Pattern B opt-in documentation: scenario template fields (`requires_db_state` / `db_setup_sql` / `db_teardown_sql`), runtime contract (configurable DB-execute MCP tool name; `MANUAL_BLOCKER` verdict when unavailable), Postgres+Supabase example, dialect callout for SQLite/MySQL/BigQuery. Pattern B is NOT shipped in kit vanilla because the runtime contract hardwires a project-specific DB-execute tool.
- **"Project extensions to kit files" section in `docs/KIT_DEVIATIONS.md`** (template). New category between "Sub-agent model overrides" and "Other project conventions." Records ADDITIVE content the project has placed inside kit-shipped files — opt-in pattern adoptions, project-specific scenario rules, extra Quality Checklist items. Distinct from "filled placeholders" (expected case) and "structurally rewritten" (full file replacement). Three examples covering Pattern B adoption + project-only extension.
- **`docs/upgrading/v5.7-to-v5.8.md`** — migration prompt walking adopting projects through verdict additions to base agent, scenario template + Required Scenario Patterns additions, optional Pattern B opt-in question, dedup detection for projects already carrying Patterns A/C as project extensions (offers to remove the now-duplicate project-side copy), KIT_VERSION bump, fingerprint check.
- **Step 0.5 — Working-tree sanity check in `docs/prompts/upgrade-kit.md`.** Before fingerprinting, runs `git status --porcelain` and greps for upgrade-content signatures (`KIT:SLOT-BEGIN`, `Sub-Agent Supervision Protocol`, `default_action_timeout`, `Expected duration`, etc.). Catches the "uncommitted prior-pass work" failure mode that confused the CoffeeScribe upgrade — 4 options surfaced (commit checkpoint, stash, treat-as-in-flight, discard).
- **"Special case — self-referential edits to `upgrade-kit.md`" rule in Step 4 patch flow.** When a release includes `### Changed` entries to `upgrade-kit.md` itself, the rule does a 3-check vanilla detection (single commit in history, empty diff vs prior vanilla, no slot markers) and wholesale-replaces the project copy when vanilla — instead of leaving the prompt stale because Find/Replace anchors don't exist in the older copy. Falls through to slot-aware editing if any check fails.
- **"Next-chat handoff" subsection in Step 6 final report.** When the upgrade leaves in-flight work (deferred test-plan retrofit, judgment-edit STOPs skipped, surface-absent follow-ups), produce a fenced paste-ready prompt addressed to the next agent — mirrors Phase 5.10 handoff format. Omitted entirely if the upgrade completed cleanly.
- **"Parallelism for large plan sets" callout in `docs/upgrading/v5.6-to-v5.7.md` Phase 3.5.3.** When a project has >8 test plans to retrofit, dispatch to N sub-agents per the Sub-Agent Supervision Protocol (bounded scope ~4 plans per dispatch). Observed: 16 plans across 4 parallel Opus agents ≈ 12 min wall-clock vs 45+ min sequentially.

### Changed
- **`docs/prompts/create-testing-agent.md` header version stamp** — (v2) → (v3). Internal version; user-facing kit version is v5.8.
- **`docs/prompts/create-testing-agent.md` Step 3 scenario template Pass criteria** — single `UI:` line split into `Render:` + `Submit:` (Pattern A). Existing `Network:`, `Console:`, `Timing:` lines unchanged.
- **`.claude/agents/testing-agent.md` header version stamp** — (v3) → (v3.1). v3 self-pacing intact; v3.1 adds the two new verdicts.
- **Verdict set everywhere in `.claude/agents/testing-agent.md`** — PASS/FAIL/SKIP/ABORT → PASS/FAIL/SKIP/ABORT/BLOCKED_NEEDS_FIXTURE/REQUIRES_INPUT. Updated in description frontmatter, Role section ambiguity clause, Step 8 verdict legend, Test Execution Loop step 10, Reporting Format Totals line.

### Files touched
- `.claude/agents/testing-agent.md` (description frontmatter; header version stamp v3 → v3.1 + new blockquote describing both verdicts; Role section ambiguity clause; Step 8 verdict legend rewritten; Test Execution Loop step 10 verdict recording; Reporting Format Totals line; two new Reporting Format sections — Blocked + Requires Input; Rules 21 + 22 added)
- `docs/prompts/create-testing-agent.md` (header version stamp v2 → v3; Step 3 scenario template Pass criteria + new BLOCKED_NEEDS_FIXTURE / REQUIRES_INPUT trigger callouts; new "Required Scenario Patterns" section between Step 7 and Quality Checklist with Pattern A + Pattern C + Pattern B opt-in reference; Quality Checklist gains 2 items)
- `docs/patterns/db-precondition.md` (NEW; Pattern B opt-in documentation)
- `docs/KIT_DEVIATIONS.md` (template — new "Project extensions to kit files" section between "Sub-agent model overrides" and "Other project conventions")
- `docs/prompts/upgrade-kit.md` (new Step 0.5 working-tree sanity check; new self-referential-edits rule in Step 4 patch flow; new Next-chat handoff subsection in Step 6; new Method B fingerprint row for v5.8)
- `docs/upgrading/v5.6-to-v5.7.md` (new Parallelism for large plan sets callout in Phase 3.5.3)
- `docs/upgrading/v5.7-to-v5.8.md` (NEW; migration prompt)
- `docs/KIT_VERSION` (5.7 → 5.8)
- `docs/KIT_CHANGELOG.md` (this entry)

### Migration
`migration-prompt-required` — additive across the runtime/template pair and requires careful merging in projects that already carry equivalent extensions (e.g. CoffeeScribe's project-side Patterns A/C should be detected and the project-side copy removed since the kit vanilla now covers them). The migration prompt walks state audit, base agent verdict additions, generator pattern additions, optional Pattern B opt-in, dedup detection for project-side Pattern A/C content, KIT_VERSION bump, and fingerprint check. Run `docs/upgrading/v5.7-to-v5.8.md`.

---

## [v5.7] — 2026-05-24

**Major — sub-agent reliability across four fronts: Phase 2 returns to the orchestrator, supervision protocol added for every other dispatch, testing-agent self-pacing to kill silent stalls, copy-pasteable next-chat handoffs.** Four linked changes bundled into one release because no project has adopted v5.7 yet — shipping the full set avoids a v5.7/v5.7.1/v5.7.2 migration ladder for adopting projects:

1. **Phase 2 inline (both modes)** — earlier kit versions dispatched Phase 2 to a sub-agent in orchestrator mode, but Phase 2 was the highest-failure-rate dispatch (large context, no incremental commits the orchestrator could observe). Silent Phase 2 failures poisoned every downstream phase. Phase 2 now runs in the orchestrator in BOTH single-chat and orchestrator mode.
2. **Sub-Agent Supervision Protocol** — every remaining sub-agent dispatch (Phase 3, 3.5, 4 automated, 4 fix-loop, 5.5, 5.7a, 5.7b) goes through a new top-level section with three rules: structured `SUB-AGENT RETURN` blocks, mandatory `git show <hash>` verification before any further work, and bounded scope (split work past ~30 tool calls into PRD-task-bounded batches). Phase 5.3 doc rewrites and Phase 5.7 sub-agent inputs are now driven by `git log --format` + `git diff main...HEAD`, NOT orchestrator transcript memory — this catches the most damaging silent-drift class in the lifecycle, where a Phase 4 fix sub-agent quietly changes the architecture and the docs end up describing the original plan while the code does something else.
3. **`testing-agent` v3 — self-pacing + ABORT verdict** — driven by an observed 12-hour testing-agent run that stalled silently for 1–2 hours at a time on hung browser actions. v3 reads an **expected duration** per scenario step (from a `[~Xs]` bracket the plan author writes, or a heuristic fallback), watches for observable progress on an adaptive cadence (3 checks per expected period), and declares `ABORT` (a fourth verdict alongside PASS/FAIL/SKIP) if any single action exceeds ~3× its expected duration with no progress. Writes a `progress.log` the user can `tail -f` from another terminal. ABORT is a reliability signal, NOT a feature failure — the orchestrator's Phase 4.1 section has explicit handling guidance for the three causes (wrong duration estimate / hung browser / app genuinely broken).
4. **Copy-pasteable next-chat handoffs** — Phase 5.10 ("Next Steps") now requires the AI to produce a literal, fenced, paste-ready prompt block addressed to the next agent ("Read X. Continue with Y. Stop at Z.") — NOT a meta-plan addressed to the user describing what they should do. Was: vague "provide a context summary block." Now: explicit shape with good/bad examples.

All four changes share a single root cause — the orchestrator was over-trusting things outside its direct context (Phase 2 sub-agent summaries, sub-agent return prose, hung testing-agent calls, handoff plans the user has to translate). v5.7 makes "verify the artifact, not the summary" the load-bearing principle across the lifecycle.

### Added
- **Sub-Agent Supervision Protocol** as a new top-level section in `docs/prompts/feature-lifecycle.md` (between Phase Commit Discipline and Skills to Invoke). Four parts: (1) structured `SUB-AGENT RETURN` block contract — status, commits (hash + subject), files modified, gate block, blockers, self-reported confidence; (2) verify-before-trust — orchestrator runs `git show <hash> --stat` on every returned commit, `git show <hash>` full diff for any commit touching architecture, re-dispatches if commits or gate blocks are missing; (3) bounded scope — split work past ~30 tool calls or >10 files into sequential PRD-task-bounded batches; (4) Phase 5 implication — doc work (5.3 + 5.7) is sourced from `git log --format` + `git diff`, not transcript. The protocol explicitly does NOT apply to Phase 2 (which now stays inline).
- **Phase 5.3 Step 1 — git evidence aggregation** as mandatory pre-rewrite step. Lists the four git commands the orchestrator runs (`git log --format`, `git diff --stat`, `git log --grep=DEVIATION:`, per-file `git diff`) and explains the load-bearing reason: fix-sub-agent architectural drift that leaves docs describing the wrong design.
- **Per-commit-log input** to Phase 5.5 (code-quality-agent Post-Test), 5.7a (context-docs-agent), 5.7b (docs-auditor-agent) — these sub-agents now receive `git log --format="%h %s%n%b%n---" main...HEAD` in addition to the existing `git diff --name-only` so they see WHY each file changed, not just WHAT changed.
- **`docs/upgrading/v5.6-to-v5.7.md`** — migration prompt covering the Phase 2 inline migration, the supervision protocol insertion, the Phase 5.3 git-evidence step, and the Phase 5.5/5.7 input extensions. Two stop points.
- **Sub-agent supervision callout** in `docs/CLAUDE_SNIPPET.md` (Model Split section) so every adopting project's CLAUDE.md gets the three-rule summary + Phase 2 inline note.
- **Common mistake row in `docs/AGENTS.md`** — "Trusting a sub-agent's summary without reading the diff" with the v5.7+ tag.
- **Fingerprint row in `docs/prompts/upgrade-kit.md` Method B** — string `Sub-Agent Supervision Protocol` in `feature-lifecycle.md` → at least v5.7.
- **`testing-agent.md` Section 2.5 "Self-Pacing & Progress Watch"** (NEW, mandatory). Per-action expected-duration determination (test-plan bracket → config default → heuristic table); adaptive-cadence watch loop with 1×/2× warnings and 3× ABORT cap; observable-progress detection (success-fn, error-fn, DOM-hash change, URL/network change); per-action progress logging to `{evidence_dir}/progress.log` so the user can `tail -f` the run.
- **`ABORT` as a fourth verdict** alongside PASS/FAIL/SKIP in the testing-agent Reporting Format. Per-ABORT evidence includes last-observed state, last network activity, last console line, and the agent's best guess at cause. Reporting Format updated.
- **`testing-agent.md` Rules 19 + 20** — "wrap every browser action in the self-pacing watch loop" and "log every START/END/WARN/ABORT to progress.log."
- **`run_hard_cap` config field** in test plans (default 45min) — whole-run wall-time cap; agent declares `RUN_ABORT` if exceeded.
- **`default_action_timeout` + `progress_log` config fields** in test plans.
- **Per-step `[~Xs]` expected-duration brackets** in the scenario template in `create-testing-agent.md`. Sub-second clicks/fills can omit; anything ≥ 5s or involving a wait must specify.
- **Scenario-level `Expected duration (whole scenario)` field** in scenario template.
- **Phase 5.10 "Next-chat handoff prompt — REQUIRED format" subsection** in `feature-lifecycle.md` — explicit shape (fenced code block, absolute paths, imperative addressed to next agent, stop points if any), with good/bad examples. Default behavior: produce the handoff prompt without being asked at end of any session that left in-flight work.
- **Phase 4.1 ABORT-handling guidance** in `feature-lifecycle.md` — three-cause taxonomy (wrong duration estimate / hung browser / genuine app hang) and the orchestrator's response per cause. "Do NOT just blanket re-run on every ABORT — that's how 12-hour test runs happen."

### Changed
- **Phase 2.0 (Mode-specific behavior) in `docs/prompts/feature-lifecycle.md`** — single-chat AND orchestrator mode now both run Phase 2 inline. Earlier "orchestrator mode dispatches a Phase 2 sub-agent" block removed. New context-budget note: if Phase 2 ever balloons, the user may `/clear` between Phase 2 and Phase 3 — Phase 2 artifacts on disk are the handoff. Do NOT solve a tight Phase 2 by re-dispatching.
- **Model split table at top of `docs/prompts/feature-lifecycle.md`** — Phase 2 sub-agent row removed; the Orchestrator row absorbs Phase 2's responsibilities (Plan Mode + PRD + ADR + Feature Architecture + plan snapshot).
- **Phase 3.0 / 3.5.0 / 4.0 Mode-specific sections** — each now references the Sub-Agent Supervision Protocol and emphasizes `git show`-on-return. Phase 3.0 specifically mentions bounded-scope splitting (15-task PRD → 2-3 sequential batches, not one mega-dispatch). Phase 4.0 fix-loop step 4 expanded to require `git show <hash>` full diff (not just `--stat`) and to flag architectural drift even if the fix-agent didn't `DEVIATION:`-tag it.
- **Phase 5.3 (Rewrite PRD/ADR/Architecture)** — restructured into Step 1 (aggregate git evidence) + Step 2 (rewrite per-doc). Explicit "fix-sub-agent architectural drift" callout. Every architectural change in the rewritten docs should cite the commit hash that introduced it.
- **Appendix mental-model diagram** — Phase 2 box now shows MAIN (not dispatch); every other dispatch arrow has a `◄── orchestrator: git show <hash>` verify-step underneath. Sub-agents now explicitly return `SUB-AGENT RETURN block` instead of ad-hoc summaries.
- **`docs/CLAUDE_SNIPPET.md` model-split table** — Phase 2 sub-agent row removed; Orchestrator row updated to mention Phase 2 inline ownership. testing-agent row updated to mention ABORT verdict + self-pacing + progress.log.
- **`docs/AGENTS.md` sub-agent table** — testing-agent row mentions ABORT + Section 2.5 self-pacing.
- **`docs/README.md`** Opus paragraph — Phase 2 ad-hoc sub-agent removed; orchestrator now noted as owning Phase 2 end-to-end inline.
- **`testing-agent.md` v2 → v3.** Header description + version stamp updated. Section 4 (async pattern) reframed as a specialization of Section 2.5 rather than the only pacing protocol.
- **`testing-agent.md` Pre-Flight Step 3** — now initializes `progress.log` and reports the `tail -f` path to the parent agent.
- **`testing-agent.md` Section 3 (Test Execution Loop)** — every action now wrapped in the Section 2.5 watch loop (not just async ops); per-scenario START/END logged to progress.log; ABORT breaks the scenario but never the suite.
- **`feature-lifecycle.md` Phase 4.1** — invocation block expanded with live progress visibility note + ABORT-handling taxonomy. PASS/FAIL/SKIP → PASS/FAIL/SKIP/ABORT in two places.
- **`feature-lifecycle.md` Phase 5.10 "Next Steps"** — was 2 vague bullets, now includes the REQUIRED handoff-prompt-format subsection.
- **`create-testing-agent.md` scenario template** — adds `Expected duration (whole scenario)` field, per-step `[~Xs]` brackets, ABORT triggers callout.
- **`create-testing-agent.md` Step 5 (Set Configuration)** — adds `default_action_timeout`, `run_hard_cap`, `progress_log` fields.
- **`create-testing-agent.md` Quality Checklist** — three new items for expected-duration coverage.

### Removed
- **The Phase 2 sub-agent dispatch path entirely.** Orchestrator mode no longer offers a Phase 2 dispatch route. Projects on v5.6 that had documented or customized the Phase 2 dispatch in their CLAUDE.md should remove those references during the v5.6→v5.7 migration.

### Files touched
- `docs/prompts/feature-lifecycle.md` (header model-split table — Phase 2 row removed; Phase 2.0 rewritten to inline-both-modes; new Sub-Agent Supervision Protocol section after Phase Commit Discipline; Phase 3.0/3.5.0/4.0 references to protocol added; Phase 4.0 fix-loop step 4 expanded; Phase 4.1 expanded with live-progress note + ABORT-handling taxonomy + PASS/FAIL/SKIP→PASS/FAIL/SKIP/ABORT; Phase 5.3 restructured into Step 1/Step 2 with git-aggregation; Phase 5.5/5.7a/5.7b inputs extended with `git log --format` per-commit; Phase 5.10 expanded with REQUIRED handoff-prompt-format subsection; Stop-Points summary updated; Appendix diagram updated)
- `.claude/agents/testing-agent.md` (v2 → v3; new Section 2.5 self-pacing; Reporting Format includes ABORT; Rules 19+20 added; Pre-Flight initializes progress.log; Section 3 wraps actions in self-pacing loop)
- `docs/prompts/create-testing-agent.md` (scenario template adds Expected duration + per-step brackets + ABORT triggers; Step 5 config adds `default_action_timeout` / `run_hard_cap` / `progress_log`; Quality Checklist adds 3 new items)
- `docs/CLAUDE_SNIPPET.md` (model-split table — Phase 2 sub-agent row removed; Orchestrator row updated; new Sub-Agent Supervision sub-section after Rule of thumb; testing-agent row updated for ABORT + self-pacing + progress.log)
- `docs/AGENTS.md` (Common mistakes — v5.5 model row updated to mention Phase 2 inline; new "Trusting a sub-agent's summary" mistake row; sub-agent table testing-agent row updated for ABORT + Section 2.5)
- `docs/README.md` (Opus paragraph — Phase 2 ad-hoc sub-agent removed; orchestrator now noted as owning Phase 2 inline)
- `docs/KIT_VERSION` (5.6 → 5.7)
- `docs/KIT_CHANGELOG.md` (this entry)
- `docs/prompts/upgrade-kit.md` (Method B fingerprint table — new row for v5.7 protocol string)
- `docs/upgrading/v5.6-to-v5.7.md` (new — migration prompt; covers all four bundled changes)

### Migration
`migration-prompt-required` — structural changes across four areas (Phase 2 sub-agent dispatch removed, new Sub-Agent Supervision Protocol section, testing-agent v2→v3 self-pacing with new Section 2.5 + 4-state verdict + progress.log + test-plan template changes, Phase 5.10 handoff-format requirement). Cannot be applied as a mechanical Find/Replace patch — projects' `feature-lifecycle.md`, `testing-agent.md`, and per-feature test plans may have hand-edits or slot customizations in the affected regions that need surgical merging. Existing test plans must be retrofitted with `Expected duration` brackets (or left without — the heuristic table covers them, but the agent will use 30s as the default which may be too short for backup-style features). Run `docs/upgrading/v5.6-to-v5.7.md`.

---

## [v5.6] — 2026-05-24

**Minor — upgrade-system robustness: graceful handling of project-side deviations from kit baseline.** Driven by the Fossify-Android v5→v5.5 upgrade retrospective. Six linked changes close gaps that retrospective surfaced: a Pre-Test gate-count drift, no recognition for `model: inherit`, no first-class outcome for "edit targets surface project doesn't have," no first-class outcome for "project file is structurally divergent," a namespace collision between the kit's release log and the project's feature log (both named `CHANGELOG.md`), and no persistent record of a project's deliberate deviations.

### Added
- **`docs/KIT_DEVIATIONS.md`** (new file, template + reference). Records the project's deliberate deviations from kit baseline in four categories: slots intentionally left as templates, files structurally rewritten, kit sections intentionally absent, sub-agent model overrides. Future upgrades read this file FIRST to distinguish intentional deviations from surprises. The kit-adopt skill seeds it as a near-empty template; the upgrade-kit prompt's new Step 4.6 appends discovered deviations.
- **Step 4.6 (`docs/KIT_DEVIATIONS.md` updates)** in `docs/prompts/upgrade-kit.md` — after Step 4 patch-release execution, persist any newly discovered deviations from this upgrade so the NEXT upgrade doesn't re-flag them.
- **Structural-divergence detection** in `docs/prompts/upgrade-kit.md` Step 2.5 — measures heading-anchor overlap between project file and kit vanilla; classifies as STRUCTURALLY DIVERGENT if overlap < 25%. Skips per-paragraph edits for those files. Documents in inventory report.
- **`surface-absent` and `structurally-divergent` outcomes** as first-class classifications in `docs/prompts/upgrade-kit.md` Step 4 (Patch release flow). Alongside `applied` and `conflict`, these are the four possible per-edit outcomes. Only `conflict` triggers a STOP; the other two are healthy continuation outcomes that document a project's deliberate deviations.
- **`model: inherit` recognition** in `docs/prompts/upgrade-kit.md` fingerprint table and `docs/upgrading/v5.4-to-v5.5.md` Phase 1.3. Treats `inherit` as a third valid state for the `model:` frontmatter field, not a surprise. Projects that deliberately route sub-agents through the orchestrator's model are preserved during model-default bumps.
- **`docs/upgrading/v5.5-to-v5.6.md`** (new — migration prompt). Walks the rename, the optional KIT_DEVIATIONS.md creation, and the gate-count fix. Special-cases projects with a non-kit root `CHANGELOG.md` (e.g. Fossify-fork upstream changelogs) so they aren't clobbered.

### Changed
- **`docs/CHANGELOG.md` → `docs/KIT_CHANGELOG.md`** (rename). Disambiguates from `docs/context/CHANGELOG.md`, which is the project's feature-by-feature change log owned by `context-docs-agent`. Two files with the same basename and different owners was a documented confusion source pre-v5.6. Path references updated in: `docs/AGENTS.md`, `docs/README.md`, `MAINTAINING.md`, `MAINTAINER_LOG.md`, `docs/prompts/upgrade-kit.md`, `docs/upgrading/v5.4-to-v5.5.md`.
- **Pre-Test gate-count consistency fix**. `docs/prompts/feature-lifecycle.md` line ~398 said `block with 5 numbered lines`; the kit's established convention (per `.claude/agents/code-quality-agent.md` lines 13, 220, 222, 278) is `4 items + process sweep`. Aligned the description. Also fixed `docs/CLAUDE_SNIPPET.md` sub-agents table rows for `code-quality-agent` Pre-Test (was `6 items` — pre-v5.1 drift) and Post-Test (was `11 items` — pre-v5.1 drift). The v5.4 release notes claimed this drift was fixed in `CLAUDE_SNIPPET.md`'s Post-Feature Gates section but missed the sub-agents table; v5.6 closes that gap.

### Files touched
- `docs/KIT_CHANGELOG.md` (renamed from `docs/CHANGELOG.md`; this entry added; naming-note callout added at top)
- `docs/KIT_DEVIATIONS.md` (new — template file)
- `docs/prompts/upgrade-kit.md` (Step 2.5 structural-divergence detection added; inventory report shape extended; Step 4 patch-release flow gains four-outcome classification; Step 4.6 KIT_DEVIATIONS.md updates section added; fingerprint table note on `model: inherit` added; path references updated to `docs/KIT_CHANGELOG.md`; final report shape updated)
- `docs/upgrading/v5.4-to-v5.5.md` (Phase 1.3 enumerates four model-field states including `inherit`; Phase 2.1 preserves `inherit` as override; Phase 2.3 documents `surface-absent` handling; Phase 2.5 references new `KIT_CHANGELOG.md` filename)
- `docs/upgrading/v5.5-to-v5.6.md` (new — migration prompt)
- `docs/prompts/feature-lifecycle.md` (Phase 3.5.2 Pre-Test gate description aligned to `4 items + process sweep`)
- `docs/CLAUDE_SNIPPET.md` (sub-agents table — Pre-Test row: `6 items` → `4 items + process sweep`; Post-Test row: `11 items` → `10 items`)
- `docs/AGENTS.md` (Versioning section — kit changelog path updated; KIT_DEVIATIONS.md row added; read-order list updated)
- `docs/README.md` (Versioning & changelog section — kit changelog path updated; KIT_DEVIATIONS.md mention added)
- `docs/KIT_VERSION` (bumped 5.5 → 5.6)
- `MAINTAINING.md` (read-order, tree diagram, single-source-of-truth list updated for the rename)
- `MAINTAINER_LOG.md` (read-order list updated for the rename; entry for v5.6 release added)

### Migration
`migration-prompt-required` — see `docs/upgrading/v5.5-to-v5.6.md`.

The rename is the only operation with risk: some adopting projects (e.g. Fossify forks) preserve an upstream's root-level `CHANGELOG.md` and the kit's release log lives separately at `docs/CHANGELOG.md`. The migration prompt audits both locations and only renames the kit log, never the project/upstream log. All other changes are additive (new file, new classifications, new outcomes) or doc-only.

### Cost note
v5.6 changes the upgrade prompt's behavior but does NOT change runtime model defaults, gate format, or sub-agent contracts. Per-feature Opus usage is unchanged from v5.5. The upgrade itself runs on Opus (same as v5.5's migration prompt) for the rename-safety judgment in Phase 1.2; small one-time cost per project.

---

## [v5.5] — 2026-05-23

**Minor — Phase 3 implementation sub-agent and `code-quality-agent` (both modes) promoted from Sonnet to Opus.** The kit's model-split principle shifts from "Opus plans, Sonnet executes everything else" to "Opus owns the critical path (planning, implementation, gate-quality decisions, post-manual-test fixes); Sonnet owns structured execution (walking a fixed test plan or decision tree)." Driven by the observation that implementation and code-quality judgment are decision-grade work — false negatives at either step are expensive to catch downstream, and the cost delta to Opus is small relative to one missed bug reaching Phase 4. `testing-agent`, `context-docs-agent`, and `docs-auditor-agent` stay on Sonnet — their work is mechanical aggregation against an explicit contract.

### Changed
- **`.claude/agents/code-quality-agent.md`** — frontmatter `model: sonnet` → `model: opus`. Model-rationale paragraph rewritten: gate blocks are decision-grade outputs; aggregating and judging findings is itself a reasoning task.
- **Phase 3 sub-agent dispatch** (orchestrator mode) — default model `sonnet` → `opus`. Override is now to Sonnet for purely mechanical work (was: to Opus for algorithmically-heavy work).
- **`docs/prompts/feature-lifecycle.md`** — model split header table rewritten end-to-end. Phase 3 row flipped to Opus. New row added for `code-quality-agent` (Opus). `testing-agent`/`context-docs-agent`/`docs-auditor-agent` consolidated into one Sonnet row. Phase 4 fix sub-agent rationale rewritten — no longer "Sonnet missed it, escalate to Opus"; now "fresh dispatch with isolated context, priors-free reading the long-running orchestrator can't get." Mental-model paragraph and "Model split at a glance" footer rewritten. Appendix ASCII diagram updated.
- **`docs/AGENTS.md`** — lifecycle-at-a-glance Phase 3 line flipped to Opus. Sub-agents table: both code-quality-agent rows flipped to Opus. "Mixing model contexts" mistake bullet rewritten.
- **`docs/CLAUDE_SNIPPET.md`** — "Model Split" section rewritten with the new principle and table. Phase 4 fix sub-agent note rewritten.
- **`docs/README.md`** — "Model setting" section rewritten with the new principle and per-role list.
- **`docs/prompts/bugfix.md` and `docs/prompts/debug.md`** — preamble Model note updated to remove the obsolete "Opus plans, Sonnet executes" framing; now points at the model split table.
- **`docs/prompts/upgrade-kit.md`** — fingerprint table extended with a v5.5 marker (`.claude/agents/code-quality-agent.md` frontmatter `model: opus`).

### Files touched
- `.claude/agents/code-quality-agent.md` (frontmatter + Model paragraph)
- `docs/prompts/feature-lifecycle.md` (model split table; Phase 3 dispatch; Phase 4 fix dispatch; appendix diagram; "Model split at a glance" footer)
- `docs/AGENTS.md` (lifecycle at a glance; sub-agents table; "Mixing model contexts" bullet)
- `docs/CLAUDE_SNIPPET.md` (Model Split section; Phase 4 fix sub-agent note)
- `docs/README.md` (Model setting paragraphs)
- `docs/prompts/bugfix.md` (Model preamble)
- `docs/prompts/debug.md` (Model preamble)
- `docs/prompts/upgrade-kit.md` (fingerprint table new row)
- `docs/CHANGELOG.md` (this entry)
- `docs/KIT_VERSION` (bumped 5.4 → 5.5)
- `docs/upgrading/v5.4-to-v5.5.md` (new — migration prompt)

### Migration
`migration-prompt-required` — see `docs/upgrading/v5.4-to-v5.5.md`.

The mechanical surface is small (flip one frontmatter field; rewrite a handful of paragraphs), but adopting projects may have customized the model-split rationale in their CLAUDE.md or sub-agent intros and need a guided pass to (a) verify the flip applied, (b) reconcile any custom rationale text that referenced the old principle, and (c) confirm the project's chat-default model setting is still Opus. A migration prompt is safer than inline `Find:` / `Replace:` pairs for paragraph rewrites.

### Cost note
This change increases Opus usage in two places: Phase 3 implementation (every feature) and `code-quality-agent` (every feature, both Pre-Test and Post-Test passes). For a typical feature that's ~2-3× more Opus turns than v5.4. The principle bet: shipping one fewer bug to Phase 4 (or one fewer regression to manual testing) is worth more than the Opus delta. If you find Phase 3 is consistently mechanical for your project (heavy scaffolding, low novel-logic ratio), use the documented Phase 3 sonnet override and call it out on dispatch.

---

## [v5.4] — 2026-05-23

**Minor — migration robustness: the kit can now upgrade projects with customizations safely.** Driven by the SuperNoteOrganiser v1 → v5.3 upgrade retrospective. Five linked changes close the gaps that retrospective surfaced: a missing v1 → v2 migration prompt, slot-marker convention so customizations survive future upgrades, formalized CHANGELOG entry structure, CLAUDE.md auto-sync, and a drift-check phase.

### Added
- **`docs/AGENTS.md`** — single-page orientation doc for any AI agent landing in a kit-adopted project. Covers lifecycle, sub-agents, gates, skills, customization conventions, project-state files the agent must NOT touch, when to STOP, and common mistakes. Read-first reference; routes to deeper docs when needed.
- **`docs/upgrading/v1-to-v2.md`** — full migration prompt for v1 → v2 (the previously-missing step that blocked SuperNoteOrganiser). 10 ordered edits to `feature-lifecycle.md` + 2 new empty side-files (`docs/known-test-failures.md`, `docs/known-test-skips.md`). Two stop points for sanity checks.
- **Slot-boundary markers (`<!-- KIT:SLOT-BEGIN <name> -->` / `<!-- KIT:SLOT-END -->`)** wrapped around every `[CUSTOMIZE]` block in the kit's 6 customizable files. The marker persists when the placeholder text is replaced at install — so a future upgrade can extract project content by slot name and re-inject after the kit update.
- **Step 2.5 (Customization Inventory)** in `docs/prompts/upgrade-kit.md` — extracts customizations by slot marker (v5.4+) OR by `[CUSTOMIZE]` grep fallback (pre-v5.4), plus detects hand-edits outside any slot.
- **Step 3.5 (Drift Check)** in `docs/prompts/upgrade-kit.md` — cross-references each extracted customization against the project's CLAUDE.md, ADRs, and context files. Catches stale references (e.g. an old API-key name) before they get re-injected. Would have caught the `ANTHROPIC_API_KEY` → `VENICE_API_KEY` drift case in SuperNoteOrganiser.
- **Step 4.5 (CLAUDE.md updates)** in `docs/prompts/upgrade-kit.md` — auto-applies mechanical CLAUDE.md edits derivable from the CHANGELOG (item-count bumps, line-number bumps, skill renames, new prompt rows, gate-count changes); surfaces judgment edits (table-row splits, paragraph placement) as STOPs.
- **`### Required entry structure` section** at the top of `docs/CHANGELOG.md` — mandates `### Files touched`, `### Migration` with explicit classification (`trivial-noop` / `inline-edit` / `migration-prompt-required`), and an optional `### Edits` block for inline-edit releases.

### Changed
- **CHANGELOG entries for v1, v2, v3, v4** backfilled with `### Files touched` and a structured `### Migration` classification line. v2 entry specifically now links to the new `docs/upgrading/v1-to-v2.md`.
- **`docs/prompts/upgrade-kit.md`** — substantially rewritten to use the slot-marker convention, Migration-field classification, drift check, and CLAUDE.md sync. Fingerprint table extended with a v5.4 marker (presence of `KIT:SLOT-BEGIN` strings in kit files).
- **`docs/CLAUDE_SNIPPET.md` Post-Feature Gates section** — fixed pre-v5.1 drift: "11 items" → "10 items", "Item 8" → "Item 7" for the second vulnerability scan, plus a Pre-Test gate note and the v5.2 Opus-fix-sub-agent paragraph. (This is the same class of drift v5.4's Step 4.5 now auto-syncs in adopting projects — caught here in the kit source itself.)

### Files touched
- `docs/upgrading/v1-to-v2.md` (new)
- `docs/prompts/upgrade-kit.md` (rewritten — Steps 2.5, 3.5, 4.5 added; Step 3 plan classification by Migration field; Step 4 patch-release flow slot-aware)
- `docs/CHANGELOG.md` (Required-structure section added; v2-v4 entries backfilled; v5.4 entry added)
- `docs/KIT_VERSION` (bumped 5.3 → 5.4)
- `.claude/agents/code-quality-agent.md` (6 slot markers added: `agent-intro`, `pkill-test-process`, `ps-grep-test-process`, `debug-grep`, `test-command`, `build-command`, `pkill-teardown`, `ps-grep-teardown`)
- `.claude/agents/testing-agent.md` (3 slot markers added: `agent-intro`, `env-credentials`, `auth-section`)
- `.claude/agents/context-docs-agent.md` (2 slot markers added: `agent-intro`, `target-files`)
- `.claude/agents/docs-auditor-agent.md` (4 slot markers added: `agent-intro`, `surface-paths`, `help-center-paths`, `tour-attributes`)
- `docs/prompts/feature-lifecycle.md` (3 slot markers added: `skills-list`, `resource-management`, `test-failure-discipline`)
- `docs/CLAUDE_SNIPPET.md` (5 slot markers added + drift fix: `intro-instructions`, `commands`, `conventions`, `skills-tools`, `mobile-android`; Post-Feature Gates section updated to v5.3 numbering)

### Migration
`inline-edit` — apply this entry directly via the upgrade-kit prompt OR manually. Six mechanical steps:

1. Copy `docs/upgrading/v1-to-v2.md` from the new kit (`cp -n` — never overwrite).
2. Copy the rewritten `docs/prompts/upgrade-kit.md` from the new kit (will overwrite the prior version — the project's copy was vanilla per v5.3 conventions, so safe).
3. Replace `docs/CHANGELOG.md` with the new kit's version (preserves history; adds v5.4 entry and the Required-structure section).
4. Update `docs/KIT_VERSION` to `5.4`.
5. Add slot markers to the project's `.claude/agents/*.md`, `docs/prompts/feature-lifecycle.md`, and `docs/CLAUDE_SNIPPET.md` (or the project's derived CLAUDE.md if it carries the same slots). Each marker wraps an existing `[CUSTOMIZE]` block with `<!-- KIT:SLOT-BEGIN <name> --> ... <!-- KIT:SLOT-END -->`. The exact slot names and positions are listed in the "Files touched" section above. Project's customized content stays inside the wrappers as-is.
6. Apply the `docs/CLAUDE_SNIPPET.md` drift fix to the project's `CLAUDE.md` if it has a Post-Feature Gates section with "11 items" or "Item 8" references (legacy pre-v5.1 / v5.3 wording).

If the project is on v5.3 already (the most common case), this is a 10-minute upgrade. If older, the upgrade prompt walks all intervening steps automatically.

---

## [v5.3] — 2026-05-23

**Minor — upgrade infrastructure: machine-readable version marker + automated upgrade prompt.** v5.1 introduced the CHANGELOG and stripped per-file version stamps. That was half the versioning story — projects could read a changelog but couldn't tell an agent which version they were on, and there was no agent-runnable script to walk from old → new. v5.3 closes both gaps so the kit is now properly versioned end-to-end.

### Added
- **`docs/KIT_VERSION`** — single-line plain-text file containing the kit's current version (e.g. `5.3`). Ships with `docs/` so adopting projects get a copy of the marker. Future upgrades use this for fast version detection (no fingerprinting needed when the marker is present).
- **`docs/prompts/upgrade-kit.md`** — self-contained agent prompt that walks an in-project agent through a full kit upgrade end-to-end: version detection (KIT_VERSION marker, or fingerprint decision-tree for unmarked legacy projects), upgrade-path planning, sequential execution of migration prompts and patch-release inline edits, KIT_VERSION stamping at the end, and a fingerprint-based sanity check to verify the upgrade actually landed. Per-step commits make a botched upgrade recoverable.

### Changed
- **`CHANGELOG.md` location: kit root → `docs/CHANGELOG.md`.** Previously the changelog lived at `ai-dev-workflow-kit/CHANGELOG.md` and didn't travel with `docs/` when a project adopted the kit. Now it ships with `docs/`, so every adopting project has a local copy at `docs/CHANGELOG.md` — which both the user and the upgrade prompt can read directly.
- **`docs/README.md` "Versioning & changelog" section** — rewritten to reference `KIT_VERSION` (the marker file) and `upgrade-kit.md` (the automated upgrade prompt) by their in-`docs/` paths, instead of pointing up to the old kit-root CHANGELOG.

### Files touched
- `docs/CHANGELOG.md` — new location (moved from kit root). No content changes during the move.
- `docs/KIT_VERSION` — new file, content `5.3`.
- `docs/prompts/upgrade-kit.md` — new file (~120 lines).
- `docs/README.md` — Versioning & changelog section updated.

### Migration
- **For agents running the new upgrade prompt automatically (the common path):** this entry IS what the upgrade prompt will apply to your project. Mechanical: create `docs/KIT_VERSION` with the target version; copy `docs/prompts/upgrade-kit.md` from the new kit; if your project still has an old `CHANGELOG.md` at its root from prior adoption, move it to `docs/CHANGELOG.md` (or delete the root copy if your project never had one there).
- **For users running the upgrade manually:** same three steps, then commit.
- **For projects upgrading FROM v5.2 specifically:** this is a pure infrastructure release. No behavioural change to feature-lifecycle, agents, or skills.

---

## [v5.2] — 2026-05-23

**Minor — Phase 4 post-manual-test fix sub-agents escalate from Sonnet to Opus.** A bug that survives Sonnet's implementation pass, the Phase 3.5 code quality gate, and Phase 4 automated/browser testing has already escaped the model that's about to fix it. Re-dispatching the same model to fix what it just missed is low-yield; escalating to Opus gets fresh-eyes review with full context (bug description, failing reproduction, PRD/ADR, scope guardrails). Single-chat mode already does this implicitly — the main orchestrator runs on Opus — so the change is concentrated in orchestrator mode.

### Changed
- **Phase 4 fix sub-agent model (manual-test fix loop only): `sonnet` → `opus`.** Each per-bug fix sub-agent now dispatches on Opus. Input contract unchanged. Triggers only after the manual testing handoff — pre-handoff fix work is not affected.
- **Model split table** (`feature-lifecycle.md` top) — row for "Phase 4 fix sub-agents" updated with new model + rationale + "post-manual-test loop only" qualifier.
- **Phase 4.0 manual-test fix loop step 2** — `Model: sonnet` → `Model: opus` with a TL;DR pointing at the model split table.
- **Orchestrator-mode mental-model diagram** (appendix) — `[Fix sub-agent] ── SONNET` → `[Fix sub-agent] ── OPUS`, with the "fresh-eyes review" rationale.
- **"Model split at a glance"** closing summary — Phase 4 post-manual-test fix sub-agents moved from the Sonnet list to the Opus list; Phase 4 automated sub-agent (4.1–4.3) explicitly called out as still Sonnet.

### Unchanged
- **Phase 3 implementation sub-agent** — still Sonnet. First-attempt execution against an approved plan; no prior failure to escape from.
- **Phase 4 automated sub-agent (4.1–4.3)** — still Sonnet. Pre-human-test failures are usually mechanical (typos, dev-server config, missing assertions); Sonnet can fix its own automated-test failures without escalation. Only human-found bugs route to Opus.
- **Phase 5.5 code-quality-agent and all named sub-agents in `.claude/agents/`** — still Sonnet. Their work is gating/aggregation, not bug-fixing.

### Files touched
- `docs/prompts/feature-lifecycle.md` — four locations (model split table, Phase 4.0 step, mental-model diagram, closing summary).

### Migration
- Trivial — no new files, no state changes. Replace the four edits in your in-project copy of `feature-lifecycle.md`. No upgrade prompt needed; this changelog entry IS the migration guide.
- **Cost note:** Opus is roughly 5× the cost-per-token of Sonnet. This change adds cost ONLY in the post-manual-test fix loop, which should be a small fraction of total feature work if Phase 3.5 and Phase 4 automated testing are doing their job. If you find this loop runs often, that's a signal Phase 3.5 / 4.x have a gap worth closing via the existing `test-suite-retro.md` and `testing-retro.md` flows — Opus fix-sub-agents should be the exception, not the norm.

---

## [v5.1] — 2026-05-23

**Patch — upstream rename absorbed: `/simplify` was merged into `/code-review` by Anthropic in late May 2026.** The merged skill accepts an effort level; `/code-review high` is the deeper-effort successor that covers both the old correctness review and the old DRY / reuse / complexity pass in a single invocation. No workflow restructure; the code-quality gate just has one fewer item.

### Changed
- **Code Quality Gate item counts.**
  - Phase 3.5 (Pre-Testing Mode) gate: **5 items → 4 items** (+ process sweep on line 5). The block now lists `/code-review high`, `/vulnerability-scanner` (pass 1), `/performance`, `/coding-standards`.
  - Phase 5.5 (Post-Manual-Testing Mode) gate: **11 items → 10 items**. The second vulnerability-scanner pass moves from line 8 to line 7. `CLEAN` requirement still applies to the second pass.
- **Phase 3.3 TDD cycle step 4.** `Run /simplify` → `Run /code-review`. For tasks introducing significant new logic, `/code-review high` is suggested.
- **Skills map in `feature-lifecycle.md`.** Removed `/simplify` entry. Added a note on `/code-review` that `/code-review high` covers the old DRY / reuse scope.

### Removed
- **Section 5.3 (`/simplify`)** from `.claude/agents/code-quality-agent.md`. Sections 5.4 → 5.10 renumbered down by one. Section 5.1 (`/code-review`) renamed to `/code-review high` and gains a one-line note about absorbing `/simplify`.

### Files touched
- `.claude/agents/code-quality-agent.md` — section removed, sections renumbered, gate-block formats updated, item-count references updated throughout.
- `docs/prompts/feature-lifecycle.md` — Phase 3.5 description, commit-discipline table row, skills map, Phase 3.3 TDD step 4, Phase 3.5.1 invocation, Phase 3.5.2 gate-output rule, Phase 5.5 delegation note, Phase 5.5 gate-output rule, Phase 5.8 gate enforcement, orchestrator-mode diagram.
- `docs/README.md` — "What's new in v5" callout, Gate #2 description.

### Migration
- Trivial. Find/replace `/simplify` with `/code-review high` in any project-level copies of these files. If a project's PRD or commit history references the 11-item gate, no action needed — the historical record stays as it was.
- No `docs/upgrading/v5-to-v5.1.md` written; the change is small enough that this changelog entry is the migration guide.

---

## [v5] — Code Quality Pre-Testing Gate

**Minor — adds Phase 3.5, upgrades code-quality-agent to dual-mode.** Catches code-quality findings before browser/manual testing so a Phase 5 refactor doesn't invalidate the testing the user just did.

### Added
- **Phase 3.5 (Code Quality Gate — Pre-Testing)** between Phase 3 (Implementation) and Phase 4 (Verification). Invokes the code-quality-agent in Pre-Testing Mode.
- **Phase 3.5 row in the Phase Commit Discipline table** (`fix(<feature-id>): phase 3.5 — pre-test code quality fixes`).

### Changed
- **`code-quality-agent` is now dual-mode** — same file, two invocation modes. Pre-Testing Mode (Phase 3.5) runs the diagnostic skills only; Post-Manual-Testing Mode (Phase 5.5) runs the full gate including cleanup and second vulnerability scan.
- **Phase 5.5 framed as "second pass"** of the dual-mode agent. No behavioural change vs v4.
- **Orchestrator-mode diagram** updated to show the Phase 3.5 dispatch point.

### Files touched
- `.claude/agents/code-quality-agent.md` (upgraded from v1 → v2 — single-mode to dual-mode)
- `docs/prompts/feature-lifecycle.md`
- `docs/README.md`

### Migration
- See `docs/upgrading/v4-to-v5.md`. Surgical merge of `code-quality-agent.md`; optional three-way merge if `feature-lifecycle.md` has hand-edits. No new files, no bootstrapping.

---

## [v4] — Test Suite Self-Improvement (Gap A becomes persistent)

**Minor — makes the unit/integration test suite self-improving across features.** v3 made the browser testing-agent library self-improving (Gap B); v4 closes the parallel loop for the unit test suite (Gap A).

### Added
- `docs/context/Unit_Test_Writing_Guide.md` — universal anti-patterns A1–A11 + a `## Project Lessons` section that accumulates over time.
- `docs/prompts/test-suite-retro.md` — categorises every miss into one of 8 buckets and decides tier propagation.
- `docs/test-suite-misses.md` — chronological audit log of every test-suite miss.

### Changed
- **Phase 5.1 Gap A** now persistent. Invokes `test-suite-retro.md` instead of running inline-only. Generalisable lessons append to the Writing Guide.
- **Phase 3.3** reads `Unit_Test_Writing_Guide.md` before writing each failing test. Confirm-RED step is now mandatory.
- **Phase 2.4 PRD test markers** cite the relevant anti-pattern / lesson IDs from the guide.

### Files touched
- `docs/context/Unit_Test_Writing_Guide.md` (new)
- `docs/prompts/test-suite-retro.md` (new)
- `docs/test-suite-misses.md` (new)
- `docs/prompts/feature-lifecycle.md` (Phase 2.4 / 3.3 / 5.1 updates)
- `docs/prompts/bugfix.md` (Phase 1.4 + 2.1 + 2.4 updates)

### Migration
`migration-prompt-required` — see `docs/upgrading/v3-to-v4.md`. Walks state-audit → safe-copies → surgical-merge of two prompts → bootstrap three new files → verification. Two stop points for sanity checks.

---

## [v3] — Testing-Agent Library Becomes Persistent (Gap B)

**Minor — testing-agent library now improves over time, not just per-feature.**

### Added
- `docs/testing-agents/REGISTRY.md` — source of truth for which agents own which scopes.
- `docs/prompts/testing-retro.md` — for every manual-testing miss, identifies which agent should have caught it and either adapts the agent or creates a new one. Categorises the miss; logs to the agent's Miss Log; decides tier propagation (feature-only / project-pattern / skill-universal).

### Changed
- **Phase 5.1 Test Autopsy** now produces two closures per bug: Gap A (unit-test gap, existing) AND Gap B (testing-agent gap, new).
- **Phase 2.7 Testing Agent** is now a find-or-create against `REGISTRY.md` (Adapt Mode for existing agents).
- **Phase 4.1** base testing-agent reads each plan's Miss Log on pre-flight — historical gaps inform every run.

### Files touched
- `docs/testing-agents/REGISTRY.md` (new)
- `docs/prompts/testing-retro.md` (new)
- `docs/prompts/create-testing-agent.md` (Step 0 find-or-create, Adapt Mode)
- `.claude/agents/testing-agent.md` (pre-flight Miss Log read, new rule 18)
- `docs/prompts/bugfix.md` (Phase 2.4 auto-runs testing-retro.md)
- `docs/prompts/feature-lifecycle.md` (Phase 2.7 find-or-create, Phase 5.1 dual-gap autopsy)

### Migration
`migration-prompt-required` — see `docs/upgrading/v2-to-v3.md`. Surgical merges over wholesale copies; preserves `[CUSTOMIZE]` slot content in `testing-agent.md` and `code-quality-agent.md`; bootstraps existing per-feature test plans into the new REGISTRY.

---

## [v2] — Stop-Point Discipline, Plan Mode, Four-Gate Enforcement

**Major — restructures the lifecycle around explicit stops, structured planning, and gate-block proof.**

### Added
- **Phase 0** — execution mode choice (single-chat vs orchestrator/sub-agent-per-phase).
- **Engineering principles preamble** that primes Plan Mode.
- **Phase Commit Discipline** — every phase commits with body detail rich enough to drive Phase 5 reconciliation off git diffs alone.
- **Phase 4.2 FULL SUITE GATE** — verbatim test-summary-line proof; `docs/known-test-failures.md` / `docs/known-test-skips.md` discipline.
- **Phase 5.3** — rewrite PRD/ADR/Architecture to match reality.
- **Phase 5.4** — mandatory pre-merge full-suite re-run.
- **Four gates** instead of three — Phase 5.4 test-suite summary lines become Gate #1.

### Changed
- **Plan Mode** upgraded — multi-option tradeoff format (Effort / Risk / Impact / Maintenance + opinionated recommendation) and planning-time performance flags.

### Files touched
- `docs/prompts/feature-lifecycle.md` (substantial rewrite — adds Phase 0, Engineering Principles preamble, Phase Commit Discipline, Plan Mode multi-option tradeoff format, Phase 4.2 FULL SUITE GATE rewrite, new Phase 5.3 + 5.4, four-gate output rule, renumbered 5.x sections)
- `docs/known-test-failures.md` (new — empty baseline file referenced by Phase 4.2)
- `docs/known-test-skips.md` (new — empty baseline file referenced by Phase 4.2)

### Migration
`migration-prompt-required` — see `docs/upgrading/v1-to-v2.md` (added in v5.4 to close the previously-missing v1 → v2 path). Ten ordered edits to `feature-lifecycle.md` + two new empty side-files; two stop points for sanity checks.

---

## [v1] — Initial release

First version of the kit: PRD → ADR → TDD → manual-testing → docs lifecycle, single-chat execution, three gates at the end (code quality, context docs, docs auditor). Superseded by v2.

### Files touched
- Initial kit drop: `docs/prompts/feature-lifecycle.md`, `docs/prompts/bugfix.md`, `docs/prompts/debug.md`, `docs/prompts/create-testing-agent.md`, `.claude/agents/{testing,code-quality,context-docs,docs-auditor}-agent.md`, plus `docs/context/` templates.

### Migration
`trivial-noop` — initial release; no prior version exists to migrate from. Adoption uses `docs/README.md` Quick Setup, not an upgrade prompt.
