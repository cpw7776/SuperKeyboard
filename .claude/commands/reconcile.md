---
description: Reconcile an ad-hoc change — backfill tests, update architecture/PRD/ADR if touched, and sync context + user-facing docs after unplanned work
---

Switch to `opus` first.

Read `docs/prompts/reconcile-change.md` and follow it end-to-end against the change I just made.

This is the *reverse* flow: I already did some unplanned work directly (a fix, a few tweaks on a page, or a small feature) with no PRD/ADR and no tests-first. Catch the project up — backfill the tests, capture any durable test/code lessons learned (the lightweight mirror of the lifecycle's Gap A / Gap B / Gap C retro), update any architecture/PRD/ADR doc the change actually touches, and sync the context + user-facing docs.

Source of truth:
- **What changed** → `git` (the diff is the authority — drive every file decision from it, never from memory).
- **Why it changed** → this chat, *if the work was done here*. If this is a fresh chat with no prior context, drive everything from `git` and ask me for the intent before writing tests or docs.

Respect the prompt's single stop point: confirm the change set with me before editing anything.

> If `docs/prompts/reconcile-change.md` doesn't exist in this project, it hasn't been upgraded to the kit version that ships `/reconcile` (v5.9+). Upgrade with `/kit-upgrade` first, or tell me and I'll run the reconciliation steps inline.
