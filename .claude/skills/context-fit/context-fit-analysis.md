# context-fit-analysis — the Gap D analysis procedure

> **What this is:** the judgment half of context-fit. An agent follows it to turn a footprint + mechanical candidates + the project's existing context into **Necessity-Gauntlet-scored proposal cards** — read-only, human-approved, default **No**. Ships with the kit as part of the `context-fit` skill (kit v5.19+); invoked by `feature-lifecycle.md` Phase 5.1 **Gap D** at epic close, by the lightweight Gap D checks in `bugfix.md` / `reconcile-change.md`, and ad-hoc via the skill.

> **Discipline (load-bearing):** propose, never apply. Default reject. **Raw reads ≠ a gap** — the signal is *concentration* (re-reads), *cohesion* (co-reads), and *homelessness* (no existing doc owns it). A clean epic produces zero cards, and that is success — **but only if `candidates.json`'s `diagnostic` is null.** If the mechanical pass set a `diagnostic` (degenerate clustering — high out-of-root %, no multi-file subsystems), the run is **INCONCLUSIVE, not clean**: surface the ⚠ as the finding and stop. Never let a failed run report success (the v5.17.1 defect-gating rule, applied to context-fit).

## Feedback channel — bookend every run (autonomous; do NOT skip)

The maintainer can't watch every project. `~/.claude/context-fit-findings.md` is how a run reports back without a human relay. Two steps, always:

- **START — consume advice.** Open the log, find this project's section. If its **Advice** is not `none`, READ it (maintainer guidance — e.g. "author X doc and register it"), act on it or surface it to the user, then reset Advice to `none`.
- **END — append your finding (good OR bad).** Append one line to this project's **Findings**: `- [UNCHECKED] <YYYY-MM-DD> · epic <N> · <outcome>` (e.g. `CLEAN (0 cards)`, `2 cards proposed: …`, `⚠ DIAGNOSTIC: <reason>`). Create the section if absent. **Every run logs** — a clean run logs "clean," a broken/inconclusive run logs its ⚠. You append; you never clear your own findings (the maintainer does, on review).

## Inputs — run the mechanical tools first

```bash
# 1. footprint of the epic (main + sub-agents + sibling sessions)
python3 footprint.py --session <uuid> --json /tmp/fp.json
#    (or --cwd /project --branch <b> --since <iso>  to span a multi-session epic)

# 2. candidates + homelessness check against the project's real context dir
python3 candidates.py --footprint /tmp/fp.json \
        --context-dir <project>/docs/context --json /tmp/cand.json
```

## Procedure

### 1. For each HOMELESS candidate — run the Necessity Gauntlet

A candidate must pass **all seven** or it is dropped. Footprint evidence answers Q1; the coverage check seeds Q3 — but **READ the nearest existing context file before asserting homelessness** (don't trust the filename heuristic).

1. **Pain / recurrence** — evidenced by the candidate's re-read counts (`executor 19×`…). If `reread_files < 2`, drop: a single hot file is not a subsystem.
2. **Wrong-edit** — name the concrete regression an agent invites by editing this subsystem blind (use the co-read coupling).
3. **Homelessness** — open the existing context files; confirm none genuinely owns this. If one *could* hold it as a section → route to **section-in-existing**, not a new file.
4. **Non-derivable** — the doc must capture the *why* (decision model, invariant, cross-file contract), not restate code.
5. **Durability** — stable subsystem, not a one-epic scratch area.
6. **Reader** — name the next 2–3 tasks that open it first.
7. **Maintenance** — the re-index/refresh trigger (diffs under the scope).

### 2. Decide the shape (route, don't default to "new file")

- **New global context doc** — a whole subsystem with a durable *why* (e.g. `Research_Agent_Context.md`).
- **Scoped index** — `docs/context/indexes/<area>.md` (`Scope:` glob) when the need is *navigation/relevance*, derived from the co-read cluster.
- **Both** — common: a doc for the *why* + an index for the *where*.
- **Section in an existing doc** — when Q3 found a near-owner.

### 3. Also surface (same pass)

- **Index-split hints** — the footprint's co-read clusters → the `Scope:` boundary, empirically.
- **Cross-epic coupling** — heavy reads of *another* epic's files → flag for the PARKED PDR-supersession item (lifecycle, not context-fit). Report, don't act.
- **Anti-bloat — CONSERVATIVE, default KEEP.** A registered, scope-checked file is self-maintaining and **can't go stale** (any diff in its scope updates it at Phase 5.7a), so keeping it costs one cheap scope-skip per feature. **Zero reads is NOT a retire signal** — a subsystem untouched for months is *dormant, not bloat*; its doc is still valid and will be needed again (you'll do an admin epic for a quarter, then return to research). Propose retire / de-register **only** when (a) the file's `Scope` maps to **no surviving code** (subsystem deleted or absorbed), or (b) it's a **confirmed duplicate / superseded** by another doc. A long read-drought is at most a soft "is this subsystem still alive?" prompt — never an automatic drop. Even a real retire is **human-approved** (a 0-read file may be deliberately human-triggered, e.g. a marketing-agent doc). **When in doubt, keep it** — the cost of keeping is a skip; the cost of dropping is a future stale read.

### 4. Emit one card per surviving candidate — then STOP

Apply nothing. Default No. Card format:

```
CONTEXT-FIT PROPOSAL — <project> <epic>                         [real footprint + coverage]
CANDIDATE  docs/context/<Name>.md            Tier-P (bespoke) | Tier-K (pack-worthy)
Scope      <subsystem>/**   (+ <coupled dir>/**)
Gauntlet   1 Pain ✓ <evidence>  2 Wrong-edit ✓ …  3 Homelessness ✓ <files checked>  … 7 ✓
           → RECOMMEND ACCEPT|SECTION|INDEX-ONLY|DROP.  Default No.  Approve? [y/N]
+ INDEX    docs/context/indexes/<area>.md  (Scope: …)   co-reads: <pair Nx · …>
```

Close with a **CONTEXT-FIT GATE** summary: candidates considered, cards proposed, anti-bloat flags, cross-epic flags, and what was deliberately dropped (with reason). Nothing auto-applied.

**Don't put the judgment on the human.** The Gauntlet is the judge — it read the actual docs; the maintainer running this across many projects hasn't, and isn't expected to. So:
- **A 0-card run asks nothing.** Report `CLEAN — logged, nothing to apply` and stop. No "Apply?" prompt — there's nothing to decide.
- **When cards survive,** the human approves the *action* (spend effort authoring the doc), not the *judgment* (is it warranted — that's already decided). And because every run is logged to the findings channel, they can always **defer to `/maintainer-review`** instead of deciding in the project session. Never demand an on-the-spot call from someone who hasn't read the project.

## On approval — close the maintenance loop (do NOT skip)

A context file that is created but not maintained rots into false information — the exact failure this feature exists to prevent. So **accepting a card is two actions, not one:**

1. **Write** the context doc / scoped index (a real pass over the subsystem code — not a stub).
2. **Register** it in the project's `.claude/agents/context-docs-agent.md` **`target-files` slot** (inside the `KIT:SLOT-BEGIN target-files` markers), annotated with its `Scope` so the Phase-5.7a agent reads it every feature but only **deep-updates when the diff touches that scope**. Bump the count references in that file (drift discipline).

This is **fail-safe registration** — once in the slot the file can't be silently missed. The inverse, an accepted **anti-bloat retire**, is **de-registration** (remove the slot entry), equally human-approved. The `target-files` slot stays curated by exactly two gated flows: *add on accept*, *drop on proven-unread*.

## Not yet in scope (later phases)

- **Failure × footprint (the apex "Understand" signal)** — "what he should have searched for but didn't," from a test/bugfix failure cross-referenced against the footprint. Needs wiring into the test/bugfix flows; Phase 2 covers only the reconstruction/coverage signals.
- **Tier-K graduation** — when the same bespoke candidate recurs across ≥3 projects (e.g. an "agent-subsystem context" pattern), propose a kit pack via `/maintainer-review`.
