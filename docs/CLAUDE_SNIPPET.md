# CLAUDE.md Snippet — AI Dev Workflow Kit

<!-- KIT:SLOT-BEGIN intro-instructions -->
> **Instructions:** Copy the content below into your project's `CLAUDE.md` file. Customize the sections marked with `[CUSTOMIZE]` (and slot markers `KIT:SLOT-BEGIN/END`) for your project's specific tools and conventions.
<!-- KIT:SLOT-END intro-instructions -->

---

## Documentation System

This project uses a structured documentation system in `docs/`. **Read before you build.**

### Context Files — Source of Truth (`docs/context/`)

Before starting any work, read the relevant context files:

| File | Read when... |
|------|-------------|
| `docs/context/Context_Index_File.md` | Always — find any file in the project |
| `docs/context/Project_PDR.md` | Understanding architecture, patterns, tech stack |
| `docs/context/database_reference_guide.md` | Before ANY database changes |
| `docs/context/API_REFERENCE.md` | Before ANY API route changes |
| `docs/context/Project_Authentication.md` | Before ANY auth or RLS changes |
| `docs/context/PRODUCTION_READY.md` | Checking milestone status, known bugs |
| `docs/context/CHANGELOG.md` | Understanding recent changes |
| `docs/context/Unit_Test_Writing_Guide.md` | Before writing any failing test in TDD (universal anti-patterns + project lessons appended by `test-suite-retro.md`) |

### Workflow Prompts (`docs/prompts/`)

| Prompt | When to use |
|--------|------------|
| `docs/prompts/feature-lifecycle.md` | Full feature development — 3 stop points, otherwise autonomous |
| `docs/prompts/reconcile-change.md` | **After unplanned work** (reverse lifecycle, `/reconcile`) — you did an ad-hoc fix / small feature with no PRD/ADR/tests-first; this backfills the tests, updates architecture/PRD/ADR only if touched, and syncs the context + user-facing docs. Runs **inline** (reuses `context-docs-agent` + `docs-auditor-agent` as checklists, not dispatched). One stop point: confirm the change set |
| `docs/prompts/bugfix.md` | Investigating and fixing a known bug (auto-runs `test-suite-retro.md` for Gap A always, `testing-retro.md` for Gap B when bug escaped past Phase 4) |
| `docs/prompts/debug.md` | Diagnosing an unknown issue |
| `docs/prompts/create-testing-agent.md` | Maintain testing-agent library — find-or-create (Step 0 reads `docs/testing-agents/REGISTRY.md`) and Adapt Mode (called from `testing-retro.md`). Classifies each plan `parallel_safe` (v5.10+) so Phase 4 can run independent plans concurrently |
| `docs/prompts/testing-retro.md` | **Gap B** — self-improvement loop for the **browser testing-agent library**. Run when manual testing or production catches a bug a testing agent should have caught. Auto-invoked from `bugfix.md` Phase 2.4 and `feature-lifecycle.md` Phase 5.1 |
| `docs/prompts/test-suite-retro.md` | **Gap A** — self-improvement loop for the **unit/integration test suite**. Categorises into 8 buckets, logs to `docs/test-suite-misses.md` (always), appends a Project Lesson to `docs/context/Unit_Test_Writing_Guide.md` if generalisable. Auto-invoked from `bugfix.md` Phase 1.4 + 2.4 and `feature-lifecycle.md` Phase 5.1 Gap A |

### Sub-Agents (`.claude/agents/`)

These are invoked by `feature-lifecycle.md` — do not run them inline.

| Sub-agent | Invoked from | Produces |
|-----------|--------------|----------|
| `.claude/agents/testing-agent.md` | Phase 4.1 | PASS/FAIL/SKIP/**ABORT** test results block (verbatim). Reads each plan's Miss Log on pre-flight. Self-paces every browser action against an expected duration; ABORT is a reliability signal (the action hung), NOT a feature-under-test failure. Writes a live `progress.log` the user can `tail -f`. |
| `.claude/agents/code-quality-agent.md` (Pre-Testing Mode) | Phase 3.5 — between implementation and browser testing | `CODE QUALITY GATE (Pre-Test):` block (4 items + process sweep, verbatim). Pre-flight quality bar — NOT one of the five final gates. |
| `.claude/agents/code-quality-agent.md` (Post-Manual-Testing Mode) | Phase 5.5 — after manual testing | `CODE QUALITY GATE:` block (10 items, verbatim). Gate #3 in the five-gate final check. |
| `.claude/agents/context-docs-agent.md` | Phase 5.7a | `CONTEXT DOCS GATE:` block (verbatim) |
| `.claude/agents/docs-auditor-agent.md` | Phase 5.7b | `DOCUMENTATION GATE (User-Facing):` block (verbatim) |

### Model Split — Opus Owns the Critical Path, Sonnet Owns Structured Execution

The workflow follows one principle: **planning, implementation, and gate-quality decisions run on Opus; walking a fixed test plan or decision tree runs on Sonnet.** (Updated v5.5 — Phase 3 implementation and `code-quality-agent` were promoted from Sonnet to Opus.)

| Role | Model |
|------|-------|
| Orchestrator running `feature-lifecycle.md` | **Opus** — Phase 1 questions, **Phase 2 planning end-to-end** (Plan Mode + PRD + ADR + Feature Architecture + plan snapshot — inline in both modes as of v5.7), Phase 5 reconciliation, every sub-agent dispatch + supervision |
| Phase 3 ad-hoc sub-agent (orchestrator mode) | **Opus** — implementation is where invariant violations and hidden coupling originate (override to Sonnet only for purely mechanical scaffolding) |
| Phase 4 manual-test fix sub-agents | **Opus** — fresh dispatch with isolated bug context, priors-free reading the orchestrator can't get |
| `code-quality-agent` (Pre-Test and Post-Test) | **Opus** — set in agent frontmatter; gate outputs are decision-grade |
| `testing-agent`, `context-docs-agent`, `docs-auditor-agent` | **Sonnet** — set in each agent's own frontmatter; mechanical decision-tree walks |

**Switch the main chat to `opus` before invoking `feature-lifecycle.md`, `bugfix.md`, or `debug.md`.** Those prompts contain the planning and dispatching. If the main chat is on Sonnet, planning gets done on the wrong model and dispatched sub-agents inherit a weaker brain.

Rule of thumb: if you're *deciding, implementing, or judging quality*, you should be on Opus. If you're *walking a fixed test plan or decision tree*, Sonnet is the right tool.

### Sub-Agent Supervision (orchestrator mode — v5.7+)

Sub-agents are isolated context windows that can crash, context-exhaust, or stop silently — their return summary describes intent, not reality. `feature-lifecycle.md` defines a **Sub-Agent Supervision Protocol** with three rules the orchestrator MUST follow on every dispatch:

1. **Structured returns** — every sub-agent ends with a `SUB-AGENT RETURN` block (status / commits / files / gate / blockers / confidence). Re-dispatch if it's missing or prose-only.
2. **Verify-before-trust** — after every return, `git show <hash> --stat` every commit before doing anything else. `git show <hash>` in full for any commit touching architecture / data flow / public APIs. The diff is the source of truth; if it disagrees with the sub-agent's summary, trust the diff.
3. **Bounded scope** — split work that would plausibly take >~30 tool calls into sequential batches (PRD-task boundaries are the usual cut).

**Implication for docs work:** Phase 5.3 (PRD/ADR/Architecture rewrites) and Phase 5.7 (context-docs + docs-auditor inputs) are driven by `git log --format="%h %s%n%b%n---" main...HEAD` and `git diff main...HEAD` — NOT from orchestrator transcript memory. This catches the most damaging silent-drift class: a Phase 4 fix sub-agent quietly changing the architecture to make a bug-fix work, leaving the docs documenting the original plan while the code does something else.

(**Phase 2 runs inline in the orchestrator in both modes as of v5.7** — earlier kit versions dispatched it to a sub-agent, but Phase 2 was the highest-failure-rate dispatch in the kit and silent Phase 2 failures poisoned every downstream phase. The supervision protocol does NOT apply to Phase 2; it stays in-context.)

### Feature Documentation (created per feature)

| Folder | Contains |
|--------|---------|
| `docs/prd/` | Product Requirements Documents (what to build) |
| `docs/ard/` | Architecture Decision Records (how to build it) |
| `docs/architecture/` | Feature architecture docs (button-to-DB flows) |
| `docs/plans/` | Implementation plans + retrospectives |
| `docs/bugs/` | Bug reports with investigation and resolution |
| `docs/testing-agents/` | Persistent library of browser test plans + `REGISTRY.md` index. Each plan has a `## Miss Log` populated by `testing-retro.md`. |

### Documentation Rules

1. **Never skip doc updates.** Every feature must update every affected context file in `docs/context/`.
2. **Check before creating.** Search for existing PRD/ADR before creating new ones — update, don't duplicate.
3. **CHANGELOG is mandatory.** Every feature and every fix gets an entry. No exceptions.
4. **Context Index stays current.** Every new file created must be added to `docs/context/Context_Index_File.md`.
5. **PRDs match reality.** After implementation, rewrite the PRD to reflect what was actually built.

---

## Development Workflow

### Feature Development
Use `docs/prompts/feature-lifecycle.md` for all feature work. Three stop points:
1. Scope approval (after questions)
2. Plan mode approval (before writing docs)
3. Manual testing handoff (after automated testing)

### Ad-Hoc Work (reverse lifecycle)
Did an unplanned fix or small feature directly — no PRD/ADR, no tests-first? Run `/reconcile` (or use `docs/prompts/reconcile-change.md`) afterwards to catch the project up: backfill the tests, update architecture/PRD/ADR only if the change touches one, and sync the context + user-facing docs. One stop point: confirm the change set. Runs inline (reuses `context-docs-agent` + `docs-auditor-agent` as checklists — not dispatched). For an actual epic, use `feature-lifecycle.md` instead.

### Bug Fixes
Use `docs/prompts/bugfix.md`. One stop point after investigation, before applying the fix.

### Debugging
Use `docs/prompts/debug.md`. One stop point after diagnosis, before applying the fix.

---

<!-- KIT:SLOT-BEGIN commands -->
## [CUSTOMIZE] Commands

> Replace these with your project's actual commands.

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start development server |
| `npm run test:run` | Run tests (single run — NOT watch mode) |
| `npm run build` | Production build |
| `npm run lint` | Lint check |

### ⚠️ Test Command Safety — CRITICAL

**NEVER run tests in watch mode.** Watch mode spawns persistent worker processes that consume CPU indefinitely and will overheat low-memory machines.

- **Always use:** `npm run test:run` (or `npx vitest run`) — runs once and exits
- **Never use:** `npm test`, `npx vitest`, or any command that defaults to watch mode
- **Single file:** `npm run test:run -- path/to/file.test.ts`
- **If tests hang:** Kill immediately with `pkill -f vitest` and re-run with the `run` flag
<!-- KIT:SLOT-END commands -->

---

<!-- KIT:SLOT-BEGIN conventions -->
## [CUSTOMIZE] Conventions

> Add your project-specific coding conventions, naming patterns, and rules here. Examples:

- TypeScript strict mode — no `any` types
- All components use functional style with hooks
- API routes return consistent error shapes
- Tests live in `__tests__/` next to source files
- [YOUR CONVENTIONS]
<!-- KIT:SLOT-END conventions -->

---

<!-- KIT:SLOT-BEGIN skills-tools -->
## [CUSTOMIZE] Skills / Tools

> If you use Claude Code skills or custom tools, list them here with when to invoke each one. Remove this section if not applicable.
<!-- KIT:SLOT-END skills-tools -->

---

## Post-Feature Gates (FIVE — produced by Phase 5.1 + Phase 5.4 + three sub-agents)

Phase 5 of `feature-lifecycle.md` requires **five** verbatim gate blocks before committing. All five MUST appear in the main transcript, in this order:

> Suite-less stacks (native-mobile sideload, CLI build, data-app smoke run) run **four** (Retrospective + Code Quality + Context Docs + Documentation) — no Test-Suite gate block; the build/sideload proof + any suite lesson fold into the retro's Gap A (or a project-named build gate). See `feature-lifecycle.md` Phase 5.8; flag it with `Gate count: four (suite-less)` in `KIT_DEVIATIONS.md`.

1. **`RETROSPECTIVE GATE:`** — verbatim from Phase 5.1, authored by the main agent. The unconditional retro sweep: `Findings swept: N` over every manual-test finding ∪ `fix(` commit (`git log --grep="^fix(" <phase-2-end>..HEAD`), with Gap A (unit-guide lessons + miss-ledger rows), Gap B (testing-agent Miss Log + testing-pattern), Gap C (implementation/code lessons → `docs/context/Implementation_Patterns.md`), planning, Tier-3 proposals, and a "deliberately NOT lessoned" line that NAMES each polish/forward-scope/Tier-1 finding. A zero-finding epic still prints it with all lines `none`.
2. **Test-Suite Summary Lines** — verbatim final summary from your test runner, captured during Phase 5.4's mandatory pre-merge full-suite re-run. Zero unexplained failures (any failures must already be in `docs/known-test-failures.md`).
3. **`CODE QUALITY GATE:`** — 10 items, from `.claude/agents/code-quality-agent.md` in Post-Manual-Testing Mode. Item 7 (`/vulnerability-scanner (2)`) must show `CLEAN`. (A separate Pre-Test gate at Phase 3.5 — `CODE QUALITY GATE (Pre-Test):`, 4 items + process sweep — runs BEFORE manual testing and is NOT one of the five Phase 5.8 gates.)
4. **`CONTEXT DOCS GATE:`** — from `.claude/agents/context-docs-agent.md`. The CHANGELOG line must show `Entry added — MANDATORY`.
5. **`DOCUMENTATION GATE (User-Facing):`** — from `.claude/agents/docs-auditor-agent.md`. Every decision-tree question must be answered YES/NO with evidence.

**Note on Phase 4 fix sub-agents:** in orchestrator mode, per-bug fix sub-agents dispatched AFTER the manual testing handoff run on **Opus**. They get a fresh, isolated context (bug description + failing reproduction + PRD/ADR + scope guardrails), free of the priors the long-running orchestrator was carrying when the bug shipped. Pre-handoff automated-test fix work uses whatever model the Phase 4 automated sub-agent runs on (Sonnet per its frontmatter).

Do not commit until all five are present, verbatim, and none show an unresolved failure. "I ran the checks and everything was fine" is NOT acceptable — the gate blocks (and verbatim test-summary lines) are the proof.

## Recommended Discipline Files

These two files make pre-existing test problems auditable instead of invisible. Phase 4.2 of `feature-lifecycle.md` enforces them. Create them as empty files on the first feature; append to them as features touch the test baseline.

- **`docs/known-test-failures.md`** — date-stamped, user-approved pre-existing failures with follow-up tasks. Without this, "those failures are unrelated to my feature" is a free pass.
- **`docs/known-test-skips.md`** — baseline skip count + reasons. Skip-count drift handling lives here.

## Test Suite Self-Improvement (Gap A — kit v4)

The unit/integration test suite improves over time via three artefacts:

- **`docs/context/Unit_Test_Writing_Guide.md`** — read at every Phase 3.3 TDD failing test. Ships with 11 universal anti-patterns; accumulates project-specific lessons under `## Project Lessons`. Never hand-edit the lessons section — run a retro.
- **`docs/test-suite-misses.md`** — chronological log of every unit-test miss with category and tier. Appended by `test-suite-retro.md`.
- **`docs/prompts/test-suite-retro.md`** — the protocol that ties them together. Run any time a bug escaped the unit suite (auto-invoked from bugfix Phase 1.4 + 2.4 and feature-lifecycle Phase 5.1 Gap A).

The browser equivalent (Gap B) lives in `docs/prompts/testing-retro.md` + `docs/testing-agents/REGISTRY.md` + per-agent Miss Logs. Both systems are parallel and don't overlap.

<!-- KIT:SLOT-BEGIN mobile-android -->
## [CUSTOMIZE] Mobile (Android) Projects

If this project ships a debug-signed APK to the developer's own phone via sideload (native Android *or* Capacitor), the canonical build/sideload reference is `docs/mobile/Android_Build_and_Sideload.md`. Read it before touching the build pipeline — it covers the persistent debug keystore pattern (the load-bearing piece), `apksigner` signature verification, the Syncthing delivery loop, and troubleshooting for both project types.

Remove this section if the project has no mobile build.
<!-- KIT:SLOT-END mobile-android -->
