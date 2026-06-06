---
name: Implementation Patterns
purpose: Curated, evolving guidance for the agent WRITING FEATURE CODE. The code-side mirror of the Unit Test Writing Guide. Read BEFORE writing implementation code in Phase 3 of feature-lifecycle.md, the same way the TDD red step reads the test guide.
read_when: Before writing the implementation for a feature/task (the "implement" step of TDD), before any non-trivial code change, when reviewing code for recurring fault-classes
updated_by: the three retro-running flows' Gap C step — feature-lifecycle.md Phase 5.1, bugfix.md Phase 2.4, reconcile-change.md Phase 2.1 (do not hand-edit lessons; each lesson traces to the fix/commit it was autopsied from)
last_updated: 2026-06-06
---

# Implementation Patterns

> **Purpose:** This is the canonical place the implementing agent looks before writing feature code. It is the **code-side mirror of `Unit_Test_Writing_Guide.md`**: that guide encodes how to write tests that catch bugs; this one encodes how to write code that doesn't grow them. It holds both **universal anti-patterns** and **project-specific lessons** accumulated from real faults that shipped.
>
> **Why this exists:** The test ecosystem has two self-improving, read-before-you-work guides (`Unit_Test_Writing_Guide.md` for unit/integration, `Testing_Patterns.md` for browser tests). The *code* ecosystem had none — implementation lessons scattered into ADRs, per-feature plan docs, and test-miss side-notes, and were never read cross-feature before the next implementation. This guide closes that gap: it is **Gap C** of the Phase 5.1 retrospective sweep.
>
> **Mental model:** When a bug ships, the retro asks three things — did a *test* miss it (Gap A), did the *testing agent* miss it (Gap B), and was the *code itself* an instance of a recurring fault-class or project gotcha (Gap C)? When the third is true, the durable lesson lands here so the next implementer doesn't write the same fault.
>
> **Discipline:** Lessons under `## Project Lessons` are appended via the Phase 5.1 Gap C autopsy, **not by hand**. Each lesson traces to the `fix(` commit that motivated it. The guide stays curated and scannable.

---

## How to use this guide

**Phase 3 (Feature Lifecycle) — before writing implementation code:**

1. Read `## Universal Anti-Patterns` (below) — always-on rules for any codebase.
2. Read `## Project Lessons` — this project's accumulated scar tissue, one entry per shipped-fault-class.
3. Write the implementation, steering clear of both.
4. If a lesson here would have prevented the code you're about to write, reference its ID in a one-line comment so the next reader knows which scar it avoids (one-liner only — no essays in code).

Read it **once before the first implementation step of a feature**, and again before any change that crosses into a layer/area a `## Project Lessons` entry calls out. The guide is short by design — re-reading is cheap.

**Do NOT hand-edit `## Project Lessons` directly.** That section is the output of the Phase 5.1 Gap C autopsy. If you spot a pattern that should be a lesson mid-implementation, note it for the retro — don't append it here by hand.

---

## Universal Anti-Patterns (always-on)

These are fault-classes that produce **code that works in the demo and breaks in production**. They are deliberately stack-agnostic — they apply to a web app, a CLI, a library, a data pipeline, or a mobile app alike.

### I1 — Swallowing errors silently

**Symptom:** An empty `catch`, an ignored return/error value, a `try` that logs nothing and continues. The failure becomes invisible until something far downstream is already corrupt.

**Fix shape:** Handle the error, propagate it, or fail loudly. If you genuinely intend to ignore it, say so in a comment with the reason. "Can't happen" is not a reason — make it can't-happen by construction or assert it.

### I2 — Trusting input at a boundary without validating it

**Symptom:** Data from outside the unit (user input, an external API, a file, another service, a queue message) flows into core logic unchecked. Shape, type, range, and presence are all assumed.

**Fix shape:** Validate and normalize at the boundary, once, then treat the value as trusted inside. Reject or coerce invalid input at the edge, not three layers deep where the blast radius is larger.

### I3 — Duplicated logic that will drift

**Symptom:** The same rule, calculation, or constant is copy-pasted across call sites. A later fix lands in one copy and the others silently keep the bug.

**Fix shape:** Extract the single source of truth (a function, a constant, a shared module) and call it from every site. DRY is not about line count — it's about having one place to fix a rule.

### I4 — Shared mutable state without clear ownership

**Symptom:** Two code paths read and write the same variable/object/store and the outcome depends on order. Manifests as "works on my machine," intermittent failures, or corruption under concurrency.

**Fix shape:** Give the state one owner, make it immutable where you can, or guard the critical section explicitly. Prefer passing data over sharing it.

### I5 — Stringly-typed / primitive-obsessed values across boundaries

**Symptom:** Meaningful concepts (an id, a money amount, a status, a unit) are passed as bare strings/numbers, so invalid states are representable and impossible combinations compile fine.

**Fix shape:** Model the concept so invalid states can't be constructed — an enum/union for a fixed set, a typed wrapper for an id, an explicit unit on a quantity. Make the type carry the invariant.

### I6 — No idempotency on an operation that can be retried

**Symptom:** A create/charge/send/enqueue runs twice (a retry, a double-click, an at-least-once delivery) and produces two effects.

**Fix shape:** Make the operation idempotent — a natural key, an idempotency token, an upsert, or a "already done?" check before the effect. Assume every side-effecting call can be delivered more than once.

### I7 — Unbounded resource use

**Symptom:** Loads "all" rows/items/files into memory, a loop with no limit, a subscription/handle/listener that's opened but never closed, a cache that only grows. Fine with test-sized data, fatal at real scale.

**Fix shape:** Bound it — paginate, stream, cap, and pair every acquire with a release. Ask "what happens at 100× the data I'm testing with?"

### I8 — Implicit time / order / locale / environment assumptions

**Symptom:** Code assumes a timezone, a clock that only moves forward, a stable iteration order, a locale, a path separator, or an env var that "is always set." Breaks in another region, on another machine, or after midnight.

**Fix shape:** Make the assumption explicit and injected — pass the clock/zone/locale, sort when you depend on order, read config with a validated default. Don't bake the environment into the logic.

### I9 — Partial failure left in an inconsistent state

**Symptom:** A multi-step operation fails halfway — step 1 committed, step 2 threw — and there's no rollback, so the system is left in a state that should be impossible.

**Fix shape:** Define the consistency boundary: a transaction, a saga with compensation, or a reorder so the irreversible step is last. Decide explicitly what "half-done" means and prevent it.

### I10 — Fixing the symptom at the wrong layer

**Symptom:** A bug is patched where it surfaced (a guard in the UI, a special-case in the caller) while the actual fault stays in the layer below — so the same fault re-emerges through a different path. This is the most common source of a *recurring bug-class*.

**Fix shape:** Fix the fault at its origin. If you must patch at the surface for now, leave a pointer to the real cause and the follow-up. (A recurring symptom across features is exactly what a Gap C `## Project Lessons` entry should capture.)

### I11 — Breaking a shared contract without a migration path

**Symptom:** A function signature, schema, event shape, config key, or public API used by other code/features changes incompatibly, and the callers aren't updated in the same change — or there's no transition window for data/clients already in flight.

**Fix shape:** Change shared contracts additively where possible; when you can't, update every caller in the same change and provide a migration for in-flight data/clients. Treat a shared boundary as a promise.

---

## Project Lessons

> **This section is appended by the Gap C autopsy in any of the three retro-running flows — `feature-lifecycle.md` Phase 5.1, `bugfix.md` Phase 2.4, or `reconcile-change.md` Phase 2.1 — when a shipped fault reflects a durable code lesson (a recurring bug-class, a project coding gotcha, or an architectural anti-pattern this codebase is prone to). Do not hand-edit.**
>
> Each lesson is one of:
> - **Bug-class lesson** — a fault-class this codebase keeps producing (the code-side equivalent of a recurring test miss).
> - **Gotcha lesson** — a non-obvious project-specific trap (a framework quirk, a data-shape surprise, a deploy-environment difference) that bit an implementation.
> - **Architecture lesson** — a structural anti-pattern (a boundary that keeps leaking, a coupling that keeps biting) that the next feature in this area should design around.
>
> Each entry must include: date, the `fix(` commit (or bug ref) that motivated it, the rule, a short example, and one sentence on the fault that shipped.

_No lessons yet. The first entries will land here when the first Phase 5.1 Gap C autopsy promotes a fault to a project lesson._

---

## Out-of-scope for this guide

This guide covers **implementation code written during a feature**. It does NOT cover:

- **Test honesty** — that's `Unit_Test_Writing_Guide.md` (Gap A). A fault that was a *test* blindness, not a *code* fault, belongs there.
- **Browser-observable behaviour** — that's `docs/testing-agents/` and `docs/context/Testing_Patterns.md` (Gap B).
- **One-off mistakes** — a typo or a genuinely non-recurring slip is a Tier-1 finding: named in the retro, not promoted to a lesson here. This guide is for fault-*classes*, not every bug.

If a lesson is genuinely cross-cutting (it's both a code anti-pattern AND a test blindness), it can land in both this file and `Unit_Test_Writing_Guide.md` with a cross-reference. The Phase 5.1 sweep decides.
