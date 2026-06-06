# Feature Lifecycle

> **Kit version:** see `docs/KIT_CHANGELOG.md` for the current version and what changed in each release. (Renamed from `docs/CHANGELOG.md` in v5.6; the unqualified `CHANGELOG.md` lived at the kit root in v5.0–v5.2 only.) This prompt lives at the kit's current version (no per-file version stamps).

> **This is the single prompt for all feature work.** Point the AI at an epic and let it run. There are only **three deliberation stop points** in the entire flow — everything else runs autonomously. Phase 0 adds a one-click execution-mode choice up front; it's not a deliberation stop.

> **Model split — Opus owns the critical path; Sonnet owns structured execution:**
>
> The kit follows a single principle: **planning, implementation, and gate-quality decisions run on Opus; structured execution of a decision tree or test plan runs on Sonnet.** Apply this rule whenever you're deciding which model an agent or sub-agent should use.
>
> | Role | Model | Why |
> |------|-------|-----|
> | **Orchestrator (this prompt)** | **`opus`** (top Opus — always the current highest-capability Opus; don't pin a version) | Owns Phase 1 scope shaping, **Phase 2 planning end-to-end** (Plan Mode + PRD + ADR + Feature Architecture + plan snapshot — kept in-context in both modes as of v5.7, see Phase 2.0), dispatches Phase 3 / 3.5 / 4 / 5 sub-agents, supervises them per the Sub-Agent Supervision Protocol below, and runs Phase 5 reconciliation. **Switch to `opus` before running this prompt.** |
> | **Phase 3 sub-agent** (orchestrator mode, ad-hoc) | **`opus`** | Implementation is where architectural intent meets code. Subtle invariant violations, hidden coupling, and "this test passes but tests the wrong thing" failures all originate here, and they are expensive to catch downstream. The cost delta to Sonnet is real but small relative to one missed bug that reaches Phase 4. Use Opus by default; only override down to Sonnet for genuinely mechanical work (boilerplate scaffolds, generated code, pure renames) and call out the override on dispatch. |
> | **Phase 4 fix sub-agents** (orchestrator mode, one per bug — **post-manual-test loop only**) | **`opus`** | A bug that reached human testing escaped implementation, the Phase 3.5 code-quality gate, and Phase 4 automated/browser testing. Even with Phase 3 on Opus, the orchestrator's long context can carry assumptions that caused the miss in the first place — a fresh fix sub-agent gets full context (bug description + failing reproduction + PRD/ADR + scope guardrails) without the priors that let the bug ship. Stay on Opus: this is the most consequential debugging in the entire lifecycle, and the cost is one focused dispatch per bug. (Single-chat mode does this implicitly — the orchestrator is already Opus.) |
> | **`code-quality-agent`** (Pre-Test and Post-Test modes) | **`opus`** (declared in the agent's frontmatter) | This agent's gate blocks are decision-grade outputs. A false negative on `/vulnerability-scanner`, `/code-review high`, or `/coding-standards` either lets a real defect ship (Post-Test mode) or wastes the manual-testing pass that's about to happen (Pre-Test mode). The underlying skills do most of the analysis, but aggregating and judging which findings warrant a fix-loop is itself a reasoning task. Worth Opus. |
> | **`testing-agent`, `context-docs-agent`, `docs-auditor-agent`** (declared in each agent's own frontmatter) | **`sonnet`** (top Sonnet — always the current highest-capability Sonnet; don't pin a version) | Structured execution: walking a test plan step-by-step, walking a per-file decision tree, walking a per-surface documentation decision tree. The contract is explicit; the work is mechanical aggregation against a checklist. Sonnet is sufficient and ~5× cheaper. |
>
> **Mental model:** Opus runs everywhere a decision is being made — planning, implementing, judging quality, debugging bugs that escaped earlier gates. Sonnet runs everywhere an explicit contract is being executed without judgment — test plans, decision trees, structured aggregation. If you ever find Sonnet making an architectural call, or Opus mechanically ticking off a checklist, the split has slipped — re-dispatch on the right model.

> **Stop Points:**
> 1. **Requirements & Scope Approval** — The AI reads your rough epic, presents its understanding, asks ALL questions to build out the full feature, then presents a scope table for approval.
> 2. **Plan Mode Approval** — The AI enters plan mode, reads the codebase, and presents a structured plan with multi-option tradeoffs, perf flags, and risks. You approve before it writes PRD/ADR docs.
> 3. **Manual Testing Handoff** — After implementation + automated testing, the AI stops for you (or a testing agent) to verify manually. Then it finishes everything.

---

## Engineering Principles (apply throughout — strongest in Plan Mode)

- **DRY** — flag duplication aggressively; reuse over re-implementation.
- **Well-tested code is mandatory** — better too many tests than too few.
- **Engineered enough** — not fragile or hacky, not over-engineered.
- **Correctness over speed of implementation** — handle edge cases first.
- **Explicit over clever** — clarity beats compactness.
- **Opinionated, not neutral** — when presenting options, recommend one and say why.

---

## Launch

Proceed with Epic/User Story/Feature: **[INSERT EPIC/FEATURE ID]**

---

## PHASE 0: Execution Mode Choice (INTERACTIVE — one question, no deliberation)

**Before Phase 1, ask the user how to run this feature.** Use `AskUserQuestion`.

> **"How do you want to run this feature?"**
>
> 1. **Single-chat mode (default)** — All phases run in this chat with the main agent. Best for small/medium features that fit comfortably in one context window.
>
> 2. **Orchestrator mode** — The main agent acts as a conductor. Phases 2, 3, and the automated portion of Phase 4 run in **sequential sub-agents** with isolated context. Phase 5 reconciliation uses **git diffs per phase commit** as the source of truth for what was actually built. Best for long or multi-phase epics where the manual-test → fix → re-test loop tends to bloat context.

**Rules for orchestrator mode:**
- **Sequential only (file-mutating phases).** Never run Phase 2/3/5 sub-agents in parallel — they'd race on the same files and overheat low-memory machines. **The one exception is Phase 4 browser testing (kit v5.10+):** read-only testing agents may run concurrently when capacity allows, because each is isolated on its own browser session + dev-server port + test user and edits no code. Phase 4.1 gates this on spare memory and distinct-login count.
- **Phase 1 always runs in main** (interactive).
- **Phase 4 STOP (manual testing) and Phase 5 always run in main** (Phase 5 needs the orchestrator's full picture: scope decisions, plan-mode plan, manual-test results, commit hashes).
- **Manual-test fix loop in orchestrator mode:** each fix is dispatched as a focused fix sub-agent (specific bug + failing test path + scope), commits with `fix(<feature-id>): <bug-summary>`, returns. Keeps the orchestrator's context clean across many iterations.

Track the chosen mode and apply the **mode-specific behavior** subsection in each phase below.

---

## Phase Commit Discipline (BOTH MODES — MANDATORY)

**Every phase ends with at least one commit. Commit messages must be detailed enough to drive the Phase 5 git-diff reconciliation without re-reading the chat.** This rule is not optional in either mode — single-chat agents lose accurate memory of what they built on long features just as readily as orchestrators do.

**Required commits per phase:**

| Phase | Commit message format | Body must include |
|-------|----------------------|-------------------|
| Phase 2 (end) | `docs(<feature-id>): phase 2 — planning artifacts` | Paths to PRD, ADR, Feature Architecture, plan snapshot, test plan. List of architecture decisions made (one line each: "decision: chose Option X — reason"). |
| Phase 3 (incremental) | `feat(<feature-id>): <prd-task-id> <short summary>` | What changed, which PRD task it implements, any deviation from the plan and why. |
| Phase 3 (end) | `feat(<feature-id>): phase 3 — implementation complete` | Summary of what was built, any tasks skipped/deferred, any tradeoff decisions that flipped during implementation (with reason). |
| Phase 3.5 (only if fixes) | `fix(<feature-id>): phase 3.5 — pre-test code quality fixes` | What each of items 1–4 flagged and fixed (`/code-review high`, `/vulnerability-scanner`, `/performance`, `/coding-standards`). |
| Phase 4 (automated) | `fix(<feature-id>): phase 4 — automated test fixes` (only if fixes were needed) | Tests that failed, root cause per failure, fix applied. |
| Phase 4 (manual fix loop) | `fix(<feature-id>): <bug-summary>` (one per fix) | Bug description, failing-test reproduction, root cause, fix scope. |
| Phase 5 (end) | `docs(<feature-id>): phase 5 — reconciliation + retrospective` | Plan-vs-reality summary, autopsy findings, **confirmation that all FIVE gates passed** (retrospective gate from 5.1 + test-suite summary lines from 5.4 + code-quality + context-docs + docs-auditor). |

**Commit message body rules:**
- **Body is mandatory** for Phase 2-end, Phase 3-end, and Phase 5-end commits. A one-line subject is not sufficient — Phase 5.0 reads these bodies to reconstruct what happened.
- **Reference PRD task IDs** wherever possible so the diff-to-plan mapping is mechanical.
- **Flag deviations explicitly** with the line `DEVIATION:` — Phase 5 greps for these.
- **Skip-count drift** (Phase 4.2 rule): if a commit deliberately adds a skipped test, call it out in the commit body and add it to `docs/known-test-skips.md` in the same commit.

**The single-chat agent commits the same way the orchestrator's sub-agents do.** Same hashes get recorded for Phase 5. The only difference between modes is *who* does the work, not what shape the git history has at the end.

---

## Sub-Agent Supervision Protocol (orchestrator mode — MANDATORY)

**Sub-agents are isolated context windows. When they crash, run out of context, or stop early, the orchestrator only sees their final return message — which describes what they *intended* to do, not what they *actually did*. The Agent tool's own documentation warns of this explicitly. This protocol turns "trust the summary" into "verify the artifact."**

This protocol applies to **every** sub-agent dispatch in this prompt (Phase 3 implementation, Phase 3.5 code-quality Pre-Test, Phase 4 automated testing, Phase 4 manual-test fix sub-agents, Phase 5.5 code-quality Post-Test, Phase 5.7a context-docs, Phase 5.7b docs-auditor). It does NOT apply to Phase 2 — that runs in the orchestrator's own context as of v5.7.

### 1. Structured Returns — every sub-agent must end with this block (verbatim)

```
SUB-AGENT RETURN
- Status: COMPLETE | BLOCKED | PARTIAL
- Commits made (hash + subject, oldest first):
    <hash1> <subject1>
    <hash2> <subject2>
    ...
- Files modified (paths, relative to repo root):
    <path1>
    <path2>
    ...
- Gate block produced (verbatim, if this agent owns a gate): <pasted-here OR "n/a">
- Blockers / questions for orchestrator: <text OR "none">
- Self-reported confidence (0-100%): <n>
```

**Why this shape:** the orchestrator parses fixed fields, not prose. Status, commits, files, gate, blockers — every field is mechanically extractable. A sub-agent that returns only a prose summary has not satisfied the contract; re-dispatch with a reminder.

**Specify the block in every dispatch.** Each phase's Mode-specific behavior section already lists the per-phase "Expected output back to orchestrator" — those lists are the per-phase population of the fields above. Treat them together: phase-specific fields + the universal block shape.

### 2. Verify-Before-Trust — git is the ground truth

**After every sub-agent dispatch returns, before any further work, the orchestrator MUST:**

1. **Read every commit hash the sub-agent reported.** Run `git show <hash> --stat` at minimum; `git show <hash>` for any commit touching architecture / data flow / public APIs. For multi-commit sub-agents (Phase 3 implementation, Phase 4 automated test fixes): also run `git log --format="%h %s%n%b%n---" <prev>..<latest>` to read every commit body.
2. **Reconcile claim vs reality.** Compare what the sub-agent's return block says it did against what the diff shows. If the diff includes files the sub-agent didn't mention, or omits files it claimed to touch, the return block is wrong — **trust the diff, not the summary.** Note the discrepancy in the orchestrator's working memory for Phase 5 retrospective (it's a sub-agent supervision miss worth logging).
3. **If commits are missing.** Sub-agent reported "complete" but `git log` shows no new commits since dispatch? The sub-agent crashed or context-exhausted mid-work and reported success on autopilot. Re-dispatch the same scope with the same input contract; do NOT trust the original return.
4. **If gate block is missing or paraphrased** (for sub-agents that own a gate): re-dispatch. The gate is the proof; nothing downstream can proceed without it.

**This step is not optional and not deferrable to Phase 5.** The cost of one `git show` per dispatch is seconds; the cost of carrying a phantom "sub-agent did X" belief into Phase 5 is a poisoned reconciliation.

### 3. Bounded Scope — split work that won't fit one context

A sub-agent whose task plausibly takes more than ~30 tool calls is a context-exhaustion candidate. Before dispatching, ask: would this task touch more than ~10 files? Require more than ~5 sequential `/skill` invocations? Span more than one architectural surface (e.g., DB migration + API + frontend + tests)? If yes, **split it** along PRD-task boundaries and dispatch sequentially, each with its own input contract and its own returned commits.

Phase 3 is the most common candidate — a 15-task PRD should be dispatched as 2-3 sub-agent batches, not one giant "implement everything" dispatch. Each batch returns its own commits; the orchestrator runs verify-before-trust between batches; if batch 1's diff reveals the sub-agent went off-script, the orchestrator can correct the dispatch for batch 2 instead of discovering the drift in Phase 5.

### 4. Phase 5 implication — docs work is driven by `git diff`, not transcript

**Phase 5.3 (rewrite PRD / ADR / Feature Architecture) and Phase 5.7 (context-docs + docs-auditor sub-agent inputs) MUST be sourced from a comprehensive git aggregation, not from the orchestrator's recollection of what sub-agents said:**

```
git log --format="%h %s%n%b%n---" <phase-2-end-commit>..HEAD
git diff --stat <phase-2-end-commit>..HEAD
git diff --name-status main...HEAD
```

The orchestrator runs these, reads the output, and uses it as input. **This catches the most damaging silent-drift class in the lifecycle: a Phase 4 fix sub-agent quietly changes the architecture (different data flow, different error path, different component boundary) to make a bug-fix work, and the orchestrator never sees the architectural shift.** If Phase 5.3 rewrites the ADR from the orchestrator's transcript, the ADR will document the *original* design while the code does something else — the docs preserve the lie. Reading the actual diffs is the only way to catch this.

Fix sub-agents are required by the Phase Commit Discipline table to flag deviations with `DEVIATION:` in commit bodies, and `git log --grep="DEVIATION:"` surfaces them quickly — but the diff is the source of truth either way. **Read the diff. Update the docs to match the code, not the plan.**

---

## Skills to Invoke (across the full lifecycle)

<!-- KIT:SLOT-BEGIN skills-list -->
> **[CUSTOMIZE]** Replace with your project's skills. These are examples — keep the ones that match your setup, rename or remove the rest.

- `/brainstorming` — Requirements exploration and feature shaping
- `/using-git-worktrees` — Isolated feature branch (optional)
- `/writing-plans` — Implementation plan document (created in Phase 2, updated as a learning doc in Phase 5)
- `/architecture-patterns` — Modular architecture design
- `/vulnerability-scanner` — Security at design + completion
- `/db-best-practices` — Database changes (e.g., Supabase, Prisma, Drizzle, raw SQL)
- `/framework-best-practices` — Framework-specific patterns (e.g., React/Next.js, Django, Rails)
- `/tdd` — Test-driven development throughout
- `/coding-standards` — Language/framework best practices
- `/technical-writing` — Documentation quality
- `/code-review` — Code quality audit. `/code-review high` does a deeper pass that also covers DRY / reuse / complexity (the merged successor to the old separate `/simplify` skill)
- `/performance` — Performance checks
- `/web-design-guidelines` — UI/UX and accessibility
- `/agent-browser` — Browser automation testing
- `/doctor` — **React projects only — optional.** `react-doctor` lint / a11y / bundle / architecture sweep. Good fit at end-of-epic or before commit on React code. Not wired into any gate by default; the code-quality-agent will not call it unless you customize it to. Note: `/doctor` fetches a live playbook from `react.doctor` on each run — supply-chain surface; drop this line if you don't want that.
<!-- KIT:SLOT-END skills-list -->

---

## PHASE 1: Kickoff & Requirements (INTERACTIVE — main agent in BOTH modes)

**This is the only phase where the AI should ask questions about the feature itself. Batch ALL questions here — do not drip-feed them across later phases.**

**Epics will often be rough — voice notes, quick ideas, half-formed thoughts. Your job is to take that rough input and help the user shape it into a fully specified feature.**

### 1.1 Branch Setup
- Ask: "Do you want to use a git worktree for isolation, or work on a regular branch?"
- If worktree: Run `/using-git-worktrees` to create `feature/{ID}-{short-description}`
- If regular branch: `git checkout -b feature/{ID}-{short-description}` from `main`
- Confirm branch is active

### 1.2 Understand the Epic
- Read the Epic/User Story provided
- Read all relevant context: `docs/context/Context_Index_File.md`, `docs/context/PRODUCTION_READY.md`, `docs/context/database_reference_guide.md`, `docs/context/API_REFERENCE.md`
- Present back to the user: **"Here's what I understand so far..."** — summarise the feature intent, the user problem it solves, and your initial read on what needs to happen
- Be explicit about what's clear and what's vague or missing

### 1.3 Feature Build-Out Questions
Run `/brainstorming` against the Epic to explore requirements and edge cases. Then compile ALL clarifying questions into a single batch:

- **Requirements questions** — What exactly should this do? What are the user flows? What are the edge cases? What should happen on error?
- **Design questions** — UI/UX decisions, where does this live in the app, how does the user discover and interact with it?
- **Technical questions** — Data model changes, API needs, auth/authz implications, integration with existing features?
- **Scope questions** — What's in v1 vs later? Are there related features that should be considered now?

**Use the `AskUserQuestion` tool (interactive terminal selector) for ALL questions.** Batch related questions (up to 4 per call), provide 2-4 options with descriptions, and use `multiSelect: true` when choices aren't mutually exclusive. This gives the user selectable options they can navigate with arrow keys. If answers raise follow-up questions, batch those too — do NOT proceed until requirements are fully resolved.

### 1.4 Stack & Technology Research (when needed)
**If the feature involves unfamiliar libraries, APIs, patterns, or technology choices:**
- Identify what needs researching (e.g., "which charting library?", "how does X API work?", "best approach for real-time updates?")
- Research options, compare tradeoffs (performance, bundle size, maintenance, community)
- Present a recommendation with reasoning
- Get user confirmation on the tech choice before proceeding

**Skip this step if the feature uses only the existing stack with no new dependencies.**

### 1.5 Update the Epic
- Update the Epic document with ALL answers and decisions — nothing undocumented
- Everything discussed becomes part of the written spec

### 1.6 Scope Confirmation
Before proceeding, produce a scope table:

| # | What | Why | Files/Areas Affected |
|---|------|-----|----------------------|
| 1 | ... | ... | ... |

Present the scope table and wait for approval. This is the last time you stop before autonomous execution begins.

**Once the user approves the scope, proceed through Phases 2–5 autonomously. Do not stop unless confidence drops below 90% on a specific decision.**

---

## PHASE 2: Planning & Documentation (AUTONOMOUS)

**Role:** Senior Full-Stack Developer & Architect creating a plan for a junior dev or less capable LLM to execute. Every task must be small, explicit, and self-contained.

### 2.0 Mode-specific behavior

**Phase 2 runs in the main agent in BOTH modes.** This is the v5.7 change — earlier kit versions dispatched Phase 2 to a sub-agent in orchestrator mode, but Phase 2 sub-agents were the highest-failure-rate dispatch in the kit: large context (Plan Mode reads + PRD + ADR + Feature Architecture + plan snapshot + test plan, all in one shot), no incremental commits the orchestrator could observe, and silent context-exhaustion stops where the orchestrator only learned later that an artifact was missing or thin. Worse, when Phase 2 fails silently, the failure poisons every downstream phase — the PRD informs Phase 3, the ADR informs Phase 5.3 rewrites, the test plan informs Phase 4. Phase 2 is also not parallelizable (it's iterative, with the human stop-point at 2.1), so dispatching it bought no concurrency. Keeping it in-context costs orchestrator tokens but eliminates the silent-failure class.

- **Single-chat mode:** Main agent runs Phase 2 inline. Continue with 2.1.
- **Orchestrator mode:** **Main agent runs Phase 2 inline — same as single-chat.** No dispatch. The orchestrator owns Plan Mode, PRD authoring, ADR authoring, Feature Architecture authoring, plan snapshot, and the testing-agent find-or-create. The orchestrator records the Phase 2-end commit hash for Phase 5's git-diff comparison.

**Context-budget note.** If a Phase 2 ever balloons (heavy research + multiple revision rounds on a large epic) and the orchestrator's context window starts to feel tight, the user may `/clear` between Phase 2 and Phase 3 — the Phase 2 artifacts on disk (PRD, ADR, Feature Architecture, plan snapshot, test plan, all committed) are the handoff to a freshly-cleared orchestrator entering Phase 3. Do NOT solve a tight Phase 2 by dispatching to a sub-agent; the silent-failure cost outweighs the context savings.

### 2.1 Enter Plan Mode

**Immediately after scope approval (or on dispatch in orchestrator mode), enter plan mode (`/plan`).** This is the fast alignment gate before you invest time writing documents.

In plan mode you can read, search, and think — but you **cannot** write or edit files. Use this constraint intentionally: plan mode is for structuring your approach, not producing artifacts.

**In plan mode, produce — in this exact structure:**

#### 1. High-level task breakdown
The major implementation steps in execution order (DB migration → API routes → components → tests → docs).

#### 2. Architecture decisions — multi-option tradeoff format
For each non-trivial architectural decision (data model shape, integration boundary, state-management approach, third-party choice, auth pattern, etc.), produce this block:

> **Decision:** [name of the decision]
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
>   - Effort / Risk / Impact / Maintenance: ...
>
> **Recommendation:** [Option X] — [reasoning, opinionated, not neutral]

Aim for 2–3 options per decision. Always include "do nothing / defer" if it's a credible path. Be honest about tradeoffs, not promotional about your recommendation.

#### 3. Performance flags (planning-time)
Flag these **before** writing code — cheap to design around, expensive to retrofit:
- **N+1 / loop-in-query risks** in the proposed data model or API shape
- **Expensive I/O** (cross-region calls, large payloads, sync work that could be async)
- **Memory hotspots** (large in-memory collections, unbounded caches, leaky subscriptions)
- **Caching opportunities** (expensive reads that are rarely-changing)
- **Latency budgets** for any user-blocking interaction

If a flag is severe enough to change the plan, surface it as a Decision in section 2 with options.

#### 4. Risk flags
Anything uncertain, complex, or likely to change during implementation — what you'd want a senior reviewer to scrutinize.

#### 5. Estimated scope
Rough numbers: files touched, new files created, test files needed, migration count.

---

Present this plan for alignment. The user may approve, adjust, or redirect before you spend time on detailed documents.

**Once the plan is approved, exit plan mode to begin writing documents.**

### 2.2 Existing Document Check

**Check first — do not create duplicates.** Search `docs/prd/`, `docs/ard/`, and `docs/architecture/` for existing PRD/ADR/Architecture docs for this feature or a previous version of it.

- **If documents exist:** Update the existing documents — do NOT create new ones. This might be v2 or v3 of a feature; the existing PRD is the one to extend.
- **If documents do not exist:** Create them using the structure below.
- If this feature supersedes an older document, note it under a "Supersedes" heading.

### 2.3 Implementation Plan Document

Run `/writing-plans` to produce `docs/plans/YYYY-MM-DD-<feature-name>.md`. This is the **initial plan snapshot** — what we believed would happen before writing any code. It captures the approach, task breakdown, file paths, and technical decisions at planning time, **including the multi-option tradeoffs and perf flags from Plan Mode**.

This document has a dual purpose:
- **During implementation (Phase 3):** Reference for the planned approach
- **After implementation (Phase 5):** Updated as a **learning document** — what changed, what we didn't foresee, and why. The PRD gets rewritten to match reality; the plan doc preserves the journey.

### 2.4 PRD (Product Requirements Document)

Flesh out the approved plan into a detailed PRD. The PRD is the implementation checklist — every task the agent will execute in Phase 3 lives here.

- Break into small, incremental tasks with checkable boxes (`- [ ]`)
- Embed `STOP: AI Test` markers where automated tests should run during implementation
- Embed `STOP: Human Verification` after every major feature addition (these guide the manual testing checklist later)
- End with a Human Testing Plan (click-by-click instructions for manual testing later)
- Define complete validation plan: user journey, success criteria, edge cases

**Read `docs/context/Unit_Test_Writing_Guide.md` before authoring `STOP: AI Test` markers.** For each marker, cite the relevant anti-pattern IDs (e.g., `A2 over-mocking`, `A6 integration seam`) that apply to that task — this primes the Phase 3.3 test author on which traps to avoid for that specific code. Lessons in the guide's `## Project Lessons` section often map directly to specific seams in this codebase; flag them on the markers that touch those seams.

### 2.5 ADR (Architecture Decision Record)
Run `/architecture-patterns` then document:
- Technical approach, data flow, and schema
- Modular design: reusable utilities, hooks, components — no one-off implementations
- Security by design: Run `/vulnerability-scanner` to identify threats. Document auth, validation, sanitization for every endpoint/form
- URL-addressable routing: all user-visible view modes must be directly linkable and survive refresh/back-forward
- **Carry the Plan Mode multi-option tradeoffs into the ADR — that's the historical record of *why* the chosen option won.** Don't drop the rejected options.

### 2.6 Feature Architecture Document
Output: `docs/architecture/Feature_Architecture_[Feature_Name].md`

Run `/architecture-patterns` then document:
1. **Button-to-Database Flow** — Trigger, every file/function/handler in sequence, completion behavior
2. **Modular Code & Dependencies** — Shared/reusable components, helpers, external libraries
3. **Data Architecture** — Tables read/updated, schema changes, local state management
4. **Integration Points** — Cross-feature interactions. What breaks if removed?
5. **Security** — Run `/vulnerability-scanner`: auth/authz, input validation, data exposure
6. **Future Modernization Guide** — Tech debt, update priorities, scaling at 10x

### 2.7 Testing Agent — Find-or-Create

Run `docs/prompts/create-testing-agent.md`. **Step 0 of that prompt is now a find-or-create against `docs/testing-agents/REGISTRY.md`** — if an existing agent already owns the scope of this feature (e.g., the feature is a small extension of an existing tested surface), the prompt will switch to Adapt Mode and extend that agent rather than create a duplicate.

When a new agent is created, the prompt writes `docs/testing-agents/{feature-name}-tests.md`, adds a row to `REGISTRY.md`, and includes a `## Miss Log` section seeded as empty.

Any project-wide patterns logged in `docs/context/Testing_Patterns.md` (populated by Tier-2 retros over time) are automatically baked into the new plan's scenarios — read more in `docs/prompts/testing-retro.md` Phase 4.

### 2.8 Skills Map
Identify which skills apply at each PRD step (e.g., `/db-best-practices` for DB tasks, `/tdd` for tests).

**Proceed immediately to Phase 3. Do not stop.**

---

## PHASE 3: Implementation (AUTONOMOUS)

### 3.0 Mode-specific behavior

- **Single-chat mode:** Main agent runs Phase 3 inline. Continue with 3.1.
- **Orchestrator mode:** Main agent dispatches Phase 3 sub-agents. **Apply the Sub-Agent Supervision Protocol** (see top of this prompt) to every dispatch — structured returns, verify-before-trust (`git show` every returned hash), and bounded-scope splitting. A 15-task PRD typically splits into 2-3 sequential batches, not one mega-dispatch. Input contract per dispatch:
  - **Model: `opus`** (default). Implementation is where architectural intent meets code — subtle invariant violations and hidden coupling originate here and are expensive to catch downstream. See the model split table at the top of this prompt for the rationale. Override to `sonnet` ONLY for genuinely mechanical work (boilerplate scaffolds, generated code, pure renames); call out the override with a one-line reason on dispatch.
  - Feature ID
  - Branch name
  - Paths to PRD, ADR, Feature Architecture, plan snapshot
  - PRD task range this dispatch is scoped to (e.g., "tasks 1-6" — not the whole PRD if it's large)
  - Path to `docs/context/Unit_Test_Writing_Guide.md` (mandatory — read before writing any failing test)
  - Path to `docs/context/Implementation_Patterns.md` (mandatory — read before writing implementation code; the code-side companion to the test guide)
  - Resource caps from 3.1 below
  - **Expected output back to orchestrator:** the universal `SUB-AGENT RETURN` block (see Sub-Agent Supervision Protocol). Phase-3-specific fields:
    - Commits made: ideally one per PRD task; minimum one per dispatch. Final dispatch's last commit subject is `feat(<feature-id>): phase 3 — implementation complete`.
    - Files modified: every path touched (including tests).
    - Deviations (≤200 words) flagged with `DEVIATION:` in the relevant commit body. The diff is the source of truth — Phase 5.3 will read it.
    - Any confidence-below-90% questions that need user input *before* the next dispatch or Phase 4. Do NOT defer to the end report.
  - **The sub-agent must NOT touch Phase 4 or Phase 5 work.** It returns control to the orchestrator after its scoped batch completes.
  - **The orchestrator runs `git show <hash> --stat` on every returned commit** (more depth for any commit touching architecture) before dispatching the next batch or proceeding to Phase 3.5.

### 3.1 Resource Management (CUSTOMIZE — adjust for your hardware)

<!-- KIT:SLOT-BEGIN resource-management -->
> **[CUSTOMIZE]** The rules below are written for a 16GB MacBook Air with no fan. Adjust caps/limits for your hardware. If you're on a workstation with 64GB+ RAM, you can relax the parallel-agent cap and the "never full test suite" rule, but keep the "kill processes when done" discipline — orphaned dev servers and test workers still cost memory even on large machines.
<!-- KIT:SLOT-END resource-management -->

- **Max 2 parallel agents at a time** *inside* a phase (raise for large-memory hardware). **File-mutating phase sub-agents (Phase 2/3/5) always run sequentially**, never overlap — concurrent edits to the same files corrupt each other. **Exception (kit v5.10+): read-only Phase 4 testing agents MAY run in parallel** when the machine has spare memory and the project has enough distinct test logins — they don't edit code, and each is isolated on its own browser session + dev-server port + test user. Phase 4.1 owns the capability gate and the concurrency math.
- **Kill processes AS SOON AS they finish.** After every test run, build, TypeScript check, or agent completion, immediately check and kill orphaned processes: `ps aux | grep -E "node|vitest|dev-server" | grep -v grep`. Dev servers and test runners must NEVER be left running.
- **Never leave processes running.** Kill them the moment they are done.
- **Never run back-to-back heavy operations** (test suite → tsc → build) without checking the machine isn't already hot. Combine into one chained command where possible.
- **Never run tests in watch mode.** Always use single-run (`npm run test:run`, `npx vitest run`, `pytest`). If a test process hangs: `pkill -f vitest` (or equivalent) immediately.
- **At the end of every session**, run a final process check and kill anything left behind.

### 3.2 Pre-Flight: Clean Baseline

**Before writing any feature code, establish a clean baseline:**

1. Run `[TEST_COMMAND]` (single-run, NOT watch mode) — capture current test results
2. Run `[BUILD_COMMAND]` — confirm clean build
3. **If any tests fail or the build is broken:** Fix them NOW, even if they are unrelated to this feature. Do not start feature work on a broken baseline.
4. Commit baseline fixes separately (e.g., "fix: resolve pre-existing test failures before [feature name]")

### 3.3 TDD Implementation Cycle

**Two read-before-you-work guides bracket this cycle — read both once before the first task of the feature** (and again when you cross into a new layer/area either one calls out):
- **`docs/context/Unit_Test_Writing_Guide.md`** — the test-side guide, read at the RED step (step 0 below).
- **`docs/context/Implementation_Patterns.md`** — the **code-side mirror** (Gap C of the Phase 5.1 retro), read at the IMPLEMENT step (step 3 below). Its universal anti-patterns (I1–In) and `## Project Lessons` are what a *good* implementation for this codebase avoids — recurring bug-classes, project gotchas, architectural traps. Reading it before you write code is the same discipline as reading the test guide before you write a test.

Run `/tdd` to enforce this cycle for each PRD task:

0. **Read `docs/context/Unit_Test_Writing_Guide.md`** — mandatory before the first failing test in this feature, and before any subsequent test that touches a different layer (unit / integration / contract / component) than the previous one. The guide is short by design; the universal anti-patterns (A1–A11) and the `## Project Lessons` section both inform what a *good* test for this codebase looks like. Also re-check the PRD's `STOP: AI Test` marker — it cites the specific anti-pattern IDs that apply to this task.
1. Write failing test — explicitly chosen to dodge the guide's anti-patterns. In particular: assert behaviour not implementation (A1), mock at the system boundary not inside your own code (A2), cover at least one error / boundary / empty case alongside the happy path (A3), and confirm the assertion is actually observable for async tests (A7).
2. **Run the test. Confirm it goes RED for the right reason** (the missing behaviour, not a typo or import error). If a test passes before any code is written, it's testing the wrong thing — rewrite it.
3. Implement minimum code to pass — **applying `Implementation_Patterns.md`**: steer clear of its universal anti-patterns (I-series) and any `## Project Lessons` that touch this area. If a lesson would have prevented the code you're writing, reference its ID in a one-line comment.
4. Run `/code-review` — quick per-task review (use `/code-review high` if this task introduced significant new logic that would benefit from a DRY / reuse / complexity check)
5. Lint
6. Tick off PRD task
7. Move to next task

**Test Rule:** If a test fails, fix the **code**, not the test — unless the test logic itself is wrong.

**Confidence Rule:** If confidence drops below 90% on any requirement or architectural decision, **STOP** and ask the user (multiple-choice format). Otherwise keep going.

- In **orchestrator mode**, the Phase 3 sub-agent surfaces the question to the orchestrator immediately (not at the end). The orchestrator relays to the user. The sub-agent must also read `Unit_Test_Writing_Guide.md` and `Implementation_Patterns.md` — pass both paths in the input contract so the sub-agent doesn't have to re-derive them.

### 3.4 Mid-Implementation Checks
- After every major component/feature addition: run `[TEST_COMMAND]` to catch regressions early
- Run `/coding-standards` periodically to prevent drift
- Keep tests passing at all times — never accumulate broken tests

### Implementation complete → Proceed immediately to Phase 3.5. Do not stop.

---

## PHASE 3.5: Code Quality Gate — Pre-Testing (AUTONOMOUS)

**Catch obvious code-quality issues BEFORE browser testing and manual testing.** Significant refactors at Phase 5 invalidate the testing the user just did; this phase eats that risk early.

### 3.5.0 Mode-specific behavior

- **Single-chat mode:** Main agent dispatches the code-quality-agent in **Pre-Testing Mode** inline before invoking the testing-agent.
- **Orchestrator mode:** Main agent dispatches the code-quality-agent as a sub-agent in Pre-Testing Mode. **Apply the Sub-Agent Supervision Protocol** — verify the agent's gate block is present and unparaphrased (item 4 of the protocol) and `git show` any fix commits the agent made. The Phase 4 sub-agent is NOT dispatched until this one returns clean.

### 3.5.1 Invocation

Invoke `.claude/agents/code-quality-agent.md` with this input contract:

- **Mode:** `pre-test`
- **Feature name**
- **List of changed files:** `git diff --name-only main...HEAD` at the latest Phase 3 commit
- **Path to the PRD**

The agent runs items 1–4 only (`/code-review high`, `/vulnerability-scanner` first pass, `/performance`, `/coding-standards`) plus the mandatory teardown sweep. It does NOT run cleanup, the second vulnerability scan, targeted tests, or the build — those belong to Phase 5.5.

### 3.5.2 Gate Output

The agent prints a verbatim `CODE QUALITY GATE (Pre-Test):` block — 4 diagnostic items (`/code-review high`, `/vulnerability-scanner` (1), `/performance`, `/coding-standards`) plus a 5th process-sweep line. **Paste it into the main transcript unmodified.** If the block is missing, paraphrased, or shows an unresolved issue (especially an open vulnerability on line 2), Phase 4 cannot start — re-run the agent or escalate to the user.

**This block is NOT one of the five final gates checked at Phase 5.8.** It's a pre-flight quality bar, separate from the post-merge gate set. Its sole job is to keep Phase 4 from running on code that's about to be refactored anyway.

### 3.5.3 Fix Loop

If the agent surfaces issues, it applies fixes within scope and re-prints the block clean. If a fix requires architectural change outside the changed-file set, the agent escalates — handle the escalation in main and re-dispatch the agent after the architectural decision is made.

### 3.5.4 Commit

Commit any pre-test fixes with `fix(<feature-id>): phase 3.5 — pre-test code quality fixes` and a body summarising what each skill flagged and fixed. If no fixes were applied, no commit is needed for this phase.

### Pre-test gate clean → Proceed immediately to Phase 4. Do not stop.

---

## PHASE 4: Verification (AUTONOMOUS → STOPS for manual testing → AUTONOMOUS)

### 4.0 Mode-specific behavior

- **Single-chat mode:** Main agent runs Phase 4 inline (4.1–4.4 then STOP).
- **Orchestrator mode:**
  - **Apply the Sub-Agent Supervision Protocol** to every dispatch in this phase — structured returns, verify-before-trust (`git show` every returned hash), and bounded scope. The fix-loop in particular is where architecture quietly drifts: a fix sub-agent picks a different data flow or error path to make the bug go away and the orchestrator only sees "bug fixed." Reading the commit diff is what catches the architectural change.
  - **Automated portion (4.1–4.3) runs in a Phase 4 sub-agent.** Input contract:
    - Feature name, branch, dev server URL, path to test plan, tier/role list.
    - Expected output: the universal `SUB-AGENT RETURN` block, populated with the verbatim PASS/FAIL/SKIP/ABORT gate from `testing-agent` + automated test results + mobile responsiveness summary + commits for any code fixes (`fix(<feature-id>): phase 4 — automated test fixes`).
  - **Manual testing (the STOP) always runs in main.** This is intentional: manual testing is interactive (the user pastes results, asks follow-ups, says "wait, this is broken"), so it has to be in the orchestrator's chat.
  - **Manual-test fix loop = one fix sub-agent per fix.** When a manual test fails:
    1. Orchestrator captures the bug description and which test exposed it.
    2. Orchestrator dispatches a focused fix sub-agent. **Model: `opus`** — see the model split table at the top of this prompt for the rationale. TL;DR: this bug escaped implementation plus every automated gate; the orchestrator's long context may carry the assumption that let it ship. A fresh Opus dispatch with isolated context (bug description + failing reproduction + PRD/ADR + scope guardrails) gets the priors-free reading the long-running orchestrator cannot.
    3. Fix sub-agent writes a failing test first (TDD), fixes the code, runs `[TEST_COMMAND]` and `[BUILD_COMMAND]`, commits with `fix(<feature-id>): <bug-summary>`, returns the `SUB-AGENT RETURN` block. **If the fix changed a data flow, error path, or component boundary documented in the PRD/ADR/Feature Architecture, the fix-agent MUST flag this with `DEVIATION:` in the commit body.** Phase 5.3 reads these.
    4. **Orchestrator runs `git show <hash>`** on the returned commit (not just `--stat` — read the actual diff). Confirm the change matches the bug description. If the diff reveals an architectural shift the fix-agent didn't flag, note it for Phase 5.3 anyway — the orchestrator catches what the fix-agent missed.
    5. Orchestrator records the commit hash and asks the user to re-test.
    6. Repeat until manual testing passes.
  - **Why this matters:** without this, the fix loop sits in the orchestrator's context. After 5–6 iterations the orchestrator is full of stale debug back-and-forth, Phase 5 reconciliation gets noisy, and architectural drift introduced by fixes goes undocumented because the orchestrator only "remembers" bug-fix labels, not the actual code changes.

### 4.1 Browser Testing (via testing-agent sub-agent)

**Do NOT run agent-browser inline. Invoke the dedicated testing-agent sub-agent.**

The testing-agent lives at `.claude/agents/testing-agent.md` and has mandatory RAM-hygiene pre-flight + teardown. Running agent-browser inline bypasses these safeguards and can leave multi-GB of orphaned MCP/chromium processes.

**Invocation:**

1. Confirm the feature-specific test plan exists at `docs/testing-agents/{feature-name}-tests.md` (generated in Phase 2.7). If not, generate it now via `docs/prompts/create-testing-agent.md` before proceeding.
2. Start the dev server on a known port: `[DEV_SERVER_COMMAND]` (e.g. `npm run dev`, localhost:3000 by default; use a different port per worktree).
3. If in a worktree: symlink `.env.local` from main.
4. Invoke the testing-agent with this input block:
   - Feature name
   - Dev server URL (e.g. `http://localhost:3000`)
   - Path to the test plan
   - Tiers / roles to test (if applicable to your auth model)
5. Wait for the testing-agent to complete and paste its full test results block (PASS/FAIL/SKIP/ABORT table + Reliability Notes) into the main transcript. The main agent must NOT paraphrase — paste verbatim.

**If the testing-agent asks for help (bad creds, missing env, wrong branch), answer it immediately. Do not defer to the end report.**

**Live progress visibility (v5.7+).** The testing-agent writes a `progress.log` in its evidence dir (path is reported at the start of its run). In a second terminal, the user can `tail -f` it to watch scenario START/END events and any WARN/ABORT signals in real time. If a single scenario hasn't logged anything for several minutes when the bracket says it should have, that's a reliability problem worth interrupting — but the agent's own self-pacing should ABORT it within ~3× the expected duration regardless.

**Handling ABORTs in the returned report.** ABORT is a **fourth verdict** alongside PASS/FAIL/SKIP (added in kit v5.7 to surface silent stalls — v2 testing-agent runs were observed hanging for hours on stuck browser actions). An ABORT means the testing-agent couldn't get a verdict on that scenario, NOT that the feature failed. Three possible causes per ABORT, and the orchestrator picks the response:
1. **Wrong expected duration in the test plan** — bracket says 30s, real-world is 2min. Fix: widen the bracket via Adapt Mode (`docs/prompts/create-testing-agent.md`) and re-dispatch.
2. **Hung browser action** (chromium/MCP/agent-browser) — fix: re-dispatch the agent (usually transient); if it ABORTs again on the same scenario, escalate to the user.
3. **App is genuinely broken in a way that hangs the browser** (infinite redirect, modal blocking interaction, JS exception preventing state change) — fix: this IS a real bug; treat the ABORT'd scenario like a FAIL and route into the manual-test fix loop with the ABORT evidence (`{test-id}-abort.png` + console log + network log).

The orchestrator's job is to read the per-ABORT evidence the agent captured and decide which of the three applies. Do NOT just blanket re-run on every ABORT — that's how 12-hour test runs happen.

Once the testing-agent completes, it handles its own RAM teardown. The main agent should NOT invoke agent-browser or MCP browser tools directly.

#### 4.1p Parallel testing mode (kit v5.10+ — capability-gated, opt-in by plan)

**When a feature touches several scopes, Phase 4 may need to run multiple test plans.** On a capable machine you can run them **concurrently** instead of back-to-back, cutting verification wall-clock substantially. This is the kit's one sanctioned parallel sub-agent dispatch — safe only because testing agents are **read-only** (they edit no code) and each is **fully isolated** (own browser session + dev-server port + test user). Run sequentially (the flow above) whenever any gate below fails — parallel is an optimization, never a requirement.

> **Scope & portability (universal concept, browser-default primitives).** The *idea* is stack-agnostic: run independent test plans concurrently when you have spare capacity AND can isolate each run along three axes — (1) execution sandbox, (2) data/backend, (3) identity. The *primitives named below* — agent-browser `--session`, a dev-server port, a test login — are the kit's defaults for its **browser** testing-agent (the vanilla `.claude/agents/testing-agent.md`). The **capacity math (Step 2) and the parallel-safety classification are universal**; only the isolation mechanics are browser-shaped. If your project's testing-agent is **structurally divergent** (rewritten for a non-web stack — native mobile, CLI, data pipeline, a library test harness), map the three axes to your stack's equivalents — e.g. parallel `pytest -n` workers each with an isolated temp DB + fixture dir; N `go test` packages in separate build dirs; isolated simulator instances — or, if your harness has no safe concurrency story, **keep Phase 4 sequential** (parallel is always optional). Don't read "dev-server port" as a requirement to *have* a dev server; read it as "give each concurrent run its own execution sandbox, whatever that means for your stack."

**Step 1 — Collect the candidate plans.** List the test plans this feature needs (from the Registry / Phase 2.7). Read each plan's `Test Configuration`:
- Only plans with **`parallel_safe: true`** are eligible. `parallel_safe: false` (or unmarked) plans run sequentially in the flow above — always.
- Note each eligible plan's `parallel_isolation` (`read-only` / `distinct-login` / `isolated-backend`) and which test user/role it needs.

**Step 2 — Derive the concurrency N (adapt to THIS machine + project — never a fixed number).** The kit gives you a formula and calibration *starting points*; the actual N must be **derived at runtime** from probing the host machine and reading the project in front of you. Do not treat any number below as a kit commandment — a 16 GB laptop and a 64 GB workstation must reach different N from the same prompt. The base bound is `N = min(memory_allowed, eligible_plan_count, cap)`, with **one extra constraint that applies only to `distinct-login` plans** (see below).

<!-- KIT:SLOT-BEGIN parallel-testing-capacity -->
> **[CUSTOMIZE] — calibration, not commandments.** Everything below is a *starting point to adapt to your hardware and stack*, not a value the kit imposes. The **memory probe is the source of truth**; the numbers just seed it. Per-agent budget tracks dev-server weight (a Next.js dev server + chromium is heavier than a Vite one). The cap is **your machine's comfortable ceiling**, not a kit default to copy — record what *your* hardware sustains. List distinct logins however your project actually supplies them — env vars, tier accounts, fixtures — or `no auth — login cap N/A`. If you record a standing ceiling here, the agent adapts silently within it; if you leave it at the template, the agent probes and asks you per run.

1. **Memory headroom** — probe spare RAM, divide by the per-agent budget (starting point **~3 GB**, calibrate to your dev-server weight):
   ```bash
   # macOS (approx available GB = free + inactive pages):
   ps=$(vm_stat); psz=$(sysctl -n hw.pagesize)
   free=$(echo "$ps" | awk '/Pages free/{gsub(/\./,"",$3);print $3}')
   inact=$(echo "$ps" | awk '/Pages inactive/{gsub(/\./,"",$3);print $3}')
   avail_gb=$(( (free + inact) * psz / 1073741824 ))
   # Linux: avail_gb=$(free -g | awk '/^Mem:/{print $7}')
   echo "$(( avail_gb / 3 ))"   # memory-allowed agent count
   ```
   If the probe looks wrong (returns 0 or errors on your platform), treat memory-allowed as **1** (sequential) — never guess high.
2. **Plan count** — number of `parallel_safe: true` plans to run.
3. **Distinct test logins available — caps `distinct-login` plans ONLY.** How many *different* test users you can hand out. This is the user-identity clash guard: a shared DB is fine, but two agents acting as the *same* user at once corrupt each other — so **at most this many `distinct-login` plans run concurrently.** List the pool, e.g. `TEST_USER_1`, `TEST_USER_2`, `TEST_ADMIN`. **`read-only` and `isolated-backend` plans do NOT consume the login pool** and are bounded only by memory + cap. **A project with no auth at all** (a CLI tool, a public/read-only app, a single-user local app — all its plans are `read-only` or `isolated-backend`) is **not limited by logins** and parallelizes on memory alone — write `no auth — login cap N/A` here. (Note: no-auth still requires per-plan isolation — a single-user app that mutates one shared JSON file/DB from every port must use `isolated-backend`, e.g. a per-port `DB_PATH`, or stay `read-only`; "no login" is not the same as "no shared state.")
4. **Per-machine ceiling (`cap`)** — your hardware's comfortable max, NOT a kit-fixed number. Calibrate to what the machine actually sustains: a 16 GB laptop lands around 1–2, a 32 GB machine ~3, a 64 GB+ / Apple-silicon-Max workstation ~4–6. Start conservative and raise it as you observe headroom. ~4 is a reasonable *opening* guess, not a target.
<!-- KIT:SLOT-END parallel-testing-capacity -->

So: `N = min(memory_allowed, eligible_plan_count, cap)`, **and** the number of concurrently-running `distinct-login` plans must not exceed `distinct_logins`. `read-only`/`isolated-backend` plans are unconstrained by the login count. **If N < 2, run sequentially** (the flow above) — you're done here.

**Surface the recommendation; the machine's owner decides (adaptive default).** Once you've *derived* a recommended N from the probe + project, **present it as a quick pick** rather than silently running it — e.g. *"This machine can run up to N parallel testing agents (~M GB free, K plans, L distinct logins). Run N / fewer / sequential?"* — and honour the choice. This is a one-click resource decision (like the Phase 0 mode pick), not a deliberation; it exists because the lanes burn the user's own RAM and they own that trade-off. **Skip the prompt only when the CUSTOMIZE slot records a standing ceiling** — then adapt silently within it. And if parallel ABORTs cluster mid-run, that's the machine telling you N was too high: **self-correct** — drop N and re-run the failed plans without re-asking.

**Step 3 — Allocate identities and ports.** For the batch of N:
- Assign each `distinct-login` agent a **distinct test login** from the pool. Two plans that both require the *same exclusive* user/role (e.g. the single admin) cannot be in the same batch — serialize those across batches; parallelize across distinct-user groups. **`read-only` and `isolated-backend` plans need no dedicated login and don't consume the pool.**
- Assign each agent a **distinct port** and start one dev server per port (`[DEV_SERVER_COMMAND]` with a per-agent port). For `distinct-login` plans the servers may share one backend DB — isolation comes from the distinct login. For `isolated-backend` plans, give each port its own backend (e.g. a per-port `DB_PATH` / schema / JSON file) — isolation comes from the separate store, not a login.
- Assign each agent a **unique session name** (e.g. `tplan-<feature>-<n>`).

**Step 4 — One pre-batch sweep, then dispatch.** The orchestrator owns global cleanup so the agents don't have to:
```bash
pkill -f "actors-mcp-server|playwright-mcp" 2>/dev/null || true   # one global sweep BEFORE the batch
```
Then dispatch all N testing agents **concurrently** (a single message with N Agent calls). Each invocation block adds, on top of the normal fields (feature, plan path, dev-server URL):
- **`Run mode: parallel`**
- **`SESSION:`** the unique session name
- **`PORT:`** the agent's port (and the matching dev-server URL)
- **`Assigned test user:`** the one login this agent may use

Each agent applies testing-agent.md **§0.5** (session/port/user isolation) and its **parallel-mode teardown** (§8 — closes only its session, frees only its port; never `--all`, never global chromium `pkill`).

**Step 5 — Collect and verify every gate.** Apply the Sub-Agent Supervision Protocol to **each** returned agent independently: paste each verbatim test-results block into the main transcript, and `git`-verify nothing (testing agents make no commits — but confirm each actually returned a results block, not a silent stop). A `BLOCKED_NEEDS_FIXTURE (parallel-user-unavailable)` from any agent means the allocation was wrong — re-run that plan sequentially with a valid user.

**Step 6 — Post-batch sweep.** After all N return, run the global teardown once to catch anything orphaned:
```bash
npx agent-browser close --all 2>/dev/null || true
pkill -f "actors-mcp-server|playwright-mcp|chromium.*headless" 2>/dev/null || true
ps aux | grep -E "agent-browser|chromium.*headless|node.*dev" | grep -v grep | wc -l   # expect 0
```

If any agent ABORTs, handle it per the ABORT rules above — but note a parallel ABORT can also mean **memory contention** (you dispatched too many for the machine). If ABORTs cluster, drop N and re-run the failed plans.

### 4.2 Automated Tests — FULL SUITE GATE (mandatory)

This is the gate that **targeted-files-only test runs cannot replace**. The whole suite runs, and proof gets pasted into the transcript.

1. **Pre-flight cleanup.** Before launching the suite, kill any lingering dev servers, MCP servers, or test runners (`ps aux | grep -E "node|test-runner|chrom|playwright" | grep -v grep` and `pkill` what doesn't belong). One heavy operation at a time.

2. **Run `[TEST_COMMAND]` — full suite, no path argument** (no targeted file lists, no `--only`). Capture the **verbatim** final summary lines from your test runner. Examples:
   - Vitest / Jest: `Test Files  X passed | Y skipped (Z)` and `Tests  A passed | B skipped | C todo (D)`
   - Pytest: `=== A passed, B skipped, C deselected, D warnings in T s ===`
   - Go test: `ok      package    T s` (per package)

   **Paste the verbatim summary lines into the transcript.** "All green" or "tests pass" without these lines does NOT satisfy the gate. Sub-agent reports that say "tests pass" without the lines also do not satisfy it — the main agent must run the suite and paste the proof.

3. **Run `[BUILD_COMMAND]`.** Confirm zero errors and exit code 0.

4. **Failure handling.** If any test fails:
   - Fix the **code** (not the test, unless the test logic is wrong), re-run, re-paste the summary lines.
   - **Pre-existing failures are NOT a "not my problem" exemption.** Two options: (a) fix them as part of this feature's pre-flight in a separate commit (`fix: pre-existing test failure …`), or (b) get explicit user approval to log them in `docs/known-test-failures.md` with a date-stamped reason and a follow-up task — only then can the feature merge.

5. **Skip-count drift handling.** If `B` (skipped) is higher than the baseline in `docs/known-test-skips.md`, document the new skips in that file with a reason, or remove them. The merge commit must mention any deliberate skip additions (per Phase Commit Discipline).

<!-- KIT:SLOT-BEGIN test-failure-discipline -->
> **[CUSTOMIZE]** Your project's `docs/known-test-failures.md` and `docs/known-test-skips.md` are recommended discipline files — create them on first feature, append on every feature that touches the baseline. They make pre-existing-failure exemptions auditable instead of invisible.
<!-- KIT:SLOT-END test-failure-discipline -->

### 4.3 Coverage & Mobile Check

**Coverage:** Confirm browser testing + automated tests cover all new UI, modified functionality, edge cases, auth gates. Flag any gaps.

**Mobile Responsiveness (MANDATORY):** Check all touched pages at 375px viewport:
- No horizontal overflow or content cut off
- Touch targets at least 44px
- Grids stack to single column on mobile
- Modals/dialogs fit within viewport
- Text readable (minimum `text-xs` / 12px)
- Buttons/tiles wrap without overflow
- Fixed/floating elements don't obscure content
- Forms usable — inputs not cramped, labels visible

Fix any mobile issues before proceeding.

### 4.4 Test Summary
- Browser test result: passed / failed / fixed (from testing-agent verbatim block)
- Automated test result: passed / failed / fixed
- Build result: clean / errors fixed
- Coverage gaps flagged for human testing

---

### ⏸️ STOP: Manual Testing Handoff (always main agent)

**This is the only mid-flow stop.** Generate a concise manual testing checklist of anything that:
- Was not fully covered by browser automation (complex interactions, edge cases, multi-step flows)
- Relies on real device/mobile behavior
- Involves billing, auth edge cases, or third-party integrations
- Felt uncertain or flaky during browser testing

Format:
> **Human Test [N]: [Name]**
> - Steps: [what to do]
> - Expected: [what should happen]

**Present the checklist and wait for the user to complete manual testing and report back with results (what passed, what failed, what issues were found).**

In **orchestrator mode**, any failures kick off the fix-sub-agent loop described in 4.0. Stay in main; dispatch one sub-agent per fix; record commits.

---

## PHASE 5: Reconciliation & Completion (AUTONOMOUS — main agent in BOTH modes)

**Phase 5 always runs in the main agent**, regardless of mode. The orchestrator already holds: Phase 1 decisions, the Plan Mode plan, manual-test results, and commit hashes per phase — exactly what's needed to drive reconciliation.

### 5.0 Source of truth for "what was built" — MANDATORY in both modes

**Drive reconciliation from `git diff` between the recorded phase commits, plus the commit message bodies — NOT from transcript memory. This is mandatory in both single-chat and orchestrator mode.**

Why this is required even in single-chat mode: on long features the agent's memory of what it built earlier is unreliable. Context gets compacted, summaries lose detail, decisions made in Phase 2 are forgotten by Phase 5. The git history is the only ground truth. The Phase Commit Discipline above ensures the history *has* enough detail to read.

**Inspection commands (run all of these — don't skip):**
```
git log --format="%h %s%n%b%n---" <phase-2-commit>..HEAD     # full subjects + bodies
git log --oneline <phase-2-commit>..HEAD                      # quick scan
git diff --stat <phase-2-commit>..HEAD                        # change footprint
git diff <phase-2-commit>..<phase-3-end-commit> -- <file>     # what implementation changed
git diff <phase-3-end-commit>..HEAD -- <file>                 # what fixes changed during Phase 4
git log --grep="DEVIATION:" <phase-2-commit>..HEAD            # explicit deviation flags
```

**Compare diff content + commit bodies against:**
1. The PRD task list — every task should have a corresponding commit (referenced by task ID).
2. The ADR's Plan Mode tradeoff decisions — if a chosen option flipped, that's a Plan-vs-Reality entry. Capture *why* it flipped from the commit body.
3. The plan snapshot's performance flags — were they addressed? Mitigated? Still open?

**If the commit log doesn't have enough detail to drive this comparison**, that's a Phase Commit Discipline violation — flag it in the retrospective so future features tighten their commit messages.

### 5.1 Plan vs Reality

**Run this AFTER manual testing results come back — not before.** Compare the original plan against what actually happened across the entire feature, including the user's manual testing findings:

- What was implemented exactly as planned?
- What deviated? Why?
- Unexpected issues discovered during implementation?
- Issues found during manual testing — what broke, what was unexpected?
- Any planned tasks skipped or deferred?
- Anything the user flagged during testing that needs addressing before completion?

**In orchestrator mode, drive each of these from the diffs and commit messages, not the transcript.** The transcript is incomplete — sub-agents only returned summaries.

**Retrospective sweep (UNCONDITIONAL — runs on EVERY feature, including a zero-finding one).** Do NOT gate this on "manual testing revealed bugs." The retro is a sweep with a per-finding decision, not a step that only fires when something broke. Build the complete finding set first, then make a recorded decision for each one — every finding either produces a durable lesson (Gap A test-suite, Gap B testing-agent, and/or Gap C implementation/code — all below) or is named in the gate with the reason it doesn't.

**Build the finding set from two sources (take the union, dedup):**
1. **Every manual-test finding** the user reported at the Phase 4 STOP — bugs, "this is broken," "this felt off," plus any ABORT'd scenario that was routed in as a real bug.
2. **Every fix commit of the phase:** `git log --grep="^fix(" <phase-2-end-commit>..HEAD`. Each `fix(` commit is a finding whose lesson must be accounted for — whether it came from automated testing (Phase 4.2), the manual-test fix loop, or pre-flight (Phase 3.5). A fix that landed with no retro is exactly the silent miss this sweep exists to catch.

For **each** finding in that union, either run the **Test Autopsy** below (closing Gap A, Gap B, and/or Gap C), or record it on the gate's "deliberately NOT lessoned" line with a named reason (UX-polish / forward-scope / documented out-of-scope exemption). There is no other option — a finding cannot leave Phase 5.1 unaccounted-for.

The autopsy closes **three** gaps per finding, not one. All three are persistent — each writes durable artefacts the next feature reads. Gap A and Gap B ask whether the *tests* should have caught it; Gap C asks whether the *code itself* was an instance of a fault-class the next implementer should design around:

**Gap A — The unit/integration test suite:**

Run `docs/prompts/test-suite-retro.md` against the bug. The retro:

1. Identifies the existing test(s) closest to the broken behaviour and reads them honestly — what do they actually assert vs claim to assert?
2. Writes a failing reproduction test (RED against pre-fix code) **before** the fix is written. TDD discipline: the fix starts with a failing test, always.
3. Categorises the miss into exactly one of eight buckets (A wrong assertion / B over-mock / C missing scenario / D asserting mock / E test data / F no coverage / G async no-op / H integration gap), each mapped to an anti-pattern in `docs/context/Unit_Test_Writing_Guide.md`.
4. Rewrites or adds the test(s); confirms GREEN against fixed code.
5. Appends a row to `docs/test-suite-misses.md` (the chronological audit log — every miss, regardless of tier).
6. Decides tier propagation:
   - **Tier 1 — one-off:** log entry only.
   - **Tier 2 — project lesson:** appends a Project Lesson to `docs/context/Unit_Test_Writing_Guide.md` so the next Phase 3.3 test author sees it.
   - **Tier 3 — universal:** proposes a change to the universal anti-pattern section of the guide; AWAITS USER APPROVAL before editing.

**Why this matters:** without the retro, the autopsy lived inside a transcript no one would read again. Now every miss leaves two durable traces (the log row, and — if generalisable — a guide entry). The next time TDD runs in Phase 3.3, the guide is read first and that lesson is in play.

**Gap B — The testing-agent library (browser tests):**

7. **Why did the testing agent that ran in Phase 4.1 not catch this?** Run `docs/prompts/testing-retro.md` against this bug. The retro:
   - Identifies which agent in `docs/testing-agents/REGISTRY.md` owned the scope (or flags "no owner" if no agent covered it — itself a finding).
   - Categorizes the miss (A scope / B scenario / C assertion / D verification / E setup).
   - Either adapts the existing agent or creates a new one via `create-testing-agent.md` Adapt Mode.
   - Appends a Miss Log entry on the agent.
   - Decides tier propagation (feature-only / project-pattern → `docs/context/Testing_Patterns.md` / skill-universal → kit-level change with user approval).
8. If fixes are significant, re-run the relevant browser tests from Phase 4 against the updated agent to confirm the new scenario now FAILs against pre-fix code (or PASSes against fixed code).

**Gap C — The implementation/code guide:**

9. **Was the code itself an instance of a recurring fault-class, a project coding gotcha, or an architectural anti-pattern?** Gap A and Gap B fix the *tests*; Gap C captures the durable *code* lesson so the next implementer doesn't write the same fault. For each finding:
   - Read the fix's diff and name the fault honestly — not "bug in X," but the *class*: a swallowed error, an unvalidated boundary, drifted duplication, a symptom-patched-at-the-wrong-layer recurrence, etc. (the universal anti-patterns in `docs/context/Implementation_Patterns.md` are the vocabulary — I-series).
   - Decide tier propagation, mirroring Gap A:
     - **Tier 1 — one-off:** a genuine non-recurring slip (typo, one-time oversight). No guide entry — it gets NAMED on the gate's "deliberately NOT lessoned" line as a Tier-1 code finding, not silently dropped.
     - **Tier 2 — project lesson:** a fault-class, gotcha, or architectural trap this codebase is prone to → append a Project Lesson to `docs/context/Implementation_Patterns.md` (bug-class / gotcha / architecture), traced to the `fix(` commit. The next Phase 3 implementer reads it before writing code.
     - **Tier 3 — universal:** the fault-class is generalisable beyond this project (a new universal anti-pattern the I-series doesn't cover) → propose it for the guide's Universal Anti-Patterns section; AWAITS USER APPROVAL before editing (same channel as Gap A Tier 3 — surfaced on the gate's Tier-3 line).

**Why Gap C matters:** the test ecosystem has two read-before-you-work guides; without Gap C the code ecosystem has zero, and implementation lessons evaporate into ADRs and plan docs no one reads before the next feature. `Implementation_Patterns.md` is read at the IMPLEMENT step of every Phase 3 TDD cycle — so a Gap C lesson is in play the next time code is written, exactly as a Gap A lesson is in play the next time a test is written.

**Gap A, Gap B, and Gap C are independent.** A given finding may need any subset (or, rarely, none):
- Browser-observable bug, correct code, just an untested interaction? Gap B (± Gap A) only.
- Pure backend bug with no UI surface, but the code was a clean one-off? Gap A only.
- Bug through a button click whose root fault is a service function that swallowed an error — and this is the third time something swallowed an error? **All three** — Gap A (service-level test honesty), Gap B (browser test that should have observed it), Gap C (the swallowed-error fault-class → `Implementation_Patterns.md`).

**Do NOT just fix the code and move on.** If tests passed while the feature was broken, the test suite has a gap (A). If the testing agent passed while broken, the agent library has a gap (B). If the code was an instance of a fault-class this project keeps producing, the implementation guide has a gap (C). Closing all three that apply is as important as fixing the bug itself — otherwise the same class of bug escapes again on the next feature.

(In orchestrator mode, the Phase 4 fix-sub-agent loop has typically already done the autopsy + fix per bug. Phase 5's job here is to consolidate the autopsy findings across all bugs — Gap A, Gap B, and Gap C closures, including the new miss-log entries, testing-agent Miss Log entries, and any guide updates (unit-test guide and `Implementation_Patterns.md`) — into one coherent retrospective entry, and to surface any Tier-2 / Tier-3 propagations for user approval.)

#### 5.1 Gate Output — RETROSPECTIVE GATE (VERBATIM, MANDATORY — main agent authors it)

**The sweep above is not done until this block is printed.** It is the merge-blocking proof that the retro was actually performed and accounted-for per finding — the same enforcement idiom as the Documentation Gate (a step that reliably runs *because* it ends in a pasted gate, unlike the conditional reminder this replaces). Phase 5 runs in the main agent in both modes, so the main agent authors this block directly (no sub-agent dispatch). It is **Gate #1** of the five checked at Phase 5.8 — chronologically the earliest.

**A zero-finding run STILL prints the block** — `Findings swept: 0` and every line `none`. The gate is the sweep being performed and accounted-for, NOT whether any lesson resulted. "It was only polish" becomes a NAMED line in item 6, never a silent skip — that named accounting is exactly why teams can keep the gate on a polish-only epic instead of wanting to skip it.

**Print it exactly, for the WHOLE epic (all findings consolidated), as the close of Phase 5.1:**

```
RETROSPECTIVE GATE:

Findings swept: [N]   (manual-test findings ∪ `fix(` commits in <phase-2-end-commit>..HEAD, deduped)

1. Gap A — unit/integration test suite:
   - Lessons added to `docs/context/Unit_Test_Writing_Guide.md` (Project Lessons): [list each, or "none"]
   - Miss-ledger rows appended to `docs/test-suite-misses.md`: [N rows — one per autopsied finding, or "none"]
   → [N findings closed via Gap A / none needed because {reason}]

2. Gap B — testing-agent library (browser tests):
   - Miss Log entries appended on the owning agent(s): [agent → entry, one per finding, or "none"]
   - Testing-pattern additions to `docs/context/Testing_Patterns.md`: [list each, or "none"]
   → [N findings closed via Gap B / none needed because {reason}]

3. Gap C — implementation/code lessons:
   - Project Lessons added to `docs/context/Implementation_Patterns.md` (bug-class / gotcha / architecture): [list each, or "none"]
   → [guide entries added | none because ___]

4. Planning / process:
   - Plan-doc retrospective updated (`docs/plans/<...>.md` §Retrospective, per 5.2): [YES — {one-line summary} / none]

5. Tier-3 (skill-universal) proposals AWAITING USER APPROVAL:
   - [list each proposed universal test anti-pattern / universal code anti-pattern (I-series) / skill-level / kit-level change, or "none"]

6. Findings deliberately NOT lessoned (each finding NAMED with its reason):
   - [finding] — [UX-polish / forward-scope / documented out-of-scope exemption / Tier-1 one-off] — {reason}
   - ... (or "none")
```

**Accounting rule:** every finding must be accounted for. The count of findings that produced at least one lesson (Gap A, Gap B, and/or Gap C), plus the count named in item 6, must equal `Findings swept: N`. If they don't reconcile, a finding fell through — re-run the sweep. A finding can close more than one gap (it's counted once toward the reconciliation, with all its gap closures listed). Tier-3 proposals (item 5) block merge only in the sense that they must be *surfaced* here; the user approves or defers them, they don't stall the gate.

### 5.2 Update Plan Document (Learning Doc)

**Open the plan document from Phase 2.3 (`docs/plans/YYYY-MM-DD-<feature-name>.md`) and update it as a retrospective.** This is NOT a rewrite to match reality — that's the PRD's job. This is the learning document that preserves what changed and why.

Add a `## Retrospective` section at the end covering:

- **What went as planned** — which tasks executed exactly as written? This validates the planning process.
- **What changed and why** — for each deviation: what did the original plan say, what actually happened (cite the commit/diff), and what caused the change?
- **Tradeoff decisions revisited** — for each multi-option decision in Plan Mode, did the chosen option hold up? If not, why did it flip?
- **What we didn't foresee** — blind spots in the original plan. Things that seemed simple but weren't, dependencies that weren't obvious, edge cases that emerged during implementation.
- **What we'd do differently** — if starting this feature again with current knowledge, what would change in the planning phase?
- **Patterns to reuse** — approaches, utilities, or architectural decisions that worked well and should be applied to similar features.

This document becomes a reference for future planning — not "what was built" (that's the PRD/ADR) but "what we learned about how to plan."

### 5.3 Rewrite PRD / ADR / Architecture to Match Reality

**The PRD, ADR, and Feature Architecture docs must be the source of truth for the feature — they must match the actual code, not the original plan.** If someone reads them tomorrow, they should accurately describe what was built and how it works.

**Step 1 — Aggregate the git evidence (MANDATORY before any rewrite).** Per the Sub-Agent Supervision Protocol § 4: run these and read the output before touching any doc:

```
git log --format="%h %s%n%b%n---" <phase-2-end-commit>..HEAD
git diff --stat <phase-2-end-commit>..HEAD
git log --grep="DEVIATION:" <phase-2-end-commit>..HEAD
git diff <phase-2-end-commit>..HEAD -- <path-to-each-architecture-relevant-file>
```

**This step exists specifically to catch fix-sub-agent architectural drift.** A Phase 4 manual-test fix sub-agent may have changed a data flow, error path, or component boundary to make a bug-fix work and only logged it as "fix(<id>): <bug>." If you rewrite the docs from your transcript memory (which only contains "bug X fixed" labels), the docs will document the *original* design while the code does something else. **Read the diffs. The docs follow the code.**

**Step 2 — Rewrite the three docs against the diff evidence:**

- **PRD:** Rewrite task descriptions to reflect what was actually implemented. Mark completed items (`- [x]`). Remove or update tasks that changed. Note deviations from the original plan with reasoning (cite the commit hash). Add an "Implementation Notes" section: patterns that worked, pitfalls to avoid.
- **ADR:** Update technical approach, data flow, and schema to match the actual implementation. If architectural decisions changed during development (especially during the Phase 4 manual-test fix loop), rewrite the justification to reflect the real decision, not the planned one. Keep the rejected options (the ADR is also a historical record). Every architectural change should cite the commit hash that introduced it.
- **Feature Architecture file:** Rewrite any sections where the implementation differs from the original design. The button-to-DB flow, data architecture, and integration points must describe the code as it actually exists. If a fix changed a component boundary or call sequence, rewrite that section against the post-fix code.

**If the diff reveals a change the orchestrator wasn't aware of** (e.g., a fix sub-agent silently rewrote a service interface and didn't `DEVIATION:`-flag it), update the docs to match the code AND log the sub-agent supervision miss in 5.2's retrospective so the next feature tightens its fix-agent dispatch contract.

Run `/technical-writing` to ensure quality. **This is not box-ticking — it's a rewrite pass.** The plan doc (5.2) preserves the journey; these docs preserve the destination.

### 5.4 Re-run the Full Test Suite (mandatory pre-merge)

**Even if Phase 4.2 was clean, re-run the full suite after:**
- Any post-manual-testing fix
- The PRD/ADR/Architecture rewrites in 5.3 (in case anything regressed)

Re-run `[TEST_COMMAND]` and `[BUILD_COMMAND]`. **Paste the verbatim summary lines into the transcript** — these become Gate #2 in 5.8 (the Retrospective Gate from 5.1 is Gate #1). The same FULL SUITE GATE rules from Phase 4.2 apply: full suite, no path arguments, no "tests pass" without proof.

Do not skip this on the basis that "I only edited docs in 5.3" — the gate is procedural, not contingent on what changed.

### 5.5 Code Quality & Security — MANDATORY (delegated to code-quality-agent, Post-Manual-Testing Mode)

**This is the SECOND invocation of the code-quality-agent — the first ran in Phase 3.5 (Pre-Testing Mode, items 1–4). This invocation runs the full 10-item gate including cleanup and the post-cleanup vulnerability scan, which only make sense after the manual-test fix loop has landed all its changes.**

**Delegate this section to the `code-quality-agent` sub-agent at `.claude/agents/code-quality-agent.md`. Do NOT run `/code-review`, `/vulnerability-scanner`, `/performance`, or `/coding-standards` inline — the agent runs them in the correct order with the correct failure handling.**

**Invocation input** (per Sub-Agent Supervision Protocol § 4 — feed git evidence directly, the sub-agent is outside the orchestrator's context):
- **Mode:** `post-test`
- Feature name
- List of changed files (`git diff --name-only main...HEAD`)
- `git log --format="%h %s%n%b%n---" main...HEAD` — per-commit hashes and bodies, including `DEVIATION:` flags
- Path to the (post-5.3-rewrite) PRD

**Gate-output-required rule:**
The code-quality-agent's final message is a verbatim `CODE QUALITY GATE:` block with 10 numbered items (items 2 and 7 are both `/vulnerability-scanner` — a mandatory first pass and a post-cleanup second pass). You MUST paste that block into the main transcript exactly as the agent produced it. If the block is missing, has fewer than 10 items, item 7 does not show CLEAN, or any item shows a failure state, you cannot proceed to 5.6 — re-run the agent or escalate to the user.

**Do not print a paraphrased or summarised gate.** The main agent's job is to orchestrate and relay, not to re-author the gate.

**Why two passes?** The Phase 3.5 pre-test pass catches issues that would be wasteful to find after manual testing (significant refactors at Phase 5 invalidate the testing already done). The Phase 5.5 full pass catches issues introduced during the manual-test fix loop — including debug code, dead utilities, and vulnerabilities accidentally introduced by cleanup itself.

### 5.6 UI & Responsiveness Review
- Run `/web-design-guidelines` on all new/changed UI
- Verify cursor/hover info on all new buttons and interactive elements
- Verify responsive across phone (375px), tablet (768px), laptop (1440px)

### 5.7 Documentation — MANDATORY (delegated to two sub-agents, sequential)

**Launch these two sub-agents sequentially (NOT parallel — respect the RAM budget). Each produces a verbatim gate block that must appear in the main transcript.**

#### 5.7a — context-docs-agent

Invoke `.claude/agents/context-docs-agent.md` first (lighter, completes quickly).

**Input** (orchestrator must capture this **before** dispatch — these sub-agents work outside the orchestrator's context window, so feed them the git evidence directly per Sub-Agent Supervision Protocol § 4):
- Feature name
- `git diff --name-status main...HEAD` output (full, not abbreviated)
- `git log --format="%h %s%n%b%n---" main...HEAD` output — **per-commit hashes and bodies, including any `DEVIATION:` flags from Phase 3 / Phase 4 fix sub-agents.** Without this, the docs agent only sees a file list and has no narrative of *why* those files changed.
- List of API route changes, DB schema changes, auth/RLS changes, epic IDs touched, bug IDs resolved
- Path to the now-current PRD and ADR (post-5.3 rewrites)

**Required output:** Paste the agent's verbatim `CONTEXT DOCS GATE:` block into the main transcript. Apply the Sub-Agent Supervision Protocol on return — if the gate block is missing or paraphrased, re-dispatch.

#### 5.7b — docs-auditor-agent

Once context-docs-agent completes, invoke `.claude/agents/docs-auditor-agent.md`.

**Input** (same git-evidence principle as 5.7a):
- Feature name
- `git diff --name-only main...HEAD` output
- `git log --format="%h %s%n%b%n---" main...HEAD` output — per-commit hashes and bodies
- Path to PRD and ADR (post-5.3 rewrites)
- List of new pages/routes, new buttons/modals/panels, new tier-gated behaviours, third-party APIs touched

**Required output:** Paste the agent's verbatim `DOCUMENTATION GATE (User-Facing):` block (with every decision-tree answer shown) into the main transcript. Apply the Sub-Agent Supervision Protocol on return — verify the gate is present and unparaphrased, and `git show` any commits the agent reports.

### 5.8 Gate Output Required Rule — cannot proceed to git until all FIVE gates are pasted

Before Phase 5.9 (Git), the main transcript must contain, in order:
1. The verbatim `RETROSPECTIVE GATE:` block from Phase 5.1 — `Findings swept: N` plus the Gap A / Gap B / Gap C / planning / Tier-3 / deliberately-not-lessoned lines all populated, and the accounting reconciling (lessoned + named = swept). A zero-finding epic still prints it with `Findings swept: 0` and every line `none`.
2. The verbatim **Test-Suite Summary lines** from the Phase 5.4 re-run — both lines (or your test runner's equivalent), with zero unexplained failures (any failures must already be documented in `docs/known-test-failures.md` per Phase 4.2 rules).
3. The verbatim `CODE QUALITY GATE:` block from code-quality-agent (10 items — item 7 must show CLEAN from the post-cleanup vulnerability scan).
4. The verbatim `CONTEXT DOCS GATE:` block from context-docs-agent.
5. The verbatim `DOCUMENTATION GATE (User-Facing):` block from docs-auditor-agent (every decision-tree question answered).

**Enforcement:** If any gate block is missing, truncated, paraphrased, or shows an unresolved failure, Phase 5 is NOT complete. Re-run the source step (re-do the Phase 5.1 sweep for Gate #1; re-run the full suite for Gate #2; re-invoke the relevant sub-agent for Gates #3–5) and escalate to the user if a real failure surfaces. Do NOT commit, do NOT merge, do NOT declare the feature done.

"I ran the checks and everything was fine" is NOT acceptable. The gate blocks (and verbatim test-summary lines) are the proof. No proof = no completion.

### 5.9 Git
- Most code is already committed (per Phase Commit Discipline). The remaining uncommitted work at this point should only be: the Phase 5 retrospective updates to the plan doc, any updated context files from the docs sub-agents, and the rewritten PRD/ADR/Architecture from 5.3. Commit these as the **Phase 5-end commit** per the Commit Discipline table: `docs(<feature-id>): phase 5 — reconciliation + retrospective` with a body summarising plan-vs-reality, autopsy findings, and **confirmation that all FIVE gates passed**.
- Verify the full feature commit log reads cleanly: `git log --oneline main...HEAD` should tell the story of the feature without needing the chat transcript.
- If lint clean and build passes: merge to main.

### 5.10 Next Steps
- Review your epics/roadmap document, identify next logical step with 2-3 options
- Provide a **next-chat handoff prompt** (see format below)

#### Next-chat handoff prompt — REQUIRED format

The handoff must be a **literal, copy-pasteable prompt** the user can paste into a fresh chat, addressed to the next agent. It is NOT a meta-plan addressed to the user describing what they should do.

**Required shape — always a fenced code block:**

```
Read <absolute-path-to-relevant-file> and <other-files-if-any>.
Then <imperative instruction to the next agent>.
<Stop point if any>.
```

**Concrete examples (good — paste-ready):**

````
```
Read /Users/me/projects/myapp/docs/prd/F47-user-export.md and continue
implementation from Phase 3, Task 7. The branch is feature/F47-user-export.
Stop before Phase 4 for my approval.
```
````

````
```
Read /Users/me/projects/myapp/docs/upgrading/v5.6-to-v5.7.md and execute it
against this project. The kit lives at /Users/me/kits/ai-dev-workflow-kit.
Stop after Phase 1 for my approval.
```
````

**Wrong shape — do NOT produce this:**

> In your next chat:
> 1. Open the PRD file
> 2. Continue from Task 7
> 3. The agent should then ask you...

The wrong shape forces the user to translate prose into a prompt. The right shape is paste → enter → work continues.

**Rules:**
- Wrap in a fenced code block so the copy-paste boundary is unambiguous.
- Use absolute paths (not `./docs/...`) — the next chat may have a different working directory.
- Address the next agent directly in imperative form ("Read X. Do Y. Stop at Z."), not the user in third person ("the agent will then…").
- Include any stop point the next agent should respect ("Stop after Phase 1," "Wait for my approval before commits").
- Offer multiple prompt blocks if there are multiple credible next steps — don't make the user pick from a prose menu and then construct the prompt themselves.
- Default behavior: produce this prompt block **without being asked** at the end of any session that left in-flight work. Don't wait for the user to request it.

---

## Summary of Stop Points

Phase 3.5 (pre-test code quality), Phase 4 (browser testing), and Phase 5 (post-test code quality + documentation) run via dedicated sub-agents (`code-quality-agent` in two modes, `testing-agent`, `context-docs-agent`, `docs-auditor-agent`). Each sub-agent produces a verbatim gate block; progression through these phases is gated on those blocks appearing in the main transcript.

In **orchestrator mode**, Phase 3 and the automated portion of Phase 4 also run in sequential phase sub-agents (Phase 2 stays inline in the orchestrator as of v5.7 — see Phase 2.0 for the rationale). Every dispatch goes through the Sub-Agent Supervision Protocol — structured returns, verify-before-trust via `git show`, bounded scope. No new human stop points are introduced — the three deliberation stops below still cover the only moments the AI pauses for the user. Phase 0 is a one-click mode pick.

| When | Why | What happens next |
|------|-----|-------------------|
| Phase 0 — Execution mode pick | Single-chat or orchestrator | AI proceeds to Phase 1 |
| Phase 1.3–1.6 — Questions & Scope | Shape the feature from rough idea to full spec | Approve scope, AI enters plan mode |
| Phase 2.1 — Plan mode approval | Multi-option tradeoffs + perf flags reviewed | Approve plan, AI exits plan mode, writes PRD/ADR, then runs Phases 3–5 |
| Phase 4 STOP — Manual testing handoff | Human verifies on real device/browser | Complete testing, AI runs Phase 5 (in orchestrator mode, fixes go through fix sub-agents) |

Everything else runs autonomously. The AI only adds an unscheduled stop if confidence drops below 90% on a specific decision.

---

## Appendix: Orchestrator Mode Mental Model

```
MAIN AGENT (orchestrator) ── OPUS
│   (planning · dispatching · supervising · reconciliation)
│
├─ Phase 0: ask "single-chat or orchestrator?"
├─ Phase 1: interactive — questions, scope, branch
│
├─ Phase 2: MAIN  ── OPUS  (v5.7 — no longer dispatched; stays in orchestrator context)
│             (Plan Mode, tradeoffs, perf flags, PRD/ADR/Feature Architecture, plan snapshot,
│              testing-agent find-or-create)
│             /plan → /writing-plans → /architecture-patterns → /vulnerability-scanner
│             → commits "docs(<id>): phase 2 — planning artifacts"
│   ◄── orchestrator presents Plan Mode output to user, waits for approval
│
├─ Phase 3: dispatch ──► [Phase 3 sub-agent — possibly multiple sequential batches] ── OPUS
│   │                      │   (executes the approved Phase 2 plan; bounded scope per dispatch)
│   │                      ├─ TDD per PRD task, commits incrementally
│   │                      └─ returns SUB-AGENT RETURN block
│   ◄── orchestrator: `git show <hash> --stat` per returned commit; verify-before-trust
│
├─ Phase 3.5: dispatch ──► [code-quality-agent — Pre-Testing Mode] ── OPUS (agent frontmatter)
│   │                        ├─ items 1–4 only
│   │                        └─ returns CODE QUALITY GATE (Pre-Test) block
│   ◄── orchestrator: verify gate is present + unparaphrased
│
├─ Phase 4 (auto): dispatch ──► [Phase 4 sub-agent / testing-agent] ── SONNET (agent frontmatter)
│   │                             ├─ testing-agent + automated tests + mobile checks
│   │                             └─ returns SUB-AGENT RETURN block w/ verbatim gate
│   ◄── orchestrator: verify gate; `git show` any fix commits
│
├─ Phase 4 (manual): MAIN — user tests, reports failures
│   │
│   └─ for each failure: dispatch ──► [Fix sub-agent] ── OPUS
│       │                              │   (fresh dispatch — isolated context, priors-free reading)
│       │                              ├─ writes failing test, fixes code
│       │                              └─ commits w/ DEVIATION: flag if architecture changed
│       ◄── orchestrator: `git show <hash>` (full diff, not just --stat); note any
│           architectural drift for Phase 5.3 even if the fix-agent didn't flag it
│
└─ Phase 5: MAIN ── OPUS
    │   (reconciliation + the five final gates; git is the ground truth)
    ├─ aggregate: git log + git diff --stat + git log --grep=DEVIATION (Sub-Agent Supervision § 4)
    ├─ Retrospective sweep (5.1) — autopsy every manual-test finding + `fix(` commit → Gate #1 (RETROSPECTIVE GATE, main agent)
    ├─ Plan doc retrospective (5.2)
    ├─ Rewrite PRD/ADR/Architecture to match reality (5.3) — driven by diffs, not transcript
    ├─ Re-run full test suite (5.4) — Gate #2 (verbatim summary lines)
    ├─ dispatch code-quality-agent (Post-Test Mode) ── OPUS → Gate #3
    │   input includes per-commit log so the agent sees what changed and why
    ├─ dispatch context-docs-agent ── SONNET → Gate #4   (same input principle)
    ├─ dispatch docs-auditor-agent ── SONNET → Gate #5   (same input principle)
    ├─ verify all FIVE gates pasted
    └─ commit (Phase 5-end), merge, next steps
```

Sub-agents run **sequentially**, never in parallel on the same feature — **except read-only Phase 4 testing agents (kit v5.10+)**, which may run concurrently when Phase 4.1's capability gate allows (spare memory + distinct test logins), each isolated on its own browser session + port + user. **Every dispatch goes through the Sub-Agent Supervision Protocol** (structured returns + verify-before-trust + bounded scope) — sub-agents are isolated context windows that fail silently if you let them. The supervision protocol applies to each parallel testing agent independently: collect and verify every agent's gate block.

**Model split at a glance:** Opus owns the orchestrator (which now also owns Phase 2 inline), the **Phase 3 implementation sub-agent**, the **`code-quality-agent`** (Pre-Test and Post-Test), the **Phase 4 post-manual-test fix sub-agents**, and Phase 5 reconciliation — everywhere a decision is being made, judged, or debugged. Sonnet owns the Phase 4 automated sub-agent (`testing-agent`), `context-docs-agent`, and `docs-auditor-agent` — everywhere a structured contract or decision tree is being walked. See the model split table at the top of this prompt for the full rule.
