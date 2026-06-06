# Changelog

All notable changes to SuperKeyboard will be documented in this file.

> **Format:** Each entry should include: what changed, why, files affected, and any DB/component-surface/privacy changes.
> **Rule:** Every feature and every fix gets a changelog entry. No exceptions.
> **Note:** SuperKeyboard has no HTTP API and no auth — the "API changes" line tracks the Android component/service surface; the "Auth changes" line tracks the permission/privacy model.

## [Unreleased]

> Add entries here as features and fixes are completed. Move to a versioned section on release.

### AI Dev Workflow Kit adopted (v5.14) — 2026-06-06

Adopted the AI Dev Workflow Kit (fresh adoption). Copied `.claude/` (4 sub-agents + `/reconcile` command) and `docs/` (prompts, context, mobile, upgrading) into the project, adapted the sub-agent `[CUSTOMIZE]` slots to this native-Android/Kotlin/Gradle stack (Gradle test/build commands, no browser/dev-server, no auth, in-app docs surfaces), and built out the seven context files from the existing Phase 1 codebase.

**New files:** `.claude/agents/*.md`, `.claude/commands/reconcile.md`, `docs/**`, `docs/KIT_VERSION` (=5.14), `docs/KIT_DEVIATIONS.md`, `CLAUDE.md`.
**DB changes:** None. **Component-surface changes:** None. **Privacy changes:** None (documented existing model).

### Phase 1 foundation — (pre-kit, git commit `1c30398` + fixes through `feba9d3`)

Working IME keyboard with encrypted clipboard: custom drawn `KeyboardView`, gesture typing, emoji picker, AI toolbar scaffold, Compose settings app (Appearance/Clipboard/About), and a SQLCipher-encrypted Room clipboard store keyed by an Android-Keystore-wrapped passphrase. Follow-up fixes corrected the SQLCipher artifact/version, the `SupportOpenHelperFactory` import, RecyclerView adapter position handling, and native-library load order.

**DB changes:** Added `clipboard_entries` table (Room, SQLCipher-encrypted). **Component-surface changes:** Added `KeyboardService` (IME) + `SettingsActivity` (launcher). **Privacy changes:** Established encrypted-at-rest clipboard + no-network model.
