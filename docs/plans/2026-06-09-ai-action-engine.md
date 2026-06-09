# Plan Snapshot — AI Action Engine (`E2-ai-action-engine`)

> **Initial plan snapshot (pre-implementation), 2026-06-09.** What we believed before writing code. The PRD gets rewritten to match reality in Phase 5; this doc preserves the journey. Approved plan-mode artifact: `~/.claude/plans/floofy-swinging-wigderson.md`. Companions: PRD / ADR / Feature Architecture for E2.

## Approach at planning time
Wire the 5 dead AI toolbar actions to a real OpenAI-compatible engine. External (Venice-style) path only in v1, behind a default-OFF master toggle; preview-then-apply UX; Keystore-encrypted API key. Local model is v2 (later epic). This is the app's first network egress → ADR + `INTERNET` mandatory.

## Task breakdown (planned order)
Batch A (deps + manifest + crypto) → Batch B (engine core, TDD) → Batch C (settings model + UI) → Batch D (IME wiring + preview) → Batch E (strings + privacy copy) → docs. See PRD for the per-task checklist with `STOP: AI Test` markers.

## Key technical decisions (carried from Plan Mode; full tradeoffs in the ADR)
- **D1 Networking/JSON:** OkHttp + kotlinx.serialization (vs zero-dep HttpURLConnection+org.json, vs Ktor). Reflection-free JSON for R8.
- **D2 Key storage:** Keystore-encrypted file via new `util/KeystoreSecretBox`, alias `superkeyboard_ai_key` (clipboard crypto untouched).
- **D3 Egress guarantee:** engine-level gate + lazy client; unit-tested "OFF ⇒ client never called".
- **D4 Output UX:** in-keyboard `AiPreviewView` overlay (mirror `ClipboardBottomSheet`).
- **D5 Presets:** DataStore JSON (no Room migration).
- **D6 Action model:** typed `AiAction` enum + prompt templates.

## File estimate
~10 new files (`ai/*`, `util/KeystoreSecretBox`, `ime/AiPreviewView`, `settings/screens/AiSettingsScreen`), ~9–11 edits (toolbar, KeyboardService, settings repo/VM/nav, manifest, gradle catalog + build, proguard, strings), 3–4 test files, 0 DB migrations, 2 new deps + 1 Gradle plugin.

## Known risks at planning time
- Privacy correctness (egress guard test + `about_*` copy revision) — headline.
- Password-field exclusion must be enforced.
- R8 keep rules for serialization (E2 owns; verify on release build with E4).
- Whole-field read/replace edge cases via InputConnection.

## Retrospective (filled in Phase 5)
_TBD — what changed vs this snapshot, what we didn't foresee, decisions that flipped._
