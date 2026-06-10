---
name: context-fit
description: Context-Fit (Gap D) — read-only scan of THIS project that finds domain subsystems agents reconstruct by hand (heavy re-reads) with no owning context doc, and proposes new context docs / scoped indexes through a strict necessity gauntlet (default No). Auto-invoked by feature-lifecycle Phase 5.1 (Gap D) at epic close; also run ad-hoc when agents seem to keep re-deriving how a subsystem works, or after a failure that smells like "the agent didn't know how this project works".
---

# Context-Fit (Gap D) — keep the context taxonomy fitting the project

**What it does:** sweeps this project's session transcripts (main agent + Task sub-agents + parallel panes) for the read/search footprint, clusters re-read files into subsystems (stack-agnostic, relative to the project root), checks each against `docs/context/` for an owner, and — only for genuinely homeless, gauntlet-surviving subsystems — proposes a context doc + scoped index for **human approval (default No)**. Read-only: it never writes context without an explicit yes.

## Run it (mechanical pass — from the project root)

```bash
python3 .claude/skills/context-fit/run.py --project .
```

Writes `/tmp/context-fit/footprint.json` + `candidates.json` (with a `diagnostic` field). Transcripts auto-clean after ~30 days — run at epic close.

## Then the judgment pass

Follow **`context-fit-analysis.md`** (in this skill directory) end-to-end. Non-negotiables baked into it:

- **Bookend the findings channel** (`~/.claude/context-fit-findings.md`): consume any maintainer **Advice** at START; append ONE finding (good or bad) at END.
- **`diagnostic` ≠ null → the run is INCONCLUSIVE, not clean.** Never report a failed sweep as a clean epic.
- **The Gauntlet judges, not the human.** Read the actual context docs before trusting a HOMELESS label (role-named docs like `Project_PDR.md` over-trigger the filename heuristic). A 0-card run **asks the human nothing**.
- **Accept = write + REGISTER** — author the doc from a real read-pass over the code (never a stub), add it to `context-docs-agent`'s `target-files` slot (scope-annotated) so Phase 5.7a maintains it forever, and lint any scoped index with `index_lint.py`.
- **Anti-bloat is conservative** — dormant ≠ bloat; default keep; retire only dead-scope/duplicates, human-approved.

`Indexing_Guide.md` (also in this directory) governs scoped indexes (`docs/context/indexes/<area>.md`): Scope discipline, self-bootstrap templates, anti-rot writing, re-index triggers.

## Files in this skill

| File | Role |
|---|---|
| `run.py` | one-command mechanical pass (footprint → candidates) |
| `footprint.py` / `candidates.py` | the sweep + clustering/coverage engine (importable standalone) |
| `context-fit-analysis.md` | the judgment procedure (Gauntlet → cards → register) |
| `Indexing_Guide.md` | scoped-index rules + templates |
| `index_lint.py` | mis-file lint for scoped indexes (gate-suitable, exit 1 on issues) |
