# Feature Lifecycle (v3)

> **ARCHIVED — superseded by v4.** This is the v3 reference. See `docs/prompts/feature-lifecycle.md` for the current version. v3 → v4 added the Gap A (unit/integration test suite) self-improvement loop parallel to v3's Gap B (browser testing-agent library).

> **This is the single prompt for all feature work.** Point the AI at an epic and let it run. There are only **three deliberation stop points** in the entire flow — everything else runs autonomously. Phase 0 adds a one-click execution-mode choice up front; it's not a deliberation stop.

> **Model:** Switch to **claude-opus-4-7** before running this prompt.

> **What changed in v3:**
> 1. **Phase 5.1 Test Autopsy upgraded** — every manual-testing miss now produces TWO closures: a unit-test gap closure (existing) AND a testing-agent gap closure via `docs/prompts/testing-retro.md` (new). The browser-test library now improves over time, not just the unit suite.
> 2. **Testing-agent library is now persistent and indexed** — `docs/testing-agents/REGISTRY.md` is the source of truth for which agents own which scopes. Phase 2.7 does find-or-create against the registry rather than always creating fresh.
> 3. **Phase 4.1 reads the Miss Log** — the base testing-agent now reads each plan's Miss Log on pre-flight so historical gaps inform every run.

> **What changed in v2 (still active):**
> 1. **Phase 0 — Execution mode choice** (single-chat vs orchestrator/sub-agent-per-phase) so you can stay on one chat through long, multi-phase epics without context bloat from the manual-test → fix → re-test loop.
> 2. **Engineering principles preamble** that primes Plan Mode.
> 3. **Plan Mode upgraded** — multi-option tradeoff format for architectural decisions (Effort / Risk / Impact / Maintenance + opinionated recommendation) and **planning-time performance flags**.
> 4. **Phase Commit Discipline** — every phase commits, with body detail rich enough to drive Phase 5 reconciliation off git diffs alone (mandatory in both modes).
> 5. **Phase 4.2 upgraded to FULL SUITE GATE** — verbatim test-summary-line proof, plus `docs/known-test-failures.md` / `docs/known-test-skips.md` discipline so "those failures are unrelated" stops being a free pass.
> 6. **Phase 5.3 — Rewrite PRD/ADR/Architecture to match reality** as an explicit step. The PRD becomes the source of truth for what was built, not what was planned.
> 7. **Phase 5.4 — Mandatory pre-merge full-suite re-run** to catch regressions introduced by the Phase 5.3 doc rewrites and any post-manual-testing fixes.
> 8. **Four gates instead of three** — the test-suite-summary lines from Phase 5.4 are now Gate #1, alongside the three sub-agent gate blocks. Phase 5.8 enforces all four before commit.

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
- **Sequential only.** Never run phase sub-agents in parallel — they'd race on the same files and overheat low-memory machines.
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
| Phase 4 (automated) | `fix(<feature-id>): phase 4 — automated test fixes` (only if fixes were needed) | Tests that failed, root cause per failure, fix applied. |
| Phase 4 (manual fix loop) | `fix(<feature-id>): <bug-summary>` (one per fix) | Bug description, failing-test reproduction, root cause, fix scope. |
| Phase 5 (end) | `docs(<feature-id>): phase 5 — reconciliation + retrospective` | Plan-vs-reality summary, autopsy findings, **confirmation that all FOUR gates passed** (test-suite summary lines from 5.4 + code-quality + context-docs + docs-auditor). |

**Commit message body rules:**
- **Body is mandatory** for Phase 2-end, Phase 3-end, and Phase 5-end commits. A one-line subject is not sufficient — Phase 5.0 reads these bodies to reconstruct what happened.
- **Reference PRD task IDs** wherever possible so the diff-to-plan mapping is mechanical.
- **Flag deviations explicitly** with the line `DEVIATION:` — Phase 5 greps for these.
- **Skip-count drift** (Phase 4.2 rule): if a commit deliberately adds a skipped test, call it out in the commit body and add it to `docs/known-test-skips.md` in the same commit.

**The single-chat agent commits the same way the orchestrator's sub-agents do.** Same hashes get recorded for Phase 5. The only difference between modes is *who* does the work, not what shape the git history has at the end.

---

## Skills to Invoke (across the full lifecycle)

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
- `/simplify` — Code reuse, reduce complexity, DRY
- `/code-review` — Code quality audit
- `/performance` — Performance checks
- `/web-design-guidelines` — UI/UX and accessibility
- `/agent-browser` — Browser automation testing

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

- **Single-chat mode:** Main agent runs Phase 2 inline. Continue with 2.1.
- **Orchestrator mode:** Main agent dispatches a Phase 2 sub-agent. Input contract:
  - Epic / Feature ID
  - Scope table (from Phase 1.6)
  - All decisions captured during Phase 1 questions
  - Paths to context files (`docs/context/Context_Index_File.md`, `Project_PDR.md`, `database_reference_guide.md`, `API_REFERENCE.md`, `Project_Authentication.md`)
  - Branch name from Phase 1.1
  - **Expected output back to orchestrator:**
    - Verbatim Plan Mode output (the structured plan from 2.1, including the multi-option tradeoffs, perf flags, and risk flags) — pasted into the main transcript so the user can approve.
    - Paths to: PRD, ADR, Feature Architecture doc, plan snapshot doc, feature-specific testing-agent file.
    - Final commit hash with message `docs(<feature-id>): phase 2 — planning artifacts`.
  - **The orchestrator records the Phase 2 commit hash for Phase 5's git-diff comparison.**
  - The orchestrator presents the Plan Mode output to the user for the Phase 2 stop point. Sub-agent does NOT solicit user input directly — only the orchestrator does.

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
- **Orchestrator mode:** Main agent dispatches a Phase 3 sub-agent. Input contract:
  - Feature ID
  - Branch name
  - Paths to PRD, ADR, Feature Architecture, plan snapshot
  - Resource caps from 3.1 below
  - **Expected output back to orchestrator:**
    - List of commits made (one logical commit per PRD section is the ideal — definitely no fewer than one for the whole phase). Final commit message includes `feat(<feature-id>): phase 3 — implementation`.
    - Brief summary (≤200 words) of any deviations from the plan and why. Detail goes in the diffs, not the summary — Phase 5 will read the diffs.
    - Any confidence-below-90% questions that came up during implementation that need user input *before* Phase 4. Do NOT defer them to the end report.
  - **The sub-agent must NOT touch Phase 4 or Phase 5 work.** It returns control to the orchestrator after implementation completes.
  - **The orchestrator records all Phase 3 commit hashes** (start + end) for the Phase 5 git-diff comparison.

### 3.1 Resource Management (CUSTOMIZE — adjust for your hardware)

> **[CUSTOMIZE]** The rules below are written for a 16GB MacBook Air with no fan. Adjust caps/limits for your hardware. If you're on a workstation with 64GB+ RAM, you can relax the parallel-agent cap and the "never full test suite" rule, but keep the "kill processes when done" discipline — orphaned dev servers and test workers still cost memory even on large machines.

- **Max 2 parallel agents at a time** *inside* a phase (raise for large-memory hardware). **Phase sub-agents themselves always run sequentially**, never overlap.
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

Run `/tdd` to enforce this cycle for each PRD task:

1. Write failing test
2. Implement minimum code to pass
3. Run `/simplify` — check for code reuse opportunities, reduce complexity
4. Lint
5. Tick off PRD task
6. Move to next task

**Test Rule:** If a test fails, fix the **code**, not the test — unless the test logic itself is wrong.

**Confidence Rule:** If confidence drops below 90% on any requirement or architectural decision, **STOP** and ask the user (multiple-choice format). Otherwise keep going.

- In **orchestrator mode**, the Phase 3 sub-agent surfaces the question to the orchestrator immediately (not at the end). The orchestrator relays to the user.

### 3.4 Mid-Implementation Checks
- After every major component/feature addition: run `[TEST_COMMAND]` to catch regressions early
- Run `/coding-standards` periodically to prevent drift
- Keep tests passing at all times — never accumulate broken tests

### Implementation complete → Proceed immediately to Phase 4. Do not stop.

---

## PHASE 4: Verification (AUTONOMOUS → STOPS for manual testing → AUTONOMOUS)

### 4.0 Mode-specific behavior

- **Single-chat mode:** Main agent runs Phase 4 inline (4.1–4.4 then STOP).
- **Orchestrator mode:**
  - **Automated portion (4.1–4.3) runs in a Phase 4 sub-agent.** Input contract:
    - Feature name, branch, dev server URL, path to test plan, tier/role list.
    - Expected output: verbatim PASS/FAIL/SKIP block from `testing-agent` + automated test results + mobile responsiveness summary + any code fixes the sub-agent made (with commits and `fix(<feature-id>): phase 4 — automated test fixes`).
  - **Manual testing (the STOP) always runs in main.** This is intentional: manual testing is interactive (the user pastes results, asks follow-ups, says "wait, this is broken"), so it has to be in the orchestrator's chat.
  - **Manual-test fix loop = one fix sub-agent per fix.** When a manual test fails:
    1. Orchestrator captures the bug description and which test exposed it.
    2. Orchestrator dispatches a focused fix sub-agent: input = bug description, failing reproduction steps, paths to PRD/ADR, scope guardrails ("only touch these files unless absolutely necessary").
    3. Fix sub-agent writes a failing test first (TDD), fixes the code, runs `[TEST_COMMAND]` and `[BUILD_COMMAND]`, commits with `fix(<feature-id>): <bug-summary>`, returns commit hash + summary.
    4. Orchestrator records the commit hash and asks the user to re-test.
    5. Repeat until manual testing passes.
  - **Why this matters:** without this, the fix loop sits in the orchestrator's context. After 5–6 iterations the orchestrator is full of stale debug back-and-forth and Phase 5 reconciliation gets noisy.

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
5. Wait for the testing-agent to complete and paste its full test results block (PASS/FAIL/SKIP table + Reliability Notes) into the main transcript. The main agent must NOT paraphrase — paste verbatim.

**If the testing-agent asks for help (bad creds, missing env, wrong branch), answer it immediately. Do not defer to the end report.**

Once the testing-agent completes, it handles its own RAM teardown. The main agent should NOT invoke agent-browser or MCP browser tools directly.

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

> **[CUSTOMIZE]** Your project's `docs/known-test-failures.md` and `docs/known-test-skips.md` are recommended discipline files — create them on first feature, append on every feature that touches the baseline. They make pre-existing-failure exemptions auditable instead of invisible.

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

**If manual testing revealed bugs or issues:** Before fixing anything, run a **Test Autopsy** for each bug. The v3 autopsy closes **two** gaps per bug, not one:

**Gap A — The unit/integration test suite:**

1. **Why did automated tests pass when this feature was broken?** Identify the specific gap — wrong assertions, over-mocking, missing edge case, testing implementation instead of behavior, or missing integration coverage.
2. **Write new/rewritten tests first** that fail against the current broken behavior (TDD — the fix starts with a failing test, always).
3. **Then fix the code** and verify the new tests pass.
4. Re-run `[TEST_COMMAND]` and `[BUILD_COMMAND]` after fixes.

**Gap B — The testing-agent library (NEW in v3):**

5. **Why did the testing agent that ran in Phase 4.1 not catch this?** Run `docs/prompts/testing-retro.md` against this bug. The retro:
   - Identifies which agent in `docs/testing-agents/REGISTRY.md` owned the scope (or flags "no owner" if no agent covered it — itself a finding).
   - Categorizes the miss (A scope / B scenario / C assertion / D verification / E setup).
   - Either adapts the existing agent or creates a new one via `create-testing-agent.md` Adapt Mode.
   - Appends a Miss Log entry on the agent.
   - Decides tier propagation (feature-only / project-pattern → `docs/context/Testing_Patterns.md` / skill-universal → kit-level change with user approval).
6. If fixes are significant, re-run the relevant browser tests from Phase 4 against the updated agent to confirm the new scenario now FAILs against pre-fix code (or PASSes against fixed code).

**Do NOT just fix the code and move on.** If tests passed while the feature was broken, the test suite has a gap. If the testing agent passed while the feature was broken, the agent library has a gap. Closing both is as important as fixing the bug itself — otherwise the same class of bug escapes again on the next feature.

(In orchestrator mode, the Phase 4 fix-sub-agent loop has typically already done the autopsy + fix per bug. Phase 5's job here is to consolidate the autopsy findings across all bugs — both Gap A and Gap B closures — into one coherent retrospective entry, and to surface any Tier-2 / Tier-3 propagations for user approval.)

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

After manual testing and any resulting fixes, rewrite these docs:

- **PRD:** Rewrite task descriptions to reflect what was actually implemented. Mark completed items (`- [x]`). Remove or update tasks that changed. Note deviations from the original plan with reasoning. Add an "Implementation Notes" section: patterns that worked, pitfalls to avoid.
- **ADR:** Update technical approach, data flow, and schema to match the actual implementation. If architectural decisions changed during development, rewrite the justification to reflect the real decision, not the planned one. Keep the rejected options (the ADR is also a historical record).
- **Feature Architecture file:** Rewrite any sections where the implementation differs from the original design. The button-to-DB flow, data architecture, and integration points must describe the code as it actually exists.

Run `/technical-writing` to ensure quality. **This is not box-ticking — it's a rewrite pass.** The plan doc (5.2) preserves the journey; these docs preserve the destination.

### 5.4 Re-run the Full Test Suite (mandatory pre-merge)

**Even if Phase 4.2 was clean, re-run the full suite after:**
- Any post-manual-testing fix
- The PRD/ADR/Architecture rewrites in 5.3 (in case anything regressed)

Re-run `[TEST_COMMAND]` and `[BUILD_COMMAND]`. **Paste the verbatim summary lines into the transcript** — these become Gate #1 in 5.8. The same FULL SUITE GATE rules from Phase 4.2 apply: full suite, no path arguments, no "tests pass" without proof.

Do not skip this on the basis that "I only edited docs in 5.3" — the gate is procedural, not contingent on what changed.

### 5.5 Code Quality & Security — MANDATORY (delegated to code-quality-agent)

**Delegate this section to the `code-quality-agent` sub-agent at `.claude/agents/code-quality-agent.md`. Do NOT run `/code-review`, `/vulnerability-scanner`, `/simplify`, `/performance`, or `/coding-standards` inline — the agent runs them in the correct order with the correct failure handling.**

**Invocation input:**
- Feature name
- List of changed files (`git diff --name-only main...HEAD`)
- Path to the PRD

**Gate-output-required rule:**
The code-quality-agent's final message is a verbatim `CODE QUALITY GATE:` block with 11 numbered items (items 2 and 8 are both `/vulnerability-scanner` — a mandatory first pass and a post-cleanup second pass). You MUST paste that block into the main transcript exactly as the agent produced it. If the block is missing, has fewer than 11 items, item 8 does not show CLEAN, or any item shows a failure state, you cannot proceed to 5.6 — re-run the agent or escalate to the user.

**Do not print a paraphrased or summarised gate.** The main agent's job is to orchestrate and relay, not to re-author the gate.

### 5.6 UI & Responsiveness Review
- Run `/web-design-guidelines` on all new/changed UI
- Verify cursor/hover info on all new buttons and interactive elements
- Verify responsive across phone (375px), tablet (768px), laptop (1440px)

### 5.7 Documentation — MANDATORY (delegated to two sub-agents, sequential)

**Launch these two sub-agents sequentially (NOT parallel — respect the RAM budget). Each produces a verbatim gate block that must appear in the main transcript.**

#### 5.7a — context-docs-agent

Invoke `.claude/agents/context-docs-agent.md` first (lighter, completes quickly).

**Input:**
- Feature name
- `git diff --name-status main...HEAD` output
- List of API route changes, DB schema changes, auth/RLS changes, epic IDs touched, bug IDs resolved

**Required output:** Paste the agent's verbatim `CONTEXT DOCS GATE:` block into the main transcript.

#### 5.7b — docs-auditor-agent

Once context-docs-agent completes, invoke `.claude/agents/docs-auditor-agent.md`.

**Input:**
- Feature name
- `git diff --name-only main...HEAD` output
- Path to PRD and ADR
- List of new pages/routes, new buttons/modals/panels, new tier-gated behaviours, third-party APIs touched

**Required output:** Paste the agent's verbatim `DOCUMENTATION GATE (User-Facing):` block (with every decision-tree answer shown) into the main transcript.

### 5.8 Gate Output Required Rule — cannot proceed to git until all FOUR gates are pasted

Before Phase 5.9 (Git), the main transcript must contain, in order:
1. The verbatim **Test-Suite Summary lines** from the Phase 5.4 re-run — both lines (or your test runner's equivalent), with zero unexplained failures (any failures must already be documented in `docs/known-test-failures.md` per Phase 4.2 rules).
2. The verbatim `CODE QUALITY GATE:` block from code-quality-agent (11 items — item 8 must show CLEAN from the post-cleanup vulnerability scan).
3. The verbatim `CONTEXT DOCS GATE:` block from context-docs-agent.
4. The verbatim `DOCUMENTATION GATE (User-Facing):` block from docs-auditor-agent (every decision-tree question answered).

**Enforcement:** If any gate block is missing, truncated, paraphrased, or shows an unresolved failure, Phase 5 is NOT complete. You must re-invoke the relevant sub-agent (or re-run the full suite for Gate #1) and escalate to the user if a real failure surfaces. Do NOT commit, do NOT merge, do NOT declare the feature done.

"I ran the checks and everything was fine" is NOT acceptable. The gate blocks (and verbatim test-summary lines) are the proof. No proof = no completion.

### 5.9 Git
- Most code is already committed (per Phase Commit Discipline). The remaining uncommitted work at this point should only be: the Phase 5 retrospective updates to the plan doc, any updated context files from the docs sub-agents, and the rewritten PRD/ADR/Architecture from 5.3. Commit these as the **Phase 5-end commit** per the Commit Discipline table: `docs(<feature-id>): phase 5 — reconciliation + retrospective` with a body summarising plan-vs-reality, autopsy findings, and **confirmation that all FOUR gates passed**.
- Verify the full feature commit log reads cleanly: `git log --oneline main...HEAD` should tell the story of the feature without needing the chat transcript.
- If lint clean and build passes: merge to main.

### 5.10 Next Steps
- Review your epics/roadmap document, identify next logical step with 2-3 options
- Provide a **context summary block** for the next context window

---

## Summary of Stop Points

Phase 4 (browser testing) and Phase 5 (code quality + documentation) run via dedicated sub-agents (`testing-agent`, `code-quality-agent`, `context-docs-agent`, `docs-auditor-agent`). Each sub-agent produces a verbatim gate block; progression through Phase 4 and Phase 5 is gated on those blocks appearing in the main transcript.

In **orchestrator mode**, Phases 2, 3, and the automated portion of Phase 4 also run in sequential phase sub-agents. No new human stop points are introduced — the three deliberation stops below still cover the only moments the AI pauses for the user. Phase 0 is a one-click mode pick.

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
MAIN AGENT (orchestrator)
│
├─ Phase 0: ask "single-chat or orchestrator?"
├─ Phase 1: interactive — questions, scope, branch
│
├─ Phase 2: dispatch ──► [Phase 2 sub-agent]
│                          ├─ /plan, /writing-plans, /architecture-patterns, /vulnerability-scanner
│                          ├─ writes PRD, ADR, Feature Architecture, plan snapshot, test plan
│                          └─ commits → returns Plan Mode output + paths + commit hash
│   ◄── orchestrator presents Plan Mode output to user, waits for approval
│
├─ Phase 3: dispatch ──► [Phase 3 sub-agent]
│                          ├─ TDD per PRD task
│                          ├─ commits incrementally
│                          └─ returns commit hashes + deviation summary
│
├─ Phase 4 (auto): dispatch ──► [Phase 4 sub-agent]
│                                 ├─ testing-agent + automated tests + mobile checks
│                                 └─ returns verbatim gate blocks
│
├─ Phase 4 (manual): MAIN — user tests, reports failures
│   │
│   └─ for each failure: dispatch ──► [Fix sub-agent]
│                                       ├─ writes failing test, fixes code
│                                       └─ commits, returns
│
└─ Phase 5: MAIN
    ├─ git-diff comparison plan vs reality (uses recorded commit hashes)
    ├─ Plan doc retrospective (5.2)
    ├─ Rewrite PRD/ADR/Architecture to match reality (5.3)
    ├─ Re-run full test suite (5.4) — Gate #1 (verbatim summary lines)
    ├─ dispatch code-quality-agent → Gate #2
    ├─ dispatch context-docs-agent → Gate #3
    ├─ dispatch docs-auditor-agent → Gate #4
    ├─ verify all FOUR gates pasted
    └─ commit (Phase 5-end), merge, next steps
```

Sub-agents run **sequentially**, never in parallel on the same feature.
