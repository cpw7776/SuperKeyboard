# Testing Agent Retro

> **Purpose:** Self-improvement loop for the project's testing-agent library. When a fault is found during manual testing or production, this protocol identifies which agent should have caught it, root-causes the miss, and adapts the agent (or creates a new one) so the same class of bug cannot escape again.

> **Standalone — use anytime a bug is found that the testing agents should have caught.** Also invoked automatically from `bugfix.md` (Phase 2.4) and `feature-lifecycle.md` (Phase 5.1).

> **Philosophy:** Testing agents are **persistent project assets**, not ephemeral sub-agents. Each bug the agent library misses must produce an improvement. Otherwise the library plateaus at whatever quality it shipped with.

**The Miss:** [INSERT BUG DESCRIPTION + WHICH AGENT(S) RAN BEFORE IT WAS FOUND, OR "NONE" IF NO AGENT WAS APPLICABLE]

---

## When to Run This Protocol

Run whenever a fault meets BOTH of these conditions:

1. The fault is in code/behavior that the testing-agent library should have been able to verify (i.e., browser-observable, not a pure offline algorithm with no UI surface).
2. The fault escaped to manual testing or production despite Phase 4 automated testing having completed.

**Skip this protocol when:**
- The bug was caught during AI-driven test runs (Phase 4) — that's the system working as designed.
- The bug is in something testing agents fundamentally cannot observe (e.g., billing webhooks with no UI feedback, internal telemetry, infrequent scheduled jobs).
- The bug was already documented as out-of-scope for testing agents at design time (note this exemption in `docs/testing-agents/REGISTRY.md`).

---

## Phase 1: Identify the Responsible Agent

### 1.1 Look up which agent(s) ran before the miss

Read `docs/testing-agents/REGISTRY.md` and find all agents whose **scope** covers the route, component, role/tier, or async pathway involved in the bug. There are three possible findings:

- **One clear owner.** A single agent in the registry covers this scope. Adapt that agent.
- **Multiple partial owners.** Several agents touch the scope but none own it cleanly. Decide whether to extend one of them or split out a new agent.
- **No owner.** No agent in the registry covers this scope. Create a new agent and add it to the registry.

If the bug surfaced during a feature lifecycle (so a feature-specific test plan was just generated), the feature's own test plan is almost always the responsible agent.

### 1.2 Read the responsible agent's current state

Open the test plan file (e.g., `docs/testing-agents/{feature-name}-tests.md`) and read:
- The **Test Scenarios** section — what does this agent claim to verify?
- The **Miss Log** section (if it exists) — what has this agent missed before? Look for patterns.
- The **Test Configuration** — what scope is declared?

If the most recent test run report is available, read which scenarios passed and which were skipped. A `SKIP` adjacent to the bug's scope is itself a signal — the agent declared a gap and the gap wasn't backfilled.

---

## Phase 2: Root-Cause the Miss

Categorize the miss into **exactly one** of these buckets. Each bucket has a different fix shape, so the category drives the action.

### Category A — Scope gap

The agent's scope didn't include the area where the bug lives. E.g., the agent tests the Free and Pro tiers but the bug is in the Enterprise tier; or the agent tests desktop but the bug is mobile-only; or the agent tests the happy path but the bug is in the error-recovery flow.

**Fix shape:** Widen the scope. Add new scenarios for the missing tier/viewport/path. Update the agent's **Test Configuration** to declare the wider scope. Update REGISTRY.md to reflect it.

### Category B — Scenario gap

The agent's scope was right, but a specific scenario was missing. E.g., the agent tests "submit valid form" but the bug only appears with "submit, then immediately re-submit" (race condition); or the agent tests "upload a small file" but the bug is for files over 5 MB.

**Fix shape:** Add the missing scenario. Use concrete realistic inputs (the kind that revealed the bug). Make sure pass/fail criteria capture the exact failure mode.

### Category C — Assertion gap

The agent ran the right scenario but checked the wrong things. E.g., the agent verified the success toast appeared but didn't check that the DB write actually happened; or it checked `200` status code but didn't validate the response payload shape.

**Fix shape:** Strengthen the pass criteria of the existing scenario. The base agent's "Verify, Don't Assume" sequence has Network + Console + UI checks — if one of these was missing from the test plan, add it. If the bug shows up in a layer the test plan doesn't currently check (e.g., it needs a DB query, not just a UI snapshot), document that gap and either add the check or escalate it to the Miss Log as a known limitation.

### Category D — Verification gap

The agent saw the failure indicator and incorrectly marked PASS. E.g., a 4xx response was visible in the network log but the agent didn't read it; or a console error was present but the agent only looked at the UI.

**Fix shape:** This is a base-agent issue more often than a test-plan issue. Check whether the test plan's pass criteria explicitly listed Network + Console checks. If not, add them. If the base agent (`.claude/agents/testing-agent.md`) failed to run its mandatory verification sequence, that's a Tier-3 propagation (see Phase 4).

### Category E — Setup gap

The agent couldn't reach the state where the bug appears. E.g., couldn't log in as the affected tier; couldn't seed the required data; couldn't reproduce the timing window.

**Fix shape:** Fix the credentials / fixture / setup. Often this means adding a new entry to `.env.local` or seeding a specific record. If the setup is fundamentally not reproducible in browser automation, document it in the Miss Log as a known limitation and route this class of bug to manual testing forever.

---

## Phase 3: Apply the Fix

### 3.1 Decide: adapt existing agent vs create new

- **Adapt** when Category A/B/C/E applies AND the existing agent is the correct owner of this scope.
- **Create new** when:
  - No existing agent owns this scope (Phase 1.1 found "No owner").
  - The existing agent's scope would be stretched so wide it becomes unwieldy.
  - The bug is in a different feature/concern from the agents that ran.

### 3.2 Apply the change

**For adaptation:**

1. Edit the test plan file directly. Add the new scenario(s), strengthen assertions, widen scope.
2. Bump the file's `last_updated` date in the header.
3. Update `docs/testing-agents/REGISTRY.md` if scope changed.

**For new agent:**

1. Invoke `docs/prompts/create-testing-agent.md` in **Retro-Create Mode** (a variant of Create Mode where the input is a bug + category, not a PRD + ADR). Pass:
   - The bug description
   - The Miss Category from Phase 2
   - The routes / components / roles / async pathways affected
   - Any relevant PRD/ADR (may be `None` for cross-feature concerns)
2. Save the new agent to `docs/testing-agents/{descriptive-name}-tests.md` — the name should describe the SCOPE the agent owns (e.g., `enterprise-tier-billing-tests.md`), not the bug that motivated it.
3. Add an entry to `docs/testing-agents/REGISTRY.md`. Note in the new agent's header: `Created from retro: {bug summary} on {date}` so the origin is traceable.

### 3.3 Append to the Miss Log

Every adapted or newly-created agent gets an entry appended to its **Miss Log** section. The entry must include:

```markdown
### Miss — {YYYY-MM-DD} — {short bug summary}

- **Found in:** {manual testing | production | feature lifecycle Phase 5}
- **Category:** {A scope | B scenario | C assertion | D verification | E setup}
- **Why this agent missed it:** {1-3 sentences explaining the gap}
- **Action taken:** {what changed — new scenario T{N}, strengthened pass criteria on T{M}, widened scope to include role X, etc.}
- **Tier propagation:** {feature-only | project-pattern | skill-universal — see Phase 4}
- **Verification:** {how we confirmed the updated agent now catches this — re-ran agent / wrote a new scenario that fails against pre-fix code / deferred}
```

The Miss Log lives at the bottom of the test plan file. The base agent reads it on every run as part of pre-flight, so historical misses inform future runs.

---

## Phase 4: Decide Tier Propagation

A miss can teach a lesson at three different levels. Choose the **highest level** that applies — higher levels imply lower levels (a skill-universal lesson updates the project doc AND the feature agent).

### Tier 1 — Feature-only (default)

The lesson is specific to this agent's scope. The Miss Log entry on this agent is sufficient. No further action.

**Examples:**
- "Forgot to test the Enterprise tier on this specific dashboard."
- "Need to add a test for files larger than 5MB on this specific upload form."

### Tier 2 — Project pattern

The lesson applies across multiple features in this project, but is project-specific. Update (or create) `docs/context/Testing_Patterns.md` with the pattern.

**Examples:**
- "In this codebase, every form submission has both client-side and server-side validation — agents must always test the server-side path with a request that bypasses the client validator."
- "This app uses optimistic updates everywhere — agents must wait for the actual API response before declaring success, not the UI flicker."
- "Auth tokens in this project expire after 30 minutes — long-running test runs need a re-auth step."

When `docs/context/Testing_Patterns.md` is updated, the next time `create-testing-agent.md` runs, it must read this file and incorporate the patterns into every new test plan.

### Tier 3 — Skill-universal

The lesson is universal across all projects using this kit. Propose a change to the **base agent** (`.claude/agents/testing-agent.md`) or to the **test plan generator** (`docs/prompts/create-testing-agent.md`).

**Examples:**
- "The mandatory verification sequence should include reading the response payload, not just the status code."
- "The pre-flight should kill orphaned Chrome processes from prior runs across all projects."

Tier 3 changes are higher-risk because they affect every future feature in every project. Don't apply Tier 3 changes silently — flag them in the Miss Log and present the proposed change to the user for explicit approval before editing the kit files.

---

## Phase 5: Verification (Optional but Recommended)

Close the loop by proving the updated agent now catches the bug it missed.

### Option A — Re-run the agent on pre-fix code

Best when:
- The code fix and the agent fix can be staged separately
- A worktree or revert is cheap

Steps:
1. Stash or revert the code fix temporarily.
2. Invoke the updated testing-agent against the unfixed code.
3. Confirm the new/updated scenario FAILS.
4. Restore the code fix.
5. Re-run; confirm PASS.

Record the result in the Miss Log under **Verification**.

### Option B — Write the scenario as a failing assertion against captured evidence

When re-running is impractical (e.g., the bug requires a hard-to-reproduce timing window or specific production data), at least document:
- The exact selector / network call / console message that would have flipped the verdict.
- Pseudocode for the assertion the updated agent now contains.

Mark this in the Miss Log Verification field as `documented — not live-tested`. These should age out over time; a Miss Log with many `documented — not live-tested` entries is a signal that this agent's coverage is theoretical, not real.

### Option C — Defer

Acceptable for low-severity misses where verification cost outweighs the value. Mark Verification as `deferred — {reason}`. Tier-2 and Tier-3 propagations should not defer verification — they affect too much surface area.

---

## ⏸️ STOP: Present Retro Summary

After Phases 1–5, present a structured summary before declaring the retro complete:

```markdown
## Testing Agent Retro: {bug summary}

**Responsible Agent:** {path/to/agent.md} (or "no owner — created new agent")
**Miss Category:** {A scope | B scenario | C assertion | D verification | E setup}
**Tier:** {1 feature-only | 2 project pattern | 3 skill universal}

### What the agent missed
{1-2 sentences}

### Change applied
{specific edits — list the new/changed scenarios, assertions, scope, etc.}

### Files changed
- {path/to/test-plan.md} — {what changed}
- {docs/testing-agents/REGISTRY.md if scope/agent count changed}
- {docs/context/Testing_Patterns.md if Tier 2}
- {kit files if Tier 3 — requires user approval first}

### Verification
{Option A re-run result | Option B documented assertion | Option C deferred with reason}

### Miss Log entry appended to: {path}
```

Wait for user acknowledgement before closing the retro. For Tier-3 propagations, wait for explicit user approval before editing kit files.

---

## Integration With Other Prompts

### From `bugfix.md` (auto-invoked at Phase 2.4)

Bugfix's `Phase 2.4 — Testing Agent Retro` references this protocol. When a bugfix is for a bug found post-Phase-4, the bugfix protocol runs Phases 1–4 of this retro inline before the final commit. Phase 5 verification can be folded into the same commit when adaptation is small.

### From `feature-lifecycle.md` (auto-invoked at Phase 5.1)

Phase 5.1's Test Autopsy traditionally adapts the unit test suite. The v3 update extends it to also adapt the testing-agent library via this retro protocol. Every bug surfaced during manual testing produces both: (a) a new/rewritten unit test (closes the suite gap), and (b) a retro entry on the testing agent (closes the browser-test gap).

### From manual invocation

Paste this file as a prompt, fill in `[INSERT BUG DESCRIPTION + WHICH AGENT(S) RAN BEFORE IT WAS FOUND]` at the top, and run.

---

## Quality Checklist

Before declaring the retro complete:

- [ ] Responsible agent identified (or "no owner — created new agent")
- [ ] Miss category chosen from the five buckets and justified in writing
- [ ] Concrete change applied to the test plan file (or new file created)
- [ ] Miss Log entry appended with all six fields populated
- [ ] REGISTRY.md updated if scope changed or a new agent was created
- [ ] Tier propagation decision made and acted on (Tier 2 → patterns doc, Tier 3 → kit change with user approval)
- [ ] Verification done (A re-run, B documented, or C deferred-with-reason)
- [ ] Retro summary block presented to the user

If any item is unchecked, the retro is not complete — fix the gap, don't move on.
