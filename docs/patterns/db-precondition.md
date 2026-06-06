# Pattern B — DB-Precondition Scenarios (opt-in)

> **Status:** Opt-in. Not shipped in kit vanilla because the runtime contract hardwires a project-specific DB-execute MCP tool name. Adopt this pattern if your project has scenarios that require specific pre-set DB state (token balance, feature flag, billing tier, fixture row) before the user-facing flow can be exercised meaningfully.
>
> **Companion patterns:** Pattern A (end-to-end submit assertion) and Pattern C (REQUIRES_INPUT verdict) are SHIPPED in kit vanilla as of v5.8 — see `docs/prompts/create-testing-agent.md` → "Required Scenario Patterns." Pattern B is the third member of the same family but stayed opt-in because of the DB-tool binding. Pattern A and C apply whether or not you adopt Pattern B.

---

## Why this is opt-in

The pattern itself is generic — every project with state-dependent scenarios benefits from declarative `requires_db_state` + `db_setup_sql` fields. What makes it opt-in is the **runtime contract**:

- The base testing-agent (`.claude/agents/testing-agent.md`) needs to know which MCP tool name to invoke to execute the SQL — and that tool name varies by project: `mcp__supabase__execute_sql`, `mcp__postgres__execute`, a project-shipped Bash wrapper, `psql` over SSH, etc.
- The SQL dialect in `db_setup_sql` varies too — Postgres-with-Supabase-RLS is one common shape, but SQLite, MySQL, BigQuery, and others are equally valid.
- The verdict when the tool isn't available (`MANUAL_BLOCKER`) is project-specific in spirit — a stricter project may prefer `BLOCKED_NEEDS_FIXTURE` instead.

Shipping a single vanilla wiring would force every adopting project into one DB-tool choice, so Pattern B documents the shape and lets each project bind the specifics.

---

## When Pattern B does NOT apply — auto-skip for non-SQL projects (v5.11)

**Pre-detect this before offering the opt-in.** Pattern B's whole runtime contract is "execute SQL via a DB-execute MCP tool." A project with **no SQL database and no DB-execute tool** — anything persisting to JSON files, localStorage, a document store, flat files, or in-memory state — **cannot** use Pattern B, and offering it (or asking the three-option opt-in question) just wastes a round-trip.

A migration's Pattern B step, and any agent considering adoption, should first detect applicability:

```
1. Is there a DB-execute MCP tool available / configured? (grep the project for mcp__*__execute_sql,
   mcp__postgres__*, a psql wrapper, etc.; check docs/context for a SQL DB in the stack.)
2. Does the project's storage layer use SQL at all? (Postgres/MySQL/SQLite/BigQuery → maybe;
   JSON files / localStorage / document store / flat files → NO.)
- If NO to both: Pattern B is structurally inapplicable. DEFAULT-SKIP it with a one-line note in
  docs/KIT_DEVIATIONS.md ("Pattern B N/A — <storage> persistence, no DB-execute tool") so future
  upgrades don't re-ask. Do NOT surface the three-option opt-in prompt.
```

This has now been the case for two independent JSON-storage downstream projects; the auto-skip saves them the opt-in round-trip every upgrade. Pattern A and Pattern C still apply to these projects — only Pattern B is gated on a SQL backend.

---

## What this pattern gives you

A scenario that requires pre-set DB state can declare it inline in the test plan, and the base testing-agent (if it has the configured MCP DB tool available) executes the setup before running the scenario and the teardown afterwards. When the tool isn't available, the agent emits a structured verdict so the parent agent knows to set up state out-of-band and re-dispatch — instead of silently SKIPing the scenario.

Bug class this prevents: a scenario like "Free-tier user sees the lock-out modal when they hit their 8-book lifetime cap" needs the user's token balance at exactly 8 before the test runs. Without Pattern B, the test plan either (a) hardcodes the setup as a manual step the human keeps forgetting, or (b) silently SKIPs because the precondition isn't there, and the lock-out UI regression escapes Phase 4.

---

## Adopting Pattern B in your project

Four files change. All edits are additive — Pattern B doesn't replace anything that's already there.

### 1. Pick your DB-execute MCP tool name

Decide which tool the testing-agent will use to execute `db_setup_sql` / `db_teardown_sql`. Common choices:

- **Supabase MCP:** `mcp__supabase__execute_sql` (Postgres + RLS-aware; respects RLS unless you wrap in `set local role`)
- **Generic Postgres MCP:** `mcp__postgres__execute` (whatever name your project's Postgres MCP exposes)
- **Project Bash wrapper:** `bash scripts/test-db.sh "$SQL"` (run as a Bash tool call rather than an MCP)
- **psql over SSH:** `ssh test-db psql -d test_db -c "$SQL"` (for projects with an isolated remote test DB)

Record the choice — you'll reference it in steps 2, 3, and 4.

### 2. Add scenario template fields to `docs/prompts/create-testing-agent.md`

In the **Step 3: Generate Test Scenarios** template, add three optional fields between `Async:` and `API endpoint:`:

```markdown
**Requires DB state:** {true | false} — if true, fill in db_setup_sql and db_teardown_sql below
**DB setup SQL** (only if Requires DB state: true):
```sql
-- SQL the testing-agent runs BEFORE the scenario starts.
-- Dialect: <your dialect — e.g. Postgres + Supabase RLS>
-- Must be idempotent (re-runnable without breaking).
```
**DB teardown SQL** (only if Requires DB state: true):
```sql
-- SQL the testing-agent runs AFTER the scenario completes (PASS, FAIL, or any other verdict).
-- Must restore the row(s) to their pre-test state.
```
```

### 3. Add a Quality Checklist item to `docs/prompts/create-testing-agent.md`

Append to the **Quality Checklist (Create Mode)** section:

```markdown
- [ ] **Pattern B (opt-in):** every scenario whose pre-conditions go beyond "user is logged in as role X" declares `Requires DB state: true` AND fills in `DB setup SQL` + `DB teardown SQL`. Scenarios that depend on specific DB state without declaring it will emit `MANUAL_BLOCKER` / `BLOCKED_NEEDS_FIXTURE` and not contribute to coverage.
```

### 4. Add the runtime contract to `.claude/agents/testing-agent.md`

In the base agent, append Pattern B as **the next free Rule number after the kit's current highest vanilla rule** — substitute `{DB_TOOL}` with your chosen tool name from step 1. **As of kit v5.10 the vanilla agent ships through Rule 23 (parallel-run isolation), so Pattern B is Rule 24.** (Pre-v5.10 the kit's highest was Rule 22 and Pattern B was Rule 23 — projects that adopted it then carry it as Rule 23; on upgrade to v5.10+, the numbered-list-collision rule in `upgrade-kit.md` Step 2.5 renumbers one of the two. Always append at the next free number rather than hardcoding.)

```markdown
24. **DB-precondition handling (Pattern B opt-in).** For any scenario where the test plan declares `Requires DB state: true`:
    a. Before the scenario's Pre-Flight, execute the scenario's `db_setup_sql` via `{DB_TOOL}`. If the tool is not available in this run's permissions or fails (non-2xx, exception), emit `MANUAL_BLOCKER` for the scenario with the missing-state details (which SQL, what error) and continue to the next scenario.
    b. Run the scenario normally — Pattern A and C still apply.
    c. After the scenario completes (any verdict), execute `db_teardown_sql` via `{DB_TOOL}`. If teardown fails, log it loudly in Reliability Notes — leaked test state poisons later scenarios.
    d. **Never execute `db_setup_sql` against the production DB.** The base agent runs against the configured dev/test DB only; if your project's `{DB_TOOL}` could reach prod, gate it on a `$TESTING_DB_URL`-style env var and refuse to proceed if unset.
```

Also extend the base agent's verdict legend (Step 8 in the Execution Protocol) and Reporting Format with a `MANUAL_BLOCKER` section, modelled on the `BLOCKED_NEEDS_FIXTURE` section but with the cause specifically being "DB setup tool unavailable" rather than "missing fixture."

### 5. Record the adoption in `docs/KIT_DEVIATIONS.md`

Under "Project extensions to kit files":

```markdown
- `.claude/agents/testing-agent.md:rule-24` — adopted Pattern B (DB preconditions) per `docs/patterns/db-precondition.md`. DB-execute tool: `{your tool name}`. Dialect: `{your DB dialect}`. (Rule 23 pre-v5.10; renumber on upgrade per the collision rule.)
- `docs/prompts/create-testing-agent.md:scenario-template:db-fields` — adopted Pattern B scenario template fields per `docs/patterns/db-precondition.md`.
```

This ensures future kit upgrades treat the additions as a recognized opt-in pattern, not as surprise hand-edits.

---

## Example: Supabase-with-RLS adoption

A project running on Supabase + Postgres + RLS sets `{DB_TOOL}` to `mcp__supabase__execute_sql` and writes scenarios like:

```markdown
### T14: Free user hits 8-book lifetime cap → lock-out modal appears

**Role/Tier:** Free
**Priority:** Critical
**Type:** Role-specific + Validation
**Async:** No
**Expected duration (whole scenario):** 8s
**Requires DB state:** true
**DB setup SQL:**
```sql
-- Set the test free user's lifetime book count to 8 (the cap)
UPDATE customers
   SET total_books_used = 8
 WHERE "authUserId" IN (
   SELECT id FROM auth.users WHERE email = '<TEST_FREE_USER_EMAIL>'
 );
```
**DB teardown SQL:**
```sql
-- Restore to 0 so other scenarios don't inherit cap-hit state
UPDATE customers
   SET total_books_used = 0
 WHERE "authUserId" IN (
   SELECT id FROM auth.users WHERE email = '<TEST_FREE_USER_EMAIL>'
 );
```
**API endpoint:** POST /api/books/new
**Steps:**
1. Navigate to /dashboard [~2s]
2. Click "New Book" @cta `[~1s]`
3. Observe lock-out modal `[~1s]`

**Pass criteria (ALL must be true):**
- **Render:** lock-out modal visible with copy "You've used all 8 books on your Free plan"
- **Submit:** modal's "Upgrade" CTA navigates to /pricing (network: GET /pricing returns 200)
- **Console:** no errors
- **Timing:** modal appears in < 2s after click

**Fail indicators (ANY means FAIL):**
- Modal does NOT appear
- "New Book" flow proceeds normally (would mean cap enforcement is broken)
- Modal appears but "Upgrade" CTA is broken
```

This scenario is meaningless without the DB setup — Pattern B makes the precondition declarative instead of out-of-band-and-forgotten.

---

## SQL dialect caveat

The SQL above is Postgres + Supabase-specific (references `auth.users`, uses double-quoted camelCase column names). If your project is on a different DB, rewrite accordingly:

- **SQLite:** no `auth.users`; identify the test user by `email` directly on your own users table. No double-quoted identifiers.
- **MySQL:** use backticks for identifiers; `UPDATE customers SET total_books_used = 8 WHERE auth_user_id IN (SELECT id FROM users WHERE email = '...')`
- **BigQuery / read-only warehouses:** Pattern B doesn't fit — write-time DB setup isn't supported. Use a fixture loader script instead.

Document your dialect in the deviation entry from step 5 so the next contributor knows what flavour they're writing against.
