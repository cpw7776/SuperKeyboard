# Create Testing Agent (v3.1)

> **Purpose:** Maintain the project's persistent library of testing agents. **Find-or-create**: check the registry for an applicable agent first; only generate a new one when no existing agent covers the needed scope. Also used in **Adapt Mode** to rewrite an existing agent based on a `testing-retro.md` finding.

> **Output (Create Mode):** A test plan file saved to `docs/testing-agents/{feature-name}-tests.md`, registered in `docs/testing-agents/REGISTRY.md`.

> **Output (Adapt Mode):** Targeted edits to an existing test plan file based on a retro's root-cause analysis. No new file.

> **Base agent:** Generated/adapted files are executed by `.claude/agents/testing-agent.md` during Phase 4 or on-demand.

> **Companion protocol:** When this agent's test plan misses a real bug, run `docs/prompts/testing-retro.md` to feed the miss back into the library.

---

## Modes

### Create Mode (default — invoked by Phase 2.7 of `feature-lifecycle.md`)

Input: a PRD + ADR + Feature Architecture. Output: a new test plan file plus a registry entry. Used when standing up testing coverage for a new feature.

### Adapt Mode (invoked by `testing-retro.md` Phase 3.2, "For adaptation")

Input: an existing test plan file + a Miss Category (A–E from the retro) + the bug description. Output: targeted edits to the existing file (new scenarios, strengthened assertions, widened scope, etc.) plus a Miss Log entry and a `last_updated` bump. **Do not regenerate the whole file** — apply minimal, surgical edits that close the specific gap.

### Retro-Create Mode (invoked by `testing-retro.md` Phase 3.2, "For new agent")

Input: a bug description + Miss Category + the affected routes/components/roles/async pathways (no PRD needed). Output: a new test plan file scoped to the area the retro identified as having no current owner. Follow the Create Mode flow below, but:
- The "Read source documents" step (Step 1) is replaced by reading the retro's inputs.
- The new agent's header includes `Created from retro: {bug summary} on {date}` to trace its origin.
- The file name describes the SCOPE the agent owns (e.g., `enterprise-tier-billing-tests.md`), not the bug.
- The Miss Log section is seeded with the original miss as the first entry (the retro can append it directly, since this agent was born from that miss).

Skip directly to the relevant section (**Adapt Mode** for edits, **Create Mode** Steps 0–7 below for Retro-Create) when invoked from a retro.

---

## Step 0 (Create Mode): Registry Check — Find-or-Create

**Before generating anything, check the registry.**

1. Read `docs/testing-agents/REGISTRY.md`.
2. For each agent listed, compare its **Scope / Purpose**, **Routes Covered**, **Roles / Tiers**, and **Async** columns against the feature you're about to test.
3. Decide:
   - **Full overlap** with an existing agent → STOP creating a new file. Instead, switch to **Adapt Mode** to extend the existing agent's scope. Note the decision in the retro/commit message.
   - **Partial overlap** → Decide whether to extend one of the overlapping agents or create a new sibling agent. If splitting, the new agent's header must include a `Split from: {other-agent.md}` line.
   - **No overlap** → Proceed with Create Mode below.
4. If the registry doesn't exist yet, create it from `docs/testing-agents/REGISTRY.md` (the kit ships a template).

This step prevents the slow accumulation of redundant overlapping agents.

---

## Input Required

You need two documents to generate the testing agent:

1. **PRD** — from `docs/prd/`. Contains:
   - Task list (what was built)
   - `STOP: Human Verification` markers (what needs manual testing)
   - Human Testing Plan (click-by-click instructions)
   - Acceptance criteria

2. **Architecture Doc** — from `docs/ard/` or `docs/architecture/`. Contains:
   - User flows (button-to-database)
   - API routes involved
   - Auth/authz requirements
   - Data model
   - Role/tier gating rules

---

## Generation Process

### Step 1: Read the Source Documents

```
Read the PRD file: docs/prd/{feature}.md
Read the ARD file: docs/ard/{feature}.md
Read the architecture file: docs/architecture/Feature_Architecture_{Feature}.md (if exists)
```

### Step 1.5: Read Project Test Patterns (if present)

Before generating scenarios, read `docs/context/Testing_Patterns.md` if it exists. This file accumulates **project-wide testing lessons** distilled from past retros (Tier-2 propagations from `testing-retro.md`). Examples of patterns it might contain:

- "This app uses optimistic UI updates everywhere — always wait for the API response, not the UI flicker, before declaring success."
- "Auth tokens in this project expire after 30 minutes — long-running test runs need a re-auth step."
- "Every form has both client-side and server-side validation — agents must always test the server-side path with a payload that bypasses the client validator."

**Every pattern in this file applies to every test plan you generate**, not just this feature. Bake them into the relevant scenarios and pass criteria. If the file doesn't exist, that's fine — it's created on the first Tier-2 retro.

### Step 2: Extract Test Dimensions

From the PRD and architecture docs, identify:

**User Flows:**
- What are the primary happy paths? (user does X → sees Y)
- What are the error/edge cases? (empty input, timeout, no auth, wrong role/tier)
- What async operations exist? (AI generation, file upload, webhook callbacks)

**Auth & Role/Tier Matrix:**
- Which roles/tiers can access this feature?
- Which roles/tiers are blocked? (need gate tests)
- Does behavior differ by role/tier? (e.g., different permissions or feature access)
- Does it require auth? (need unauthenticated redirect test)

**Routes & Pages:**
- What URLs does the user visit?
- What API routes are called?
- What are the navigation transitions?

**Form Inputs:**
- What fields exist?
- Which are required vs optional?
- What validation rules apply?
- What values should the test use?

**Async Operations:**
- What triggers async work?
- How long does it take? (set appropriate timeouts)
- What does the loading state look like?
- What does the success state look like?
- What does the error state look like?

**Responsive Requirements:**
- Does this feature have mobile-specific layout?
- Are there touch targets to verify?

### Step 3: Generate Test Scenarios

Create test scenarios following this template for each:

```markdown
### T{N}: {Descriptive Name}

**Role/Tier:** {e.g., Unauthenticated | Free | Pro | Admin — customize for your project's roles}
**Priority:** {Critical | High | Medium | Low}
**Type:** {Auth gate | Smoke test | End-to-end | Interaction | Validation | Responsive | Role-specific | Error handling}
**Async:** {Yes — expected {min}–{max} seconds | No}
**Expected duration (whole scenario):** {e.g., "10s", "2min", "5-15min"} — used by the testing-agent's self-pacing watch loop (kit v5.7+). Total wall time expected to complete this scenario including all steps.
**API endpoint:** {POST /api/... | None}
**Depends on:** {T{M} if it requires a prior test's state | None}

**Steps:**
1. {Concrete action — navigate, click, fill, wait} `[~Xs]`  ← per-step expected duration in square brackets for any step ≥ 5s or that involves a wait/async. Sub-second clicks/fills don't need a bracket — the agent uses the default heuristic. Examples:
   - `Navigate to /dashboard [~3s]`
   - `Click "Start backup" @e7` (no bracket — heuristic 1s)
   - `Wait for backup completion [~5-15min]` (range OK for highly variable async)
2. {Each step should be one browser action}
3. {Use real form values, not placeholders}
4. {For steps that trigger API calls, note: "→ triggers POST /api/..."}

**Pass criteria (ALL must be true):**
- **Render:** {specific observable outcome on the rendered page — exact text, element count, component visible}
- **Submit (Pattern A — REQUIRED for any scenario with a form, modal, or interactive action):** {network endpoint returns 2xx AND expected side effect — DB row written / next-page render / success toast. Render-only PASS is NOT acceptable evidence — see Required Scenario Patterns below.}
- **Console:** no errors related to {feature API path}
- **Timing:** {if async: completes within min–max range. Faster than min = suspicious}

**Fail indicators (ANY means FAIL):**
- **Network:** {endpoint} returns 4xx/5xx, or no API call made at all
- **Console:** errors containing "{api path}" or "400" or "500"
- **UI:** error toast appears, loading stops without results, page unchanged after submit
- **Timing:** completes instantly when async operation expected (< 3 seconds = not actually running)

**BLOCKED_NEEDS_FIXTURE triggers** (kit v5.8+ — Pattern A guard):
- The render half passed but the submit/action half could not be exercised because of a missing fixture, credential, or DB precondition. Verdict is `BLOCKED_NEEDS_FIXTURE`, NOT `PASS`. Record what's missing in the Reporting Format.

**REQUIRES_INPUT triggers** (kit v5.8+ — Pattern C guard):
- The agent observed something it cannot classify as PASS or FAIL without orchestrator judgment (e.g. "admin chrome visible to lower tier in one corner — intentional or leak?"). Emit the Requires Input section with 2–3 interpretations, a recommended default, and the disambiguating question. Do NOT downgrade to FAIL. Do NOT pick PASS.

**ABORT triggers** (testing-agent declares ABORT, not FAIL — kit v5.7+):
- Any single step exceeds ~3× its bracketed expected duration with no observable progress (DOM unchanged, no network activity, no URL change, no error state). The agent screenshots the last state, logs network/console, marks the scenario ABORT, moves on.
- If you expect a step to legitimately take longer than its bracket on slow machines, widen the bracket in the test plan rather than ignoring ABORTs.

**Verify:**
- {What to check — page text, URL, element presence}
- Screenshot before: `{test-id}-before.png`
- Screenshot after: `{test-id}-after.png`
```

### Step 4: Design Execution Order

Arrange tests to minimize login/logout cycles:

1. Unauthenticated tests first (no login needed)
2. Lowest-privilege role/tier tests (login once)
3. Mid-privilege role/tier tests (login once)
4. Highest-privilege role/tier tests (login once)
5. Admin tests if applicable

Within each role/tier group, order by dependency (tests that create state needed by later tests go first).

### Step 5: Set Configuration

```yaml
feature: {kebab-case-feature-name}
evidence_dir: /tmp/test-evidence/{feature-name}
roles_required: [{list of roles/tiers this feature touches}]
async_timeout: {max seconds for the longest async operation}
dev_server: http://localhost:{PORT}
credentials_source: .env.local

# v5.7+ self-pacing — testing-agent reads these
default_action_timeout: 30s   # heuristic-fallback hard cap for any browser action without an explicit bracket
run_hard_cap: 45min           # whole-run wall-time cap; agent declares RUN_ABORT if exceeded. Tune upward for projects with very long async waits.
progress_log: /tmp/test-evidence/{feature-name}/progress.log   # tail -f from another terminal during the run

# v5.10+ parallel verification — feature-lifecycle Phase 4.1 reads these to decide concurrency
parallel_safe: false          # true ONLY if this plan can run CONCURRENTLY with OTHER plans (see decision guide below). Default false = always sequential.
parallel_isolation: distinct-login   # how safety is achieved when parallel_safe=true:
                              #   distinct-login  — each concurrent run needs its OWN test user (shared DB ok; same user = clash)
                              #   read-only       — scenarios never write persisted state; any/no login, unlimited concurrency
                              #   isolated-backend— each dev-server port has its own DB/schema; full isolation
```

**Process safety:** When the testing agent runs unit/integration tests as part of verification, it MUST use single-run commands only (e.g., `npx vitest run`, NOT `npx vitest`). Watch mode spawns persistent workers that consume CPU indefinitely. If any test process hangs, kill it immediately (`pkill -f vitest`) before continuing.

#### Parallel-safety classification (kit v5.10+) — set `parallel_safe` deliberately

The orchestrator (`feature-lifecycle.md` Phase 4.1) can run several testing agents **concurrently** to cut wall-clock time on a capable machine — but only for plans you mark `parallel_safe: true`. Classify this plan now, while you understand its scenarios. **Default is `false`; flipping to `true` is a positive claim you must justify.**

Walk these in order:

1. **Do any scenarios write persisted state** (create/update/delete rows, change settings, upload, mutate another user's view)?
   - **No — purely read-only / UI-only** → `parallel_safe: true`, `parallel_isolation: read-only`. Safe at any concurrency; needs no dedicated login.
   - **Yes** → continue.
2. **Are all writes confined to the acting user's OWN data** (this user's records, this user's settings — nothing global or cross-user)?
   - **Yes** → `parallel_safe: true`, `parallel_isolation: distinct-login`. Safe **as long as each concurrent agent gets a different test user** — the orchestrator enforces this and will cap concurrency at the number of distinct logins available. A shared DB is fine; two agents as the *same* user is the clash.
   - **No — writes touch global/shared/singleton state** (a global config, the only admin account, a shared catalogue, a singleton resource) → continue.
3. **Does the project give each dev-server port its own isolated DB/schema?**
   - **Yes** → `parallel_safe: true`, `parallel_isolation: isolated-backend`.
   - **No** → `parallel_safe: false`. This plan mutates shared exclusive state that concurrency would corrupt — keep it sequential. A false FAIL from cross-agent interference is worse than a slower run.

**Edge case — same exclusive role.** If two *different* plans both require the same single privileged user (e.g. the one admin), they can't run concurrently with each other even when individually `parallel_safe`. That's fine: the orchestrator serializes same-exclusive-user plans and parallelizes across distinct-user groups. You only classify THIS plan; the orchestrator handles cross-plan allocation.

### Step 6: Write the File

Save to `docs/testing-agents/{feature-name}-tests.md` using this structure:

```markdown
# Testing Agent: {Feature Name}

> **Feature:** {Feature Name}
> **Routes:** {comma-separated list of routes}
> **Generated from:** {PRD filename} + {ARD filename}
> **Base agent:** `.claude/agents/testing-agent.md`
> **Created:** {YYYY-MM-DD}
> **Last Updated:** {YYYY-MM-DD}

---

## Test Configuration
{yaml block from Step 5}

---

## Credentials Setup
{standard credential extraction — copy from existing test plan or document your credential loading approach}

---

## Test Scenarios
{all T1..TN scenarios from Step 3}

---

## Execution Order
{ordered list from Step 4, with rationale for grouping}

---

## Cleanup
{any feature-specific cleanup instructions}

---

## Previous Results
{empty results table for tracking across invocations}

| Test | Status | Notes |
|------|--------|-------|
{one row per test, all pending}

---

## Miss Log

> **Read this section on every run as part of pre-flight.** Each entry is a past bug this agent should have caught but didn't. Treat them as scenarios that must continue to pass — if any of them ever resurfaces, the agent's improvement regressed.

> **Populated by:** `docs/prompts/testing-retro.md` (the retro protocol). Never edit manually; always go through the retro so the categorization is consistent.

{No misses yet. Entries appended here as retros happen — see testing-retro.md.}
```

### Step 7: Update the Registry

After writing the new test plan file, update `docs/testing-agents/REGISTRY.md`:

1. Open the registry.
2. Add a row to the **Registry** table with the new agent's filename, scope, routes, roles, viewports, async flag, and today's date as `Last Updated`. Leave `Last Miss` empty until the first retro.
3. If this agent supersedes or splits from an existing one, note that in the header of the new test plan file and update the predecessor's row accordingly.
4. Commit the registry update in the same commit as the new test plan file. **Never let the registry drift from the actual file list.**

---

## Required Scenario Patterns (kit v5.8+)

> **Two patterns every test plan MUST honour.** Both are framework-agnostic and were added in v5.8 after a class of silent-pass bugs that the v2 scenario template did not prevent. The runtime contract for both patterns is implemented in `.claude/agents/testing-agent.md` (Reporting Format + Rules 21–22) — this generator and the base agent must move together.
>
> If you bypass these patterns when generating a plan, the base agent's verdict legend still includes the new verdicts and the parent agent will treat the absence of pattern-shaped pass/fail criteria as a Quality Checklist failure.

### Pattern A — End-to-end submit assertion

Every scenario for a form, modal, or interactive flow MUST include both:

1. A **render assertion** — does the UI display the correct state? (Correct fields visible, correct options enabled, correct copy.)
2. A **submit/action assertion** — does the user-actionable button/link actually succeed end-to-end? Verify at least one of: network response 2xx AND expected side effect (DB row written, expected next-page render, expected success toast). **Render-only PASS is not acceptable evidence** for a feature that involves submission.

If a scenario cannot perform the submit step because of a missing fixture, credential, or precondition, the base agent emits `BLOCKED_NEEDS_FIXTURE` (NOT `PASS`, NOT `SKIP`). Record what's missing in the scenario's Credentials Setup or Test Configuration so the parent agent can provision it and re-dispatch.

**Bug class observed:** a form rendered the correct UI (correct picker hidden, correct default badge present) while still sending stale data on submit — the submit-time payload was never asserted, so render-only PASS hid the regression. Pattern A makes the submit assertion non-skippable.

**Apply in the scenario template by:**
- Including BOTH a `Render:` line AND a `Submit:` line under **Pass criteria** (the Step 3 template now has both — don't collapse them).
- Treating "Submit assertion absent" as a Quality Checklist failure for this scenario.

### Pattern C — REQUIRES_INPUT verdict for ambiguity

When a scenario produces a finding that the testing-agent cannot categorize as PASS or FAIL **without orchestrator/human judgment**, emit a structured `REQUIRES_INPUT` verdict — NOT an informational note in Reliability Notes.

Reliability Notes are for low-confidence verdicts on scenarios the agent DID rule on. `REQUIRES_INPUT` is for scenarios where the agent explicitly declines to rule because the finding is genuinely ambiguous (e.g. "intentional admin-only chrome" vs "leak that should have been tier-gated"). The verdict shape is enforced by the base agent's Reporting Format → Requires Input section (finding, 2–3 interpretations, recommended default, disambiguating question).

**Bug class observed:** a feature-leak finding ("admin-only chrome visible to lower tier in one corner of the Settings page") was logged as Reliability Notes prose and escaped Phase 4 because the verdict was PASS — the parent agent acted on the verdict, not the prose. `REQUIRES_INPUT` forces the ambiguity into the verdict layer where it cannot be ignored.

**Apply in the scenario template by:**
- In the scenario's narrative or **Verify** section, surface any case where the agent might face this ambiguity ("if X is visible, is that intentional?") so the agent has explicit permission to emit `REQUIRES_INPUT` instead of guessing.

### Pattern B — DB preconditions (opt-in, NOT required)

If your project needs to run scenarios that require specific pre-set DB state (a token balance, a feature flag value, a billing tier set in a specific way), the kit ships an **opt-in pattern** at `docs/patterns/db-precondition.md` covering: scenario template fields (`requires_db_state` / `db_setup_sql` / `db_teardown_sql`), runtime contract (configurable DB-execute MCP tool name; `MANUAL_BLOCKER` verdict when the tool isn't available), and dialect-specific SQL examples. Pattern B is NOT shipped in kit vanilla because the runtime contract hardwires a project-specific DB-execute tool (e.g. `mcp__supabase__execute_sql`); see the pattern doc for opt-in instructions. Patterns A and C apply whether or not you adopt Pattern B.

---

## Quality Checklist (Create Mode)

Before saving the generated file, verify:

- [ ] Registry check completed (Step 0) — confirmed no existing agent owns this scope
- [ ] Project test patterns read (Step 1.5) — `docs/context/Testing_Patterns.md` patterns baked into scenarios
- [ ] Every `STOP: Human Verification` in the PRD has a corresponding test scenario
- [ ] Every API route in the architecture doc is exercised by at least one test
- [ ] Auth gate tests exist for every role/tier that should be blocked
- [ ] At least one test covers the primary happy path end-to-end
- [ ] Every form has a validation test (empty required fields)
- [ ] Async operations have explicit timeouts AND minimum expected times
- [ ] **Every scenario has an `Expected duration (whole scenario)` line** — used by the testing-agent's self-pacing watch loop
- [ ] **Every step ≥ 5s or involving a wait/async has a `[~Xs]` or `[~X-Ymin]` bracket** — sub-second clicks/fills can omit (heuristic table covers them)
- [ ] `default_action_timeout` and `run_hard_cap` set in `Test Configuration`
- [ ] **`parallel_safe` set deliberately** (kit v5.10+) — the parallel-safety classification was walked, not defaulted; if `true`, `parallel_isolation` names how (read-only / distinct-login / isolated-backend)
- [ ] A mobile responsive test exists (375px viewport)
- [ ] Tests use concrete form values, not placeholders like "test" or "example"
- [ ] Execution order minimizes login/logout cycles
- [ ] **Every test that triggers an API call specifies the expected endpoint and status code**
- [ ] **Every test has explicit FAIL indicators, not just success criteria**
- [ ] **Every async test has a minimum expected time (to catch fake completions)**
- [ ] **Pattern A (kit v5.8+):** every scenario with a form/modal/submit step has BOTH a `Render:` AND a `Submit:` line under Pass criteria. Render-only PASS is not acceptable evidence — scenarios that can render but not submit should expect `BLOCKED_NEEDS_FIXTURE`, not `PASS`.
- [ ] **Pattern C (kit v5.8+):** scenarios where the agent might face PASS-vs-FAIL ambiguity (intentional behaviour vs. leak, tolerable slow async vs. regression) explicitly authorize `REQUIRES_INPUT` in the scenario's Verify or narrative section — so the agent emits the structured verdict instead of guessing.
- [ ] Every test has a unique, descriptive screenshot filename
- [ ] Tests that create state (e.g., create a record) note the identifiable name for cleanup
- [ ] **Miss Log section included with "No misses yet" placeholder**
- [ ] **REGISTRY.md updated with a new row for this agent**

---

## Adapt Mode

Use this section when invoked from `docs/prompts/testing-retro.md` Phase 3.2 to modify an existing agent rather than create a new one.

### Adapt Mode Inputs

You must receive (from the retro):
- Path to the existing test plan file (e.g., `docs/testing-agents/transcript-generation-tests.md`)
- The bug description that triggered the retro
- The **Miss Category** (A scope | B scenario | C assertion | D verification | E setup) from the retro's root-cause analysis
- The retro's proposed Action — the specific change shape it identified

### Adapt Mode Process

1. **Read the existing test plan file in full.** Read its `Miss Log` to confirm this isn't a duplicate of a prior miss.
2. **Apply the surgical edit** based on Miss Category:
   - **Category A (scope gap):** Add new scenarios for the missing tier/viewport/route. Update **Test Configuration** to declare the wider scope. Update the registry row's columns.
   - **Category B (scenario gap):** Add the missing scenario as a new T{N+1} block using the same scenario template (`### T{N}: {Name}` with Role, Priority, Type, Async, API endpoint, Steps, Pass criteria, Fail indicators, Verify). Place it adjacent to related scenarios in execution order.
   - **Category C (assertion gap):** Edit the relevant existing T{N} scenario's **Pass criteria** and **Fail indicators** to add the missing check (network, console, payload, state).
   - **Category D (verification gap):** Usually a base-agent issue (Tier-3). Flag for user approval before editing `.claude/agents/testing-agent.md`. If purely test-plan-level (e.g., missing explicit fail-indicators in this scenario), update the scenario.
   - **Category E (setup gap):** Edit **Credentials Setup** or **Test Configuration** to add the missing fixture/credential/setup step. If the setup is unreproducible, note it as a known limitation in the scenario and route to manual testing.
3. **Bump `Last Updated`** in the file header to today's date.
4. **Append a Miss Log entry** using the template in `testing-retro.md` Phase 3.3.
5. **Update the registry row** for this agent: bump `Last Updated` and set `Last Miss` to today. Update other columns (Routes / Roles / Viewports / Async) if scope changed.
6. **Do not regenerate sections you weren't asked to change.** Adapt Mode is surgical — minimal diff, maximum coverage gain.

### Adapt Mode Quality Checklist

- [ ] Existing file read in full, including Miss Log
- [ ] Edit applied matches the retro's Miss Category
- [ ] New/edited scenario uses concrete realistic inputs (the kind that revealed the bug)
- [ ] Pass criteria include Network + Console + UI checks (per base-agent rule 6)
- [ ] Fail indicators are explicit, not implied
- [ ] `Last Updated` bumped in file header
- [ ] Miss Log entry appended with all six fields populated
- [ ] Registry row updated (`Last Updated` + `Last Miss` + any scope columns)
- [ ] No unrelated edits to the file

---

## Example Invocation

During Phase 2 of the feature lifecycle, after writing the PRD and ADR:

```
Agent tool:
  prompt: |
    Read the PRD at docs/prd/{feature}.md and the ARD at
    docs/ard/{feature}.md.

    Follow the instructions in docs/prompts/create-testing-agent.md to
    generate a feature-specific testing agent file.

    Save the output to docs/testing-agents/{feature-name}-tests.md.
```

During Phase 4, the parent agent invokes:

```
Agent tool:
  prompt: |
    You are the testing sub-agent. Read your base instructions from
    .claude/agents/testing-agent.md, then execute the test plan in
    docs/testing-agents/{feature-name}-tests.md.

    The dev server is running on localhost:{PORT}.
    Report results in the format specified by the base agent.
```
