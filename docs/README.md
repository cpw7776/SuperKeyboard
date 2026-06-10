# AI Dev Workflow Kit

> **Drop this kit into any project's root to get a full AI-assisted development workflow.**

This kit provides a complete system for AI coding agents to build features, fix bugs, debug issues, and maintain documentation with minimal human intervention. It includes **universal prompt files** (used as-is), **sub-agent definitions** (universal but customizable for your stack), **context file templates** (you build these out for your project), and a **CLAUDE.md snippet** to wire everything together.

---

## Quick Setup

### 0. Initialize git (new projects only)

If your project isn't already a git repository, initialize one before doing anything else:

```bash
cd your-project
git init
git add .
git commit -m "chore: initial commit"
```

This is **just `git init`** — no GitHub remote required. The kit relies on local git for:

- **Phase Commit Discipline** — every phase ends with at least one commit (feature-lifecycle.md Phase 3+).
- **Phase 5.0 reconciliation** — Phase 5 drives off `git diff` / `git log` between phase commits, not transcript memory. Without commits, orchestrator mode loses its source of truth.
- **`git status` / `git diff` checks** — sub-agents and gate blocks inspect the working tree to detect uncommitted changes.

If `git status` doesn't work in this directory, the workflow will break before Phase 3 finishes. You can add a remote (GitHub, GitLab, self-hosted) later — none of the kit's protocols require one.

### 0.5 Classify the stack and pick the kit root (do this first — it shapes every later step)

The kit's templates default to a **web app driven through a browser**. Most of those defaults are right for a web project and wrong for everything else, so settle two things before copying anything:

**a) What kind of project is this?** Classify the testing surface — `web` · `mobile-native` (Android/iOS) · `desktop` (Tauri/Electron) · `python-data-app / pipeline` · `cli / library`. This single answer drives three downstream adaptations (all detailed in `docs/prompts/create-testing-agent.md` → **Stack Adaptation**):
- **testing-agent shape** — non-web surfaces need the stack-rewritten base agent (drives `./gradlew`/`pytest`/native build instead of agent-browser; RAM-hygiene targets that stack's processes, not chromium).
- **which slots are N/A** — a single-user/native/library project legitimately has *no* auth slot, *no* parallel-login capacity, *no* responsive dimension. Mark those slots `intentionally empty for <reason>` (don't delete them), and record the structural divergences in `docs/KIT_DEVIATIONS.md`.
- **gate count** — a project with no standalone automated suite runs **four** gates, not five (Gate #2 folds; see `feature-lifecycle.md` Phase 5.8).

If the stack is ambiguous, **stop and ask** rather than forcing browser shape onto a non-web project.

**b) Where does the kit root live?** The kit assumes a single repo root, but it works anywhere a `docs/` + `.claude/` pair can sit next to a `git` root. In a **monorepo or package subfolder**, install the kit at the package you're working in (e.g. `agent/docs/`, `app/.claude/`) — not the repo root — and treat that package dir as "root" for every path the kit mentions. Whatever you choose, `docs/KIT_VERSION` must live there; upgrades detect the kit root by finding `KIT_VERSION`, so keep the pair together.

### 1. Copy the folders

Copy both `docs/` and `.claude/` into your project root (or your chosen package subfolder — see 0.5b):

```
your-project/
├── .claude/
│   ├── agents/               ← Sub-agents invoked by feature-lifecycle.md (customize per-stack)
│   │   ├── testing-agent.md
│   │   ├── code-quality-agent.md
│   │   ├── context-docs-agent.md
│   │   └── docs-auditor-agent.md
│   ├── commands/             ← Project-level slash commands (use as-is — no customization)
│   │   └── reconcile.md      ← /reconcile → runs docs/prompts/reconcile-change.md
│   └── skills/               ← Optional project skills (use as-is — no customization)
│       ├── cmux-orchestrator/ ← in-repo fallback copy of the global cmux-orchestrator skill (travels to machines that lack ~/.claude/skills/; the global copy overrides it where present)
│       └── context-fit/      ← Gap D engine (v5.19+): read-only transcript sweep that finds undocumented subsystems and proposes context docs/indexes (auto-run at epic close by Phase 5.1)
├── docs/
│   ├── context/              ← Build these out for YOUR project (includes Unit_Test_Writing_Guide.md — universal anti-patterns + project lessons appended by retros)
│   ├── prompts/              ← Ready to use (universal — 3 deliberation stops + Phase 0 mode pick)
│   ├── mobile/               ← Mobile-platform reference docs (only if your project ships a mobile build)
│   ├── prd/                  ← Empty — PRDs created per feature
│   ├── ard/                  ← Empty — ADRs created per feature
│   ├── architecture/         ← Empty — Architecture docs created per feature
│   ├── plans/                ← Empty — Plan docs created per feature
│   ├── bugs/                 ← Empty — Bug reports created as needed
│   ├── testing-agents/       ← Library of persistent test plans (one per scope) + REGISTRY.md
│   │   └── REGISTRY.md       ← Index of all testing agents (find-or-create source of truth)
│   ├── test-suite-misses.md  ← Chronological log of unit/integration test misses (appended by test-suite-retro.md)
│   ├── known-test-failures.md  ← (recommended) date-stamped, approved pre-existing failures
│   └── known-test-skips.md     ← (recommended) baseline skip count + reasons
├── CLAUDE.md                 ← Add the snippet from docs/CLAUDE_SNIPPET.md
└── ...
```

> **The two recommended files** at the bottom (`known-test-failures.md`, `known-test-skips.md`) make pre-existing test problems auditable instead of invisible. Phase 4.2 of `feature-lifecycle.md` enforces them. Create them as empty files on the first feature; append to them as features touch the test baseline. Without them, "those failures are unrelated to my feature" becomes a free pass.

### 2. Build out your context files

The `docs/context/` folder contains **templates** with placeholders. You need to fill these in for your project:

| File | What to do | Effort |
|------|-----------|--------|
| `Context_Index_File.md` | **Build out fully** — catalog every file in your project | Medium-High |
| `Project_PDR.md` | **Build out fully** — your tech stack, architecture, patterns | Medium |
| `API_REFERENCE.md` | **Build out fully** — all your API routes | Medium-High |
| `database_reference_guide.md` | **Build out fully** — all tables, columns, RLS, migrations | Medium-High |
| `CHANGELOG.md` | **Start fresh** — begin logging changes from now | Low |
| `PRODUCTION_READY.md` | **Build out** — your roadmap/release criteria | Low-Medium |
| `Project_Authentication.md` | **Build out** — your auth flow, session handling | Medium |
| `Unit_Test_Writing_Guide.md` | **Use as-is** — universal anti-patterns are pre-written; `## Project Lessons` accumulates from `test-suite-retro.md` over time | None initially |

**Tip:** Ask the AI agent to help build these out. Point it at the template and your codebase and say: *"Read this template and build out the context file for this project."*

### 3. Customize the sub-agents

Each sub-agent in `.claude/agents/` has `[CUSTOMIZE]` markers for project-specific bits:
- `testing-agent.md` — dev server URL, auth model, credential env vars
- `code-quality-agent.md` — test/build commands, test-process name for `pkill`
- `context-docs-agent.md` — target file list (if your context files differ from the 7 defaults)
- `docs-auditor-agent.md` — decision trees for your doc surfaces (Help Center paths, FAQ path, tour component path, legal page paths)

**Model setting.** The kit follows one principle: **Opus owns the critical path (planning, implementation, gate-quality decisions, post-manual-test fixes); Sonnet owns structured execution (walking test plans and decision trees).** Updated in v5.5 — Phase 3 implementation and `code-quality-agent` were promoted from Sonnet to Opus.

- **Opus** runs the orchestrator (`feature-lifecycle.md`) — which as of **v5.7 also owns Phase 2 planning end-to-end inline** (Plan Mode + PRD + ADR + Feature Architecture + plan snapshot), the Phase 3 ad-hoc sub-agent (TDD implementation), every Phase 4 manual-test fix sub-agent (fresh-context debugging), `code-quality-agent` (Pre-Test and Post-Test), and Phase 5 reconciliation. Anywhere a decision is being made, judged, or debugged.
- **Sonnet** runs `testing-agent`, `context-docs-agent`, and `docs-auditor-agent` — the three named sub-agents whose work is walking an explicit decision tree (test plan, per-file context update, per-surface doc audit). They ship with `model: sonnet` in their own frontmatter; the orchestrator doesn't need to override on dispatch.
- **Phase 3 override (rare).** For genuinely mechanical work (boilerplate scaffolds, generated code, pure renames), the orchestrator MAY dispatch the Phase 3 sub-agent on `sonnet` — call out the override with a one-line reason on dispatch.

The full role/model table lives at the top of `docs/prompts/feature-lifecycle.md`. If you flip a specific named sub-agent's model for some project-specific reason, change its `model:` field — but the default split is intentional and load-bearing.

### 4. Add the CLAUDE.md snippet

Copy the contents of `docs/CLAUDE_SNIPPET.md` into your project's `CLAUDE.md` file (create one if it doesn't exist). This tells the AI agent where everything is and how to use it.

### 5. Start using the prompts

The prompts in `docs/prompts/` are ready to use immediately:

| Prompt | When to use | How to use |
|--------|------------|-----------|
| `feature-lifecycle.md` | Building a new feature or epic | Paste into chat, replace `[INSERT EPIC/FEATURE ID]` |
| `reconcile-change.md` | **After unplanned work** (an ad-hoc fix / small feature done with no PRD/ADR/tests-first) — backfills tests, updates architecture/PRD/ADR if touched, syncs context + user-facing docs. The reverse of the lifecycle. | Type `/reconcile`, or paste the prompt. Confirms the change set, then runs |
| `bugfix.md` | Fixing a known bug | Paste into chat, replace `[INSERT BUG DESCRIPTION]` |
| `debug.md` | Diagnosing an unknown issue | Paste into chat, replace `[INSERT ISSUE DESCRIPTION]` |
| `create-testing-agent.md` | Maintain the testing-agent library (find-or-create + Adapt Mode). Auto-invoked during feature lifecycle Phase 2.7 and from `testing-retro.md` Phase 3.2 | Referenced automatically — no manual action needed |
| `testing-retro.md` | **Gap B** — self-improvement loop for the browser testing-agent library. Run when manual testing or production finds a bug the testing-agent library should have caught. Auto-invoked from `bugfix.md` Phase 2.4 and `feature-lifecycle.md` Phase 5.1 | Paste into chat, replace `[INSERT BUG DESCRIPTION + WHICH AGENT(S) RAN]` |
| `test-suite-retro.md` | **Gap A** — self-improvement loop for the unit/integration test suite. Run when a bug escaped unit tests; categorises the miss, logs to `test-suite-misses.md`, appends a Project Lesson to `Unit_Test_Writing_Guide.md` if generalisable. Auto-invoked from `bugfix.md` Phase 1.4 + 2.4 and `feature-lifecycle.md` Phase 5.1 | Paste into chat, replace `[INSERT BUG DESCRIPTION + WHICH TEST(S) PASSED OVER IT]` |

### 6. Bootstrap a minimal test harness (fresh projects with no tests yet)

The kit's lifecycle is **tests-first** (Phase 3 is red-green TDD; Phase 4.2 is a FULL SUITE GATE). A brand-new project often has **nothing to run** on day one — no test runner wired, no `test/` source set, no first test. Before the first feature, stand up the smallest harness that makes the lifecycle runnable:

- Install/wire the stack's standard runner and add a **single trivial passing test** so the FULL SUITE GATE has something green to report (`vitest`/`jest` for JS/TS, `pytest` for Python, the Gradle/`androidTest` test source set for Android, `go test`/`cargo test` for Go/Rust, etc.).
- Confirm the **single-run** command works (`npm run test:run`, `pytest`, `./gradlew test` — never watch mode) and record it in the `code-quality-agent.md` test-command slot.
- Create the two recommended baseline files as empty: `docs/known-test-failures.md`, `docs/known-test-skips.md`.

If the project's stack has **no automated test story at all** (e.g. a sideload-only native app), skip this — the lifecycle correctly runs **four** gates and Phase 4 becomes a build/sideload pipeline (see 0.5a and `feature-lifecycle.md` Phase 5.8). The point is to *decide* deliberately, not to discover on the first feature that Phase 4.2 has nothing to gate.

---

## What's Universal vs. What's Project-Specific

### Universal (use as-is)
- `docs/prompts/feature-lifecycle.md` — **v5.** Full feature development cycle with **Phase 0 mode pick + 3 deliberation stops**, sub-agent delegation for Phases 4 + 5, **Phase Commit Discipline**, **multi-option tradeoff Plan Mode**, **FULL SUITE GATE** at Phase 4.2, **PRD/ADR rewrite** at Phase 5.3, **pre-merge full-suite re-run** at Phase 5.4, an **unconditional merge-blocking Retrospective Gate** at Phase 5.1, and **five verbatim gate blocks** before commit. **v5:** the Phase 5.1 retro is now unconditional and merge-blocking (Gate #1 of five), and gains **Gap C** — implementation/code lessons captured into `docs/context/Implementation_Patterns.md`, the code-side mirror of the test guide, read at the IMPLEMENT step of every Phase 3 TDD cycle. **v4:** Phase 3.3 reads `Unit_Test_Writing_Guide.md` before every failing test; Phase 5.1 Gap A invokes `test-suite-retro.md` so unit-suite misses persist and generalise.
- `docs/prompts/bugfix.md` — **v4.** Bug investigation and fix protocol. **v4 (kit v5.14):** Phase 2.4 runs a third retro — **Gap C** (implementation/code lesson → `docs/context/Implementation_Patterns.md`, Tier 1/2/3) — a bug fix being the richest source of code lessons; Phase 2.2 reads the code guide before fixing. **v3:** Phase 1.4 Test Autopsy uses the eight-category vocabulary from `test-suite-retro.md`; Phase 2.4 runs Gap A (always) and Gap B (when bug escaped past Phase 4).
- `docs/prompts/debug.md` — Systematic diagnosis protocol
- `docs/prompts/reconcile-change.md` — **NEW in kit v5.9.** Standalone *reverse-lifecycle* prompt for unplanned work — you fixed/added something directly (no PRD/ADR, no tests-first) and want the project caught up afterwards. Backfills tests (reverse-TDD: asserts intended behaviour, so a red test catches a real defect), captures durable test/code lessons (the lightweight mirror of the lifecycle's Gap A / Gap B / Gap C retro), conditionally updates architecture/PRD/ADR, and syncs the context + user-facing docs. Reuses `context-docs-agent` and `docs-auditor-agent` **by reference** (run inline, not dispatched), so your per-project customizations flow through. Launched by `/reconcile` (`.claude/commands/reconcile.md`).
- `docs/prompts/create-testing-agent.md` — **v3.1.** Test plan generator with Step 0 find-or-create against REGISTRY.md and Adapt Mode for retros. **v5.10+:** every plan is classified `parallel_safe` (read-only / distinct-login / isolated-backend) so Phase 4 can run independent plans concurrently when the machine allows.
- `docs/prompts/testing-retro.md` — **Gap B retro (kit v3).** Self-improvement loop for the browser testing-agent library: root-cause the miss, adapt the responsible agent (or create a new one), append to Miss Log, decide tier propagation (feature / project pattern / skill-universal)
- `docs/prompts/test-suite-retro.md` — **NEW in kit v4. Gap A retro.** Self-improvement loop for the unit/integration test suite: categorise the miss into one of eight buckets mapped to anti-patterns, log to `test-suite-misses.md` (always), append a Project Lesson to `Unit_Test_Writing_Guide.md` if generalisable (Tier 2), propose a universal anti-pattern change with user approval if cross-project (Tier 3)
- `docs/context/Unit_Test_Writing_Guide.md` — **NEW in kit v4.** Universal anti-patterns A1–A11 + accumulating `## Project Lessons` section. Read at Phase 3.3 (before every TDD failing test) and Phase 2.1 of bugfix. Updated only by `test-suite-retro.md` (no hand-edits).
- `docs/test-suite-misses.md` — **NEW in kit v4.** Chronological audit log of every unit/integration test miss. Append-only, written by `test-suite-retro.md`. Read during pattern-checks to spot Tier-2 candidates.
- `docs/testing-agents/REGISTRY.md` — **kit v3.** Source-of-truth index of all browser testing agents in a project. Read for find-or-create; updated by `create-testing-agent.md` and `testing-retro.md`.
- `docs/mobile/Android_Build_and_Sideload.md` — Universal Android APK build + sideload reference. Covers both **native Android (Kotlin DSL)** and **Capacitor (web-wrapped, Groovy DSL)** projects. Persistent debug keystore pattern, `apksigner` verification, Syncthing delivery loop, troubleshooting catalogue, copy-paste one-liners for both project types. Read this first if your project ships an APK to the developer's own phone.

### Universal-with-customize (edit the `[CUSTOMIZE]` markers)
- `.claude/agents/testing-agent.md` — dev server URL, auth model, credentials
- `.claude/agents/code-quality-agent.md` — test/build commands, process names
- `.claude/agents/context-docs-agent.md` — target file list
- `.claude/agents/docs-auditor-agent.md` — decision-tree targets + doc paths

### Project-specific (build out from templates)
- Everything in `docs/context/` — Templates with `[PLACEHOLDER]` markers
- `docs/CLAUDE_SNIPPET.md` — Customize tool/skill references for your stack

---

## Folder Purpose Reference

| Folder | Purpose | Who creates files |
|--------|---------|-------------------|
| `.claude/agents/` | Sub-agents invoked by feature-lifecycle.md | You (copy from this kit, then customize) |
| `.claude/commands/` | Project-level slash commands (kit v5.9+ — `/reconcile`) | You (copy from this kit — no customization) |
| `.claude/skills/` | Optional project skills (kit v5.18+ — `cmux-orchestrator`; kit v5.19+ — `context-fit`, the Gap D engine auto-run at epic close); opt-in/removable. For `cmux-orchestrator` the user-level copy at `~/.claude/skills/` overrides the in-repo one where present | You (copy from this kit — no customization) |
| `docs/context/` | Project-level source of truth (7 files, always current) | You (initial), AI (maintains via context-docs-agent) |
| `docs/prompts/` | Workflow prompts for the AI agent | You (this kit provides them) |
| `docs/prd/` | Product Requirements Documents (one per feature) | AI (during feature lifecycle Phase 2) |
| `docs/ard/` | Architecture Decision Records (one per feature) | AI (during feature lifecycle Phase 2) |
| `docs/architecture/` | Feature architecture docs (button-to-DB flows) | AI (during feature lifecycle Phase 2) |
| `docs/plans/` | Implementation plan snapshot + retrospective (dual-purpose) | AI (snapshot in Phase 2.3, retrospective in Phase 5.2) |
| `docs/bugs/` | Bug reports with investigation + resolution | AI (during bugfix protocol) |
| `docs/testing-agents/` | Persistent library of test plans (one per scope) + `REGISTRY.md` index | AI (Phase 2.7 find-or-create, `testing-retro.md` adapts) |

---

## How the System Works

```
You write a rough epic/idea
        ↓
Feature Lifecycle prompt (Phase 0 mode pick + 3 deliberation stops, rest autonomous)
        ↓
Phase 0:   AI asks "single-chat or orchestrator?" → one-click pick
        ↓
Phase 1:   AI asks ALL questions, shapes the feature → YOU APPROVE SCOPE
        ↓
Phase 2.1: AI enters plan mode (multi-option tradeoffs + perf flags) → YOU APPROVE PLAN
Phase 2.2–2.8: AI writes PRD/ADR/Architecture/plan-snapshot/test-plan, commits
        ↓
Phase 3: AI implements (TDD, incremental commits per Phase Commit Discipline)
        ↓
Phase 3.5: code-quality-agent in Pre-Testing Mode → CODE QUALITY GATE (Pre-Test)
        ↓
Phase 4: testing-agent runs browser tests → FULL SUITE GATE (verbatim summary lines)
         → YOU DO MANUAL TESTING (in orchestrator mode, fixes go through fix sub-agents)
        ↓
Phase 5: AI reconciles plan vs reality FROM GIT DIFFS (5.0 / 5.1),
         UNCONDITIONAL RETROSPECTIVE SWEEP over every finding + fix commit (5.1 — Gate #1),
         updates plan doc as learning artefact (5.2),
         REWRITES PRD/ADR/Architecture to match reality (5.3),
         RE-RUNS FULL SUITE pre-merge (5.4 — Gate #2),
         delegates to code-quality-agent (Gate #3),
                       context-docs-agent (Gate #4),
                       docs-auditor-agent (Gate #5),
         all five verbatim gate blocks must appear before commit
         (four on suite-less stacks — no Test-Suite gate; build/sideload proof folds into Gap A).
```

Bugs and debug issues use standalone protocols with one stop point each (after investigation/diagnosis, before fix).

### Single-chat vs Orchestrator Mode

Phase 0 lets the user pick how the feature runs:

- **Single-chat mode (default)** — All phases run in the main chat. Best for small/medium features that fit comfortably in one context window.
- **Orchestrator mode** — Main agent acts as conductor. Phases 2, 3, and the automated portion of Phase 4 run in **sequential sub-agents** with isolated context. Phase 5 reconciliation drives off git diffs per phase commit, not transcript memory. Best for long or multi-phase epics where the manual-test → fix → re-test loop tends to bloat context.

Either way, Phase Commit Discipline applies: every phase ends with at least one commit, with a body detailed enough to drive Phase 5 reconciliation without re-reading the chat.

---

## Why sub-agents with verbatim gate blocks?

The Phase 5 delegation pattern exists because inline Phase-5 execution was historically lossy:

- "I ran the code review and everything looks good" — no proof, tests may or may not have run.
- "Documentation is up to date" — no proof, CHANGELOG entry often forgotten.
- Security findings deferred to "follow-up PRs" that never land.

Each sub-agent ends its run by printing a **canonical gate block**. The main agent pastes that block into the transcript **verbatim** — that's the mechanical proof the work was done. If the gate is missing, truncated, paraphrased, or shows an unresolved failure, Phase 5 is not complete and the feature can't ship.

**Five gates total**, in this order, before commit:

1. **`RETROSPECTIVE GATE:`** (Gate #1) — verbatim from Phase 5.1, authored by the main agent. An **unconditional** retro sweep over every manual-test finding ∪ `fix(` commit of the phase: each finding either produces a durable lesson (Gap A unit-guide + miss-ledger, Gap B testing-agent Miss Log + testing-pattern, and/or **Gap C implementation/code lessons → `docs/context/Implementation_Patterns.md`**) or is NAMED on the "deliberately NOT lessoned" line with a reason. A zero-finding epic still prints the block with every line `none`. This replaces the old conditional "if manual testing revealed bugs" retro — which got skipped precisely because it was skippable.
2. **Test-Suite Summary Lines (Gate #2)** — verbatim final summary lines from your test runner, captured during Phase 5.4's pre-merge re-run. "Tests pass" without the lines is not acceptable. Failures must already be documented in `docs/known-test-failures.md` or the feature can't merge.
3. **`CODE QUALITY GATE:`** (Gate #3) — 10 items, including **two** vulnerability-scanner passes (one early, one post-cleanup). Cleanup can introduce new vulns; the second pass catches them.
4. **`CONTEXT DOCS GATE:`** (Gate #4) — one line per context file, plus the epics tracker. The CHANGELOG line can only show `Entry added — MANDATORY` — no exceptions.
5. **`DOCUMENTATION GATE (User-Facing):`** (Gate #5) — every decision-tree question answered YES/NO with evidence. "No update needed" without per-question answers is a gate failure.

---

## Upgrading existing projects

**Don't just overwrite the files** — you'll clobber the `[CUSTOMIZE]` content you filled in (dev server URL, credentials, auth model, etc.) and the project state (testing-agent library, miss logs). Run the appropriate migration prompt:

| From | To | Migration prompt |
|------|-----|------------------|
| v2 | v3 | `docs/upgrading/v2-to-v3.md` |
| v3 | v4 | `docs/upgrading/v3-to-v4.md` |
| v4 | v5 | `docs/upgrading/v4-to-v5.md` |

Each migration walks an agent through state audit → safe copies → surgical merge → bootstrapping → verification sweep, with stop points for sanity-checking before destructive changes.

**If you're starting a new project**, ignore the migration prompts and follow "Quick Setup" above — drop the kit in fresh at its current version.

**Multi-version jumps**: run the migrations in order (e.g. v2 → v5 = v2-to-v3, then v3-to-v4, then v4-to-v5).

---

## Versioning & changelog

The kit is versioned as a single bundle, not per-file. The current version is recorded in **[`KIT_VERSION`](KIT_VERSION)** (machine-readable, ships with `docs/` to adopting projects), and a release-by-release breakdown of what changed (added / changed / removed, plus the exact files touched) lives in **[`KIT_CHANGELOG.md`](KIT_CHANGELOG.md)**. (Renamed from `CHANGELOG.md` in v5.6 to disambiguate from `context/CHANGELOG.md` — the project's feature log maintained by `context-docs-agent`.) The project's deliberate deviations from the kit baseline are tracked in **[`KIT_DEVIATIONS.md`](KIT_DEVIATIONS.md)** so future upgrades can tell them apart from surprises.

Upgrading an existing project is automated by **[`prompts/upgrade-kit.md`](prompts/upgrade-kit.md)** — a self-contained prompt an agent reads and executes. It detects the project's current version (via `KIT_VERSION` if present, or by fingerprinting for unmarked legacy projects), walks the kit changelog forward, runs the relevant `docs/upgrading/vX-to-vY.md` migration prompts for substantive releases, applies inline diffs for patch releases, and stamps the project with the new `KIT_VERSION` at the end.
