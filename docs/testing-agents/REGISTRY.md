# Testing Agent Registry

> **Purpose:** Index of all persistent testing agents in this project. Read this BEFORE creating a new testing agent — if an applicable agent already exists, adapt it instead of creating a new one. Read this DURING a testing retro to find which agent owned the missed scope.

> **Maintained by:** Auto-updated by `docs/prompts/create-testing-agent.md` (on create) and `docs/prompts/testing-retro.md` (on retro). Update manually if you rename, delete, or hand-edit a test plan file.

> **One row per agent.** A "testing agent" here means a single test plan file in `docs/testing-agents/`, which the base agent (`.claude/agents/testing-agent.md`) executes.

---

## How to Use This Registry

### Before creating a new agent (find-or-create)

1. Identify the scope of what you need to test (routes, components, roles/tiers, async pathways).
2. Scan the **Scope** column below for overlap.
3. If an existing agent covers the scope cleanly → **adapt** it (run `create-testing-agent.md` in adapt mode, or edit the file directly).
4. If multiple agents partially cover the scope → decide whether to extend one or split out a new one. Note the decision in the new agent's header (`Extends:` or `Split from:`).
5. If no overlap → **create** a new agent and add a row here.

### During a testing retro

1. Find the row whose **Scope** matches where the bug was found.
2. If one row matches, that's the responsible agent.
3. If multiple match, check which one ran most recently (see the test plan file's last test report) — that's the agent that should have caught it.
4. If none match, this is a "no owner" miss — create a new agent and add a row.

---

## Registry

> Native Android project — "Routes/Viewports" columns are repurposed for native surfaces (Gradle suites + device).

| Agent File | Scope / Purpose | Routes Covered | Roles / Tiers | Viewports | Async | Last Updated | Last Miss |
|-----------|-----------------|----------------|---------------|-----------|-------|--------------|-----------|
| `test-foundation-tests.md` | Verify the JVM + instrumented suites run green and the app build isn't regressed | Gradle `:app:testDebugUnitTest`, `connectedDebugAndroidTest`, `assembleDebug` | none (offline) | AVD Medium_Phone_API_36.1 | n | 2026-06-09 | — |
| `ai-action-engine-tests.md` | Verify the AI toolbar actions, default-OFF egress guarantee, encrypted key, and preview-then-apply behavior | Gradle `:app:testDebugUnitTest`, `assembleDebug` + on-device AI settings/actions | none (user supplies endpoint+key fixture) | physical phone (APK→Syncthing) | **y** (network) | 2026-06-09 | — |

**Column definitions:**

- **Agent File** — filename in `docs/testing-agents/`. Click-through to read the full test plan.
- **Scope / Purpose** — one-sentence description of what this agent verifies. The "Purpose" of the agent is the load-bearing field for find-or-create matching.
- **Routes Covered** — exact route patterns this agent's scenarios visit. New routes = new agent or extended scope.
- **Roles / Tiers** — auth tiers/roles this agent logs in as. Missing a tier = scope gap when a bug appears in that tier.
- **Viewports** — `desktop`, `mobile`, `tablet`, or combinations. Missing viewports often hide responsive bugs.
- **Async** — does this agent test async operations (AI generation, uploads, webhooks)? Async-shaped bugs are common gaps.
- **Last Updated** — date of the last edit to the test plan file. Stale agents drift from the code.
- **Last Miss** — date of the most recent Miss Log entry. High frequency = the agent is undercovering its scope; consider splitting or strengthening.

---

## Out-of-Scope Exemptions

> Things testing agents are explicitly NOT expected to catch. When a bug surfaces in one of these areas, do NOT run `testing-retro.md` — the gap is by design.

| Area | Reason | Caught By Instead |
|------|--------|-------------------|
| {e.g., Stripe billing webhooks} | {no UI feedback path; browser automation can't observe} | {monitoring, alerts, integration tests} |
| {e.g., Background cron jobs} | {invisible to browser; runs on schedule} | {server logs, cron monitoring} |

If a bug appears in an area listed here and testing-agent coverage would have been valuable, the exemption may be wrong — move the area out of this table and run a retro.

---

## Registry Maintenance Rules

1. **No silent renames.** If you rename a test plan file, update this registry in the same commit.
2. **No silent deletes.** If you delete a test plan, document why in the commit message and remove the row.
3. **Adapt > duplicate.** Two agents with overlapping scope are a smell. If the registry shows overlap, plan a consolidation.
4. **Date columns matter.** They drive the Phase 5 retrospective ("which agents have we updated this quarter?") and surface stale coverage.
5. **The registry is part of the project's source of truth.** Treat edits with the same care as edits to `docs/context/Context_Index_File.md`.
