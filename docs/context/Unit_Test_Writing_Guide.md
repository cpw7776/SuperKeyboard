---
name: Unit Test Writing Guide
purpose: Curated, evolving guidance for the agent writing TDD unit and integration tests. Read BEFORE writing any failing test in Phase 3.3 of feature-lifecycle.md, Phase 2.1 of bugfix.md, and any time a test is being authored.
read_when: Before writing a failing test (TDD red step), before writing any new unit/integration test, when reviewing a test in code review for honesty
updated_by: docs/prompts/test-suite-retro.md (only — do not hand-edit lessons; raw events live in docs/test-suite-misses.md)
last_updated: 2026-05-14
---

# Unit Test Writing Guide

> **Purpose:** This is the canonical place the test-writing agent looks before authoring any unit or integration test. It encodes both **universal best practices** and **project-specific lessons** accumulated from real bugs that escaped the test suite.
>
> **Mental model:** When a bug slips past unit/integration tests, that's not just a test-needs-writing problem — it's evidence the suite has a *pattern* of blindness. The Test Suite Retro (`docs/prompts/test-suite-retro.md`) decides whether a miss is one-off or generalisable. If generalisable, the lesson lands here so the next test author doesn't repeat it.
>
> **Discipline:** Lessons added under `## Project Lessons` are appended via retros, not by hand. Raw event log (every miss, generalisable or not) lives in `docs/test-suite-misses.md`. The guide stays curated and scannable; the log stays exhaustive.

---

## How to use this guide

**Phase 3.3 (Feature Lifecycle) and Phase 2.1 (Bugfix) — RED step of every TDD cycle:**

1. Read `## Universal Anti-Patterns` (below) — these are always-on rules.
2. Read `## Project Lessons` — these are this project's accumulated scar tissue.
3. Scan `## Testing Patterns by Layer` for the layer you're testing (unit / integration / contract).
4. Write the failing test.
5. **Run it. Confirm it fails for the right reason** (asserting the missing behaviour, not a typo or import error).
6. Implement the code. Confirm the test now passes.

If a lesson here would have caught the bug you're working on, reference its section in the test's comment so future readers know which scar this prevents (one-liner only — don't write essays in tests).

**Do NOT hand-edit `## Project Lessons` directly.** That section is the output of `test-suite-retro.md`. If you spot a pattern that should be a lesson, run the retro to add it through the right channel.

---

## Universal Anti-Patterns (always-on)

These are the patterns that produce **green tests over broken code**. Memorise them.

### A1 — Asserting implementation instead of behaviour

**Symptom:** Test breaks every time the implementation is refactored, even when user-visible behaviour didn't change.

**Why it lets bugs through:** It locks in the current implementation as "correct" — including its bugs. A test that says "this function calls `db.users.findOne()` once" is checking plumbing, not outcome.

**Fix shape:** Assert what the user / caller observes. For a UI test, assert what's rendered. For a service test, assert the return value or side effect on the system of record.

```
// BAD — asserts implementation
expect(mockDb.findOne).toHaveBeenCalledTimes(1)

// GOOD — asserts behaviour
expect(response.body.user.email).toBe('alice@example.com')
```

### A2 — Over-mocking the layer where the bug lives

**Symptom:** Unit tests are 100% green, but integration testing reveals the very interaction the unit tests mocked away.

**Why it lets bugs through:** If you mock the database client, you can't catch bugs in your SQL. If you mock the API client, you can't catch bugs in the URL you build. The mock confirms your **expectations**, not the system's **behaviour**.

**Fix shape:** Mock at the **system boundary** (third-party HTTP, payment processor, email sender, clock), not at the boundary inside your own code. For your own DB / your own routes / your own utilities, run the real thing in an integration test.

### A3 — Happy-path-only coverage

**Symptom:** Every test reads "valid input → expected output." Error paths, empty results, boundary values, and concurrent edges have no coverage.

**Why it lets bugs through:** Production users find the empty-state, the 0, the negative number, the unicode name, the network blip, the second click before the first finished. The test suite doesn't.

**Fix shape:** For every behaviour, write at least one test for each of: valid input, invalid input, empty / null / undefined input, boundary value (0, 1, max), error from a downstream dependency, and (where relevant) repeated / concurrent invocation.

### A4 — Asserting the mock, not the code

**Symptom:** Test sets up `mock.thing.returns(42)`, then asserts `result === 42`. The test passes whether the implementation calls `thing` or hard-codes `42` or short-circuits entirely.

**Why it lets bugs through:** The mock's return value flows straight to the assertion. The actual code path being tested might be skipped. This is the cousin of A1.

**Fix shape:** Assert something the mock could not have decided alone — a transformation, a derived value, a side effect at a different layer.

### A5 — Test data doesn't reflect real-world shape

**Symptom:** Tests use `{ id: 1, name: 'x' }` everywhere. Production has multi-byte names, empty strings, nulls, missing optional fields, hyphenated IDs, and rows with all-nulls except the PK.

**Why it lets bugs through:** Validation logic, serialisation, and edge handling never see the inputs they break on.

**Fix shape:** Build a small fixture library of realistic shapes (unicode, long, empty, partial, malformed) and reuse them. For DB-backed tests, seed data that mirrors a real row, not the minimum to compile.

### A6 — Integration gaps between units that each pass in isolation

**Symptom:** `getUser()` test passes. `formatProfile()` test passes. Putting them together breaks because `getUser()` returns `null` for missing rows and `formatProfile()` doesn't handle null.

**Why it lets bugs through:** Each unit's contract is verified against its own assumptions, which don't line up at the seam.

**Fix shape:** Write integration tests at every meaningful seam — at minimum, route → service → DB and component → hook → API. Don't rely on unit tests to find composition bugs.

### A7 — Async / timing tests that pass because they don't actually wait

**Symptom:** Test calls an async function but forgets to `await` it (or in a framework that doesn't propagate unhandled rejections, the assertion never runs). Suite is green; the underlying behaviour is unverified.

**Why it lets bugs through:** The test never observes the failure because the assertion is bypassed.

**Fix shape:** Every async test must either `await` every promise or return the promise to the test runner. After writing the test, deliberately break the production code temporarily and confirm the test goes RED. If it stays green, the test isn't actually testing anything.

### A8 — Skipped or `.todo` tests that age into permanent gaps

**Symptom:** `it.skip(...)` was added "for now" and is still there six months later.

**Why it lets bugs through:** The scenario looked covered when someone scanned the test file. It wasn't.

**Fix shape:** Every skip needs a date and a reason in `docs/known-test-skips.md` (per Phase 4.2 of `feature-lifecycle.md`). Skips that don't make it into that file are deleted, not skipped.

### A9 — Snapshot tests as a substitute for assertions

**Symptom:** A test does `expect(result).toMatchSnapshot()` and nothing else. When the bug appears, the snapshot is updated and the test stays green.

**Why it lets bugs through:** Snapshots assert *equality with the last accepted output*. If the bug was present when the snapshot was first captured, the snapshot encodes the bug as correct.

**Fix shape:** Use snapshots only for things that are large, stable, and where a structural diff is genuinely the right signal (e.g., a rendered HTML tree you've manually inspected once). Pair every snapshot with at least one targeted assertion on a value you actively care about.

### A10 — Test name lies about what's being tested

**Symptom:** `it('rejects invalid email')` is followed by code that submits a valid email and asserts a 200.

**Why it lets bugs through:** Reviewers trust the name. The body doesn't get read carefully. The named scenario is never actually tested.

**Fix shape:** Test name and test body must describe the same scenario. If you change one, change the other. In code review, read the body and confirm it matches the name before approving.

### A11 — Coverage % as a substitute for thinking

**Symptom:** "We have 92% coverage" but a critical branch (the error path on the payment API) has no test.

**Why it lets bugs through:** Coverage tools count lines executed, not behaviours verified. A single happy-path test through a function with three branches counts the whole function as covered.

**Fix shape:** Use coverage to find *unexecuted* code (the gaps coverage flags are real). Don't use it as a quality signal for executed code. Quality comes from the assertions, not the line count.

---

## Testing Patterns by Layer

### Unit tests (a single function / class / hook in isolation)

- **Mock only:** the clock, randomness, network, filesystem, third-party SDKs. Nothing else.
- **Run the real thing for:** your own utility functions, your own data transforms, your own pure logic.
- **Assertion shape:** input → output, or input → side effect captured in a stub at the system boundary.
- **Don't:** mock your own modules to "isolate" the function under test. If you have to mock your own code, your seams are wrong.

### Integration tests (multiple units composed, real seams)

- **Real DB** (test database, transactional rollback per test): yes, always, for any test that touches data.
- **Real internal HTTP routing** (e.g., supertest, Next.js API handler called directly): yes.
- **Real auth middleware** (with a test user fixture): yes.
- **Mock only:** outbound third-party calls (Stripe, SendGrid, S3) and the clock.
- **Assertion shape:** end-to-end from input (HTTP request, function call at the entry point) to observable state change (DB row, response body, emitted event).

### Contract tests (this codebase ↔ an external system)

- For any third-party API call: capture a real response once (fixture file), then build the contract test around that fixture. If the third-party shape changes, the contract test breaks loudly instead of silently.
- For internal APIs your frontend consumes: a contract test that the route returns the expected shape. Frontend and backend both reference it.

### Component / UI tests (where unit tests are still appropriate)

- Browser-level interaction tests live in `docs/testing-agents/` and are run by the browser testing-agent — **not** here. Don't duplicate them.
- Here: assert what the component renders given props/state, and that the right callback fires on the right interaction. That's it. Don't mock React, don't shallow-render, don't assert internal hook calls.

---

## Project Lessons

> **This section is appended by `docs/prompts/test-suite-retro.md` when a miss generalises beyond a single bug. Do not hand-edit.**
>
> Each lesson is one of:
> - **Anti-pattern lesson** — a new specific anti-pattern observed in this codebase that universal rules don't quite cover
> - **Pattern lesson** — a positive pattern that closed a class of bug (e.g., "always test the server-side validator with a request that bypasses the client validator")
> - **Boundary lesson** — a discovered seam this codebase has where integration tests are mandatory
>
> Each entry must include: date, miss reference (link to `test-suite-misses.md` entry), the rule, an example, and one short sentence on the bug that motivated it.

_No lessons yet. The first entries will land here when the first Tier-2 retro runs._

---

## Out-of-scope for this guide

This guide covers **unit and integration tests written by the implementing agent during TDD**. It does NOT cover:

- **Browser-observable behaviour** — that's `docs/testing-agents/` and `docs/context/Testing_Patterns.md`. The two systems are parallel and don't overlap.
- **Manual test plans** — those live in PRDs under "Human Testing Plan".
- **Load / performance / soak testing** — out of scope for this kit's test discipline as written.

If a lesson is genuinely cross-cutting (applies to both browser tests and unit tests), it should land in both `Testing_Patterns.md` and this file, with a cross-reference. The retro decides.
