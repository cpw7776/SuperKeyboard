# Indexing Guide — path-mapped scoped indexes

> **Audience:** `context-docs-agent` and the `context-fit` skill — read this before creating or touching a scoped index. Ships with the kit as part of the `context-fit` skill (kit v5.19+).

> **Why this exists:** one monolithic `Context_Index_File.md` is too long, read in full on every feature, and not relevance-scoped. The fix is **a lean top-level router + scoped sub-indexes**, each addressable by path — co-location's *benefit* (relevance) without dox's *mechanism* (no per-folder `AGENTS.md`; indexes stay centralized in `docs/context/indexes/`).

## The two artifacts

1. **Router** — `docs/context/Context_Index_File.md` becomes a **map of maps**: one row per area → its scoped index + `Scope` + one-line purpose. Lean; never lists individual files.
2. **Scoped indexes** — `docs/context/indexes/<area>.md`, each declaring a `Scope:` glob and listing the durable files/symbols in that subsystem.

An agent about to edit `apps/web/src/lib/research/…` matches the router → opens `indexes/research.md` + the router, **not** the whole catalog.

## When to create a scoped index

- A **homeless subsystem** surfaced by `/context-fit` (concentrated re-reads, no owning context doc) — the co-read cluster *is* the `Scope`.
- An area whose entries in the monolith exceed ~15 lines, or that forms a clear durable boundary.
- **Not** for a one-file hot spot or a churny scratch area (fails durability).

## Scope discipline (this is what makes mis-indexing lint-catchable)

- Every scoped index declares **`<!-- Scope: <glob>[, <glob>…] -->`** on its H1.
- Optional **`<!-- Root: <dir> -->`** so entry paths can be written relative to it (readability); else entries are repo-relative.
- Every **primary entry's** path must fall under the `Scope`. A primary entry outside Scope is a **mis-file** → `index_lint.py` flags it.
- A file that legitimately belongs to *another* index but is referenced here is a **cross-reference**, marked `<!-- cross-ref: <path> (owned by indexes/<x>.md) -->` and exempt from the Scope check. (This is how cross-cutting files are handled — list where *primarily* owned, cross-reference elsewhere.)

## Self-bootstrapping templates (dox borrow — index files only)

A scaffolded index ships **primed**: on first read an agent populates it, then deletes the bootstrap block.

**Scoped index** — `docs/context/indexes/<area>.md`:

```markdown
# Index: <area>
<!-- Scope: <glob>[, <glob>…] -->
<!-- Root: <dir> -->

> Anti-rot: stable contracts only — one line per durable file/symbol; delete stale
> entries instead of explaining history; trim the obvious. The closer to the work,
> the more concrete.

<!-- BOOTSTRAP: this index is not yet built. On first read, scan the Scope glob(s),
     then REPLACE this block with the real index (rows below). Split into child
     indexes if the area is large. Then delete this BOOTSTRAP comment. -->

| File | Role | Key exports / gotchas (the *why*, not a restatement of code) |
|---|---|---|
```

**Router** — `docs/context/Context_Index_File.md` (the map of maps):

```markdown
# Context Index — router
<!-- To find context for a path: match it below, open that index. Lean — no file lists here. -->

| Area | Index | Scope | Purpose |
|---|---|---|---|
| research | indexes/research.md | apps/web/src/**/research/** | research agent · verify/fan-out · UI |
| storage  | indexes/storage.md  | src/storage/**             | persistence drivers · adapter · migration |

<!-- BOOTSTRAP: scan the project's top-level subsystems, fill one row per durable area,
     scaffold each indexes/<area>.md, then delete this comment. -->
```

## Anti-rot writing discipline (dox borrow — all context files)

- Document **stable contracts, not diary entries**.
- **Delete stale notes** instead of explaining history.
- **Trim** the obvious, repeated rules, and warnings for risks that no longer exist.
- The closer a doc is to the work, the more concrete and practical it must be.

## Re-index trigger discipline (anti-churn)

Update an entry **only when a file's role, public interface, or scope-membership changes** — *not* on every edit. A pure logic tweak that doesn't change what the file *is* gets no index churn. (This keeps the indexes cheap to maintain — they're read on every feature.)

## "What if the agent files it in the wrong index?"

Mostly a non-issue, because of `Scope`:

- **Primary entry outside its index's Scope** → `index_lint.py` catches it (path vs glob). Mechanical, not a judgment call.
- **Cross-cutting file** → list it under its primary owner; mark a `cross-ref` elsewhere.
- **Genuinely ambiguous util** → index where *primarily* owned; cross-reference the rest.
- **A persistent pattern of router-misses in the footprint** (agent can't find via the router) → the signal that an index's `Scope` or the split itself is wrong → back into `/context-fit`.

Run `python3 index_lint.py <project>/docs/context/indexes` as a gate check after any index edit.
