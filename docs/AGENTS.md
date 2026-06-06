# AGENTS.md — How to work with the AI Dev Workflow Kit

> **Read this first if you're an AI agent in a project that uses this kit.** It's the shortest path to operating correctly. ~5 minute read. Everything else in `docs/` is detail you reach for when you need it.

The kit exists so agents working in this project don't have to invent process. There's a defined lifecycle, defined sub-agents, defined gates, and defined places where customization is preserved across upgrades. Follow the structure — don't improvise.

---

## What this kit is

A structured workflow for AI-driven software development. It defines:

- **A 5-phase feature lifecycle** (`docs/prompts/feature-lifecycle.md`) with 3 human stop-points and everything else autonomous.
- **5 specialized sub-agents** (in `.claude/agents/`) that take over specific phases — testing, code quality (×2 modes), context docs, user-facing docs.
- **4 verbatim gate blocks** that must appear in the transcript before any feature can ship.
- **A small set of side-prompts** for non-feature work (bug fixing, debugging, test-suite retro, testing-agent retro, kit upgrades, creating per-feature test plans).

It assumes you're working in **Claude Code** with access to `Edit`, `Read`, `Write`, `Bash`, `Grep`, `Glob`, and the `Skill` tool for invoking slash-commands like `/code-review high`, `/vulnerability-scanner`, `/performance`, `/coding-standards`.

---

## The lifecycle at a glance

```
Phase 0  ── Execution mode pick (single-chat or orchestrator) — INTERACTIVE
Phase 1  ── Kickoff & Requirements                            — INTERACTIVE, batch ALL questions here
Phase 2  ── Planning (Plan Mode, multi-option tradeoffs, PRD, ADR, Feature Arch, plan snapshot) — AUTONOMOUS, Opus
Phase 3  ── Implementation (TDD per PRD task)                 — AUTONOMOUS, Opus
Phase 3.5── Code Quality Gate (Pre-Test)                      — AUTONOMOUS, code-quality-agent in Pre-Testing Mode
Phase 4  ── Browser tests + full suite + mobile + STOP for manual testing
Phase 5  ── Reconciliation (plan-vs-reality, retrospective sweep, doc rewrites, full re-run, five gates, commit, merge)
```

Three stop-points exist where the agent waits for the human:
1. Phase 1.6 — scope approval
2. Phase 2.1 — Plan Mode approval
3. Phase 4 — manual testing handoff

Phase 0's mode pick is one click, not a deliberation.

---

## The 5 sub-agents

All live in `.claude/agents/`. Each is invoked by `feature-lifecycle.md` at specific phases. Each ends its work by printing a **verbatim gate block** the main agent pastes into the transcript unmodified.

| Sub-agent | Phase | Model | Output |
|---|---|---|---|
| `testing-agent.md` | 4.1 | Sonnet | PASS/FAIL/SKIP/**ABORT**/BLOCKED_NEEDS_FIXTURE/REQUIRES_INPUT block from browser tests (ABORT added in kit v5.7; the two structured verdicts in v5.8). **v5.10+: can run as one of N parallel agents** (own session + port + test user) when Phase 4.1's capability gate allows — see §0.5 in the agent file |
| `code-quality-agent.md` (Pre-Test mode) | 3.5 | Opus | `CODE QUALITY GATE (Pre-Test):` block (4 items + sweep) |
| `code-quality-agent.md` (Post-Test mode) | 5.5 | Opus | `CODE QUALITY GATE:` block (10 items) — **Gate #3** |
| `context-docs-agent.md` | 5.7a | Sonnet | `CONTEXT DOCS GATE:` block — **Gate #4** |
| `docs-auditor-agent.md` | 5.7b | Sonnet | `DOCUMENTATION GATE (User-Facing):` block — **Gate #5** |

**Gates #1 and #2 aren't sub-agents** — Gate #1 is the verbatim `RETROSPECTIVE GATE:` block the main agent authors in Phase 5.1 (the unconditional retro sweep over every manual-test finding + `fix(` commit), and Gate #2 is the verbatim test-suite summary lines from Phase 5.4's pre-merge full re-run. Both are captured by the main agent.

If you're invoked AS one of these sub-agents, read the agent file end-to-end before doing anything. The gate block format is canonical — don't paraphrase.

---

## The five gates (Phase 5.8 — no merge without all five)

Before commit, the main transcript must contain, in order:

1. **`RETROSPECTIVE GATE:`** — verbatim from Phase 5.1 (main agent). The unconditional retro sweep: `Findings swept: N` over every manual-test finding ∪ `fix(` commit, with Gap A (test suite) / Gap B (testing agent) / **Gap C (implementation/code lessons → `Implementation_Patterns.md`)** / planning / Tier-3 / deliberately-not-lessoned lines populated. A zero-finding epic still prints it with all lines `none`.
2. **Test-Suite Summary** — verbatim from Phase 5.4 (full suite re-run, no path arguments, no "tests pass" without proof)
3. **`CODE QUALITY GATE:`** — 10 items from `code-quality-agent.md` Post-Test mode. Item 7 (`/vulnerability-scanner` second pass) must show `CLEAN`.
4. **`CONTEXT DOCS GATE:`** — from `context-docs-agent.md`. CHANGELOG line must show `Entry added — MANDATORY`.
5. **`DOCUMENTATION GATE (User-Facing):`** — from `docs-auditor-agent.md`. Every decision-tree question answered YES/NO with evidence.

"I ran the checks and they were fine" without these blocks is NOT acceptable. The blocks ARE the proof.

---

## Skills the kit invokes

Via `/skill-name` slash-commands:

- `/code-review` (default) — quick correctness review. Use `/code-review high` for deeper passes that also cover DRY / reuse / complexity (absorbs the old `/simplify`).
- `/vulnerability-scanner` — security scan. Runs TWICE in Phase 5.5 (early + post-cleanup); second pass must be CLEAN.
- `/performance` — scoped to changed components/routes only.
- `/coding-standards` — language-specific style / standard violations.
- `/web-design-guidelines` — UI/UX and accessibility (Phase 5.6).
- `/technical-writing` — doc-quality pass during Phase 5.3 rewrites.
- `/agent-browser` — browser automation (used by `testing-agent` only — never invoke inline).
- `/tdd`, `/brainstorming`, `/architecture-patterns`, `/writing-plans`, `/using-git-worktrees` — phase-specific helpers.

If a skill isn't installed, the kit still runs but with reduced rigor. Don't substitute a different skill silently — surface the missing one.

---

## Customization conventions (v5.4+)

The kit ships universal templates. Projects customize specific slots — test commands, debug-grep patterns, project intros, surface paths.

### Slot markers

Every customizable region is wrapped in HTML-comment markers:

```markdown
<!-- KIT:SLOT-BEGIN <slot-name> -->
> **[CUSTOMIZE]** Default template content here.
<!-- KIT:SLOT-END <slot-name> -->
```

Inside shell code blocks the marker is comment-style:

```bash
# KIT:SLOT-BEGIN <slot-name>
pkill -f [TEST_PROCESS_NAME] 2>/dev/null || true
# KIT:SLOT-END <slot-name>
```

**Rule:** when customizing, replace the content **between** the markers, but **keep the markers themselves**. The upgrade prompt extracts content by slot name across versions. Markers without content = "slot is at default."

### Hand-edits outside slots

Some customizations don't fit a slot — added sections, gate-format extensions, project-specific blockquotes. These survive upgrades only via manual surgical-restore (or, in a future release, via baseline-snapshot three-way merge). Avoid them when a slot already exists.

### `[CUSTOMIZE]` keyword (legacy, pre-v5.4)

Older projects use the bare `[CUSTOMIZE]` keyword without slot markers. The upgrade prompt has a fallback that extracts those by grep + anchor. New customizations should use slot markers.

---

## Project-state files — DO NOT TOUCH

These belong to the project, not the kit. The upgrade flow and sub-agents must never modify them:

- `docs/prd/*.md` — Product Requirement Docs
- `docs/ard/*.md` — Architecture Decision Records
- `docs/architecture/*.md` — Feature Architecture docs
- `docs/plans/*.md` — Implementation plan snapshots
- `docs/bugs/*.md` — Bug reports
- `docs/integrations/*.md` — Third-party integration notes
- `docs/testing-agents/*-tests.md` — Per-feature test plans (registered in `REGISTRY.md`)
- `docs/test-suite-misses.md` rows — appended only by `test-suite-retro.md`
- `docs/context/*.md` — Filled-in context (project-specific; the kit ships templates only)
- `docs/known-test-failures.md` / `docs/known-test-skips.md` — Project baseline lists
- `state/`, `notes/`, source code under `src/` or equivalent

If you find yourself about to write to one of these in a kit operation, STOP and surface the intent.

---

## Versioning

- **`docs/KIT_VERSION`** — single-line marker, machine-readable. Current kit version.
- **`docs/KIT_CHANGELOG.md`** — release history, structured for mechanical patch-application. Every entry has `### Files touched` + `### Migration` (classified `trivial-noop` / `inline-edit` / `migration-prompt-required`). Distinct from `docs/context/CHANGELOG.md`, which is the project's feature-by-feature change log maintained by `context-docs-agent`.
- **`docs/KIT_DEVIATIONS.md`** *(optional but recommended)* — records this project's deliberate deviations from the kit baseline (slots intentionally left as template, structurally rewritten files, kit sections intentionally absent, sub-agent model overrides). Future upgrades read it to tell intentional deviations from surprises. Created at kit-adopt time as a near-empty template.
- **`docs/upgrading/vN-to-vM.md`** — full migration prompts for substantive releases.
- **`docs/prompts/upgrade-kit.md`** — the self-contained agent prompt that walks any version → current, runs migrations, applies inline patches, syncs CLAUDE.md, stamps KIT_VERSION.

The kit is versioned as a single bundle. Per-file version stamps were removed in v5.1. If you see `# Feature Lifecycle (v5)` style headings, the project is on a pre-v5.1 kit.

---

## When to STOP

These are non-negotiable stops:

1. **Confidence drops below 90%** on any requirement or decision. Surface as a multiple-choice question via `AskUserQuestion`.
2. **A skill or gate prints a failure** the agent can't fix in-scope.
3. **The upgrade prompt hits a customization conflict** (slot mismatch, hand-edit collision, drift suspicion).
4. **You're about to touch a file in the "DO NOT TOUCH" list** without explicit user instruction.
5. **A migration prompt's anchor isn't found** — don't guess the equivalent location.
6. **The git working tree has unexpected state** — uncommitted changes from another flow, an unfamiliar branch, lock files.

"I'll just figure it out" is the wrong instinct here. Cost of a stop: one user turn. Cost of guessing wrong: lost work, gate failures, drift.

---

## Common mistakes to avoid

- **Paraphrasing a gate block.** Gates are verbatim. Don't reformat, don't summarize, don't translate. Paste exactly what the sub-agent produced.
- **Running tests in watch mode.** Always single-run (`npm run test:run`, `pytest`, `vitest run`). Watch mode spawns persistent workers and overheats low-RAM machines.
- **Treating Phase 4.2 as a sub-agent.** It's not. The main agent (or its Phase 4 sub-agent) runs the FULL SUITE GATE and captures the verbatim summary lines.
- **Skipping `confirm RED` in TDD.** Write the failing test, run it, *confirm it fails for the right reason*, THEN implement. If a test passes before the code is written, it's testing the wrong thing.
- **Overwriting customizations.** Use `Edit`, not `Write`, for any file with slot markers or `[CUSTOMIZE]` history.
- **Leaving processes running.** After every test/build/agent run, sweep with `pkill`. Phase 0 and 5.11 in `code-quality-agent.md` are mandatory.
- **Bypassing the testing-agent.** Don't invoke `/agent-browser` or `mcp__playwright__*` directly from the main agent — go through `testing-agent.md` which has pre-flight + teardown for RAM hygiene.
- **Mixing model contexts.** As of v5.5 the critical-path agents (orchestrator — which now also owns Phase 2 inline per v5.7 — Phase 3 implementation, `code-quality-agent`, Phase 4 fix sub-agents, Phase 5 reconciliation) all run Opus. Sonnet stays on the structured-execution sub-agents (`testing-agent`, `context-docs-agent`, `docs-auditor-agent`) where the work is walking a fixed contract. Don't override silently — if a dispatch flips a model, call it out with a one-line reason.
- **Trusting a sub-agent's summary without reading the diff** (v5.7+). Sub-agents are isolated context windows; their return messages describe intent, not reality. After every dispatch in `feature-lifecycle.md`, the orchestrator MUST `git show <hash>` every commit the sub-agent reports (full diff for anything touching architecture). The Sub-Agent Supervision Protocol in `feature-lifecycle.md` formalizes this — structured returns, verify-before-trust, bounded scope. Phase 5.3 doc rewrites and Phase 5.7 sub-agent inputs are driven by `git log` and `git diff`, not orchestrator transcript memory.

---

## Quick reference — major prompts

| Prompt | When |
|---|---|
| `docs/prompts/feature-lifecycle.md` | New feature, end-to-end |
| `docs/prompts/reconcile-change.md` | After unplanned work — backfill tests + sync docs (reverse lifecycle; `/reconcile`) |
| `docs/prompts/bugfix.md` | Known bug, scoped fix |
| `docs/prompts/debug.md` | Unknown issue, diagnosis |
| `docs/prompts/create-testing-agent.md` | Generate a per-feature test plan (Phase 2.7) |
| `docs/prompts/test-suite-retro.md` | After a manual-test bug — close the unit-test gap |
| `docs/prompts/testing-retro.md` | After a manual-test bug — close the browser-test agent gap |
| `docs/prompts/upgrade-kit.md` | Upgrade the kit itself in this project |

---

## If you're confused

Read in this order:
1. This file (you're done).
2. `docs/README.md` — quick setup + folder reference.
3. `docs/prompts/feature-lifecycle.md` — the canonical lifecycle.
4. Whichever sub-agent file matches your role.
5. `docs/KIT_CHANGELOG.md` — what's changed recently and why. (Distinct from `docs/context/CHANGELOG.md`, which logs project features, not kit releases.)

If after that you're still uncertain, ask the user. The kit's bias is **structured pause over confident drift**.
