# CLAUDE.md — SuperKeyboard

> **SuperKeyboard** is a native Android IME (custom keyboard) built in Kotlin: Jetpack Compose settings app + classic-Views keyboard surface, Gradle (Kotlin DSL), Room + SQLCipher encrypted clipboard, DataStore settings. Offline, no accounts, privacy-first. This project uses the **AI Dev Workflow Kit** (`docs/KIT_VERSION` = 5.14).

---

## Documentation System

This project uses a structured documentation system in `docs/`. **Read before you build.**

### Context Files — Source of Truth (`docs/context/`)

Before starting any work, read the relevant context files:

| File | Read when... |
|------|-------------|
| `docs/context/Context_Index_File.md` | Always — find any file in the project |
| `docs/context/Project_PDR.md` | Understanding architecture, patterns, tech stack |
| `docs/context/database_reference_guide.md` | Before ANY Room/SQLCipher/DataStore changes |
| `docs/context/API_REFERENCE.md` | Before changing the Android component/service surface (IME, activities, intents) — there is no HTTP API |
| `docs/context/Project_Authentication.md` | Before changing permissions or the privacy model — there is no auth |
| `docs/context/PRODUCTION_READY.md` | Checking milestone status, known bugs |
| `docs/context/CHANGELOG.md` | Understanding recent changes |
| `docs/context/Unit_Test_Writing_Guide.md` | Before writing any failing test in TDD (universal anti-patterns + project lessons appended by `test-suite-retro.md`) |
| `docs/context/Implementation_Patterns.md` | Before writing implementation code (the code-side mirror of the test guide) |

### Workflow Prompts (`docs/prompts/`)

| Prompt | When to use |
|--------|------------|
| `docs/prompts/feature-lifecycle.md` | Full feature development — 3 stop points, otherwise autonomous |
| `docs/prompts/reconcile-change.md` | **After unplanned work** (reverse lifecycle, `/reconcile`) — ad-hoc fix / small feature with no PRD/ADR/tests-first; backfills tests, updates architecture/PRD/ADR only if touched, syncs context + user-facing docs. One stop point: confirm the change set |
| `docs/prompts/bugfix.md` | Investigating and fixing a known bug (auto-runs `test-suite-retro.md` for Gap A always, `testing-retro.md` for Gap B when bug escaped past Phase 4) |
| `docs/prompts/debug.md` | Diagnosing an unknown issue |
| `docs/prompts/create-testing-agent.md` | Maintain testing-agent library — find-or-create (Step 0 reads `docs/testing-agents/REGISTRY.md`) and Adapt Mode |
| `docs/prompts/testing-retro.md` | **Gap B** — self-improvement loop for the testing-agent library. Run when manual/instrumented testing catches a bug a test plan should have caught |
| `docs/prompts/test-suite-retro.md` | **Gap A** — self-improvement loop for the unit/integration test suite. Logs to `docs/test-suite-misses.md`, appends a Project Lesson to `docs/context/Unit_Test_Writing_Guide.md` if generalisable |

### Sub-Agents (`.claude/agents/`)

These are invoked by `feature-lifecycle.md` — do not run them inline.

| Sub-agent | Invoked from | Produces |
|-----------|--------------|----------|
| `.claude/agents/testing-agent.md` | Phase 4.1 | PASS/FAIL/SKIP/ABORT/BLOCKED_NEEDS_FIXTURE/REQUIRES_INPUT block (verbatim). **Adapted for native Android:** verification is Gradle unit/instrumented tests + manual on-device testing, NOT browser automation (no dev server / no DOM exists). |
| `.claude/agents/code-quality-agent.md` (Pre-Testing Mode) | Phase 3.5 | `CODE QUALITY GATE (Pre-Test):` block (4 items + process sweep, verbatim). |
| `.claude/agents/code-quality-agent.md` (Post-Manual-Testing Mode) | Phase 5.5 | `CODE QUALITY GATE:` block (10 items, verbatim). Gate #3. |
| `.claude/agents/context-docs-agent.md` | Phase 5.7a | `CONTEXT DOCS GATE:` block (verbatim) |
| `.claude/agents/docs-auditor-agent.md` | Phase 5.7b | `DOCUMENTATION GATE (User-Facing):` block (verbatim). Adapted: user-facing surfaces are README + in-app Settings/About + `strings.xml`, not a web Help Center. |

### Model Split — Opus Owns the Critical Path, Sonnet Owns Structured Execution

**Planning, implementation, and gate-quality decisions run on Opus; walking a fixed test plan or decision tree runs on Sonnet.**

| Role | Model |
|------|-------|
| Orchestrator running `feature-lifecycle.md` (Phase 1 questions, Phase 2 planning, Phase 5 reconciliation, dispatch + supervision) | **Opus** |
| Phase 3 ad-hoc sub-agent | **Opus** (override to Sonnet only for purely mechanical scaffolding) |
| Phase 4 manual-test fix sub-agents | **Opus** |
| `code-quality-agent` (Pre-Test and Post-Test) | **Opus** (agent frontmatter) |
| `testing-agent`, `context-docs-agent`, `docs-auditor-agent` | **Sonnet** (each agent's frontmatter) |

**Switch the main chat to `opus` before invoking `feature-lifecycle.md`, `bugfix.md`, or `debug.md`.**

### Sub-Agent Supervision (orchestrator mode — v5.7+)

Sub-agents are isolated context windows; their return summary describes intent, not reality. On every dispatch the orchestrator MUST:
1. **Structured returns** — every sub-agent ends with a `SUB-AGENT RETURN` block; re-dispatch if missing/prose-only.
2. **Verify-before-trust** — `git show <hash> --stat` every reported commit; full `git show <hash>` for anything touching architecture/data flow/public surface. The diff is the source of truth.
3. **Bounded scope** — split >~30-tool-call work into sequential batches.

Phase 5.3 / 5.7 doc work is driven by `git log` / `git diff main...HEAD`, not transcript memory. Phase 2 runs inline (not dispatched).

### Feature Documentation (created per feature)

| Folder | Contains |
|--------|---------|
| `docs/prd/` | Product Requirements Documents (what to build) |
| `docs/ard/` | Architecture Decision Records (how to build it) — **note:** any outbound network call needs an ADR here (privacy constraint) |
| `docs/architecture/` | Feature architecture docs (action-to-DB flows) |
| `docs/plans/` | Implementation plans + retrospectives |
| `docs/bugs/` | Bug reports with investigation and resolution |
| `docs/testing-agents/` | Persistent library of test plans + `REGISTRY.md` index. Each plan has a `## Miss Log`. |

### Documentation Rules

1. **Never skip doc updates.** Every feature must update every affected context file in `docs/context/`.
2. **Check before creating.** Search for existing PRD/ADR before creating new ones — update, don't duplicate.
3. **CHANGELOG is mandatory.** Every feature and every fix gets an entry. No exceptions.
4. **Context Index stays current.** Every new file created must be added to `docs/context/Context_Index_File.md`.
5. **PRDs match reality.** After implementation, rewrite the PRD to reflect what was actually built.

---

## Development Workflow

### Feature Development
Use `docs/prompts/feature-lifecycle.md`. Three stop points: (1) scope approval, (2) plan-mode approval, (3) manual testing handoff.

### Ad-Hoc Work (reverse lifecycle)
Did an unplanned fix or small feature directly? Run `/reconcile` (or `docs/prompts/reconcile-change.md`) afterwards.

### Bug Fixes
Use `docs/prompts/bugfix.md`. One stop point after investigation, before applying the fix.

### Debugging
Use `docs/prompts/debug.md`. One stop point after diagnosis, before applying the fix.

---

<!-- KIT:SLOT-BEGIN commands -->
## Commands

This is a Gradle (Kotlin DSL) Android project. Use the wrapper (`./gradlew`).

| Command | Purpose |
|---------|---------|
| `./gradlew :app:assembleDebug` | Build a debug APK |
| `./gradlew :app:installDebug` | Build + install on a connected device/emulator |
| `./gradlew :app:compileDebugKotlin` | Fast compile-only sanity check |
| `./gradlew :app:testDebugUnitTest --no-daemon` | Run JVM unit tests (single run) — *once a test source set exists* |
| `./gradlew connectedDebugAndroidTest` | Run instrumented tests (needs a device/emulator) |
| `./gradlew lint` | Android Lint |
| `./gradlew :app:assembleRelease` | Release build (minify/shrink; signing not yet configured) |

### ⚠️ Test Command Safety

- Gradle test tasks are **always single-run** (no watch mode). Use `--no-daemon` for clean one-shot runs in automation.
- **No unit-test source set exists yet** (`app/src/test/`, `app/src/androidTest/` are absent). The first test-bearing change must create one — see `docs/KIT_DEVIATIONS.md` and `docs/context/PRODUCTION_READY.md` (P0).
- Sweep stale workers after runs: `pkill -f "GradleWorkerMain|KotlinCompileDaemon"`. Do **not** kill the Gradle daemon mid-build.
- Build/sideload to a real phone: see `docs/mobile/Android_Build_and_Sideload.md`.
<!-- KIT:SLOT-END commands -->

---

<!-- KIT:SLOT-BEGIN conventions -->
## Conventions

- **Language:** Kotlin 2.0.21, JVM target 17. Package root `io.superkeyboard`.
- **Two UI toolkits, kept separate:** the keyboard surface (`ime/`) uses **classic Android Views**; the settings app (`settings/`) uses **Jetpack Compose**. Don't assume Compose inside IME code.
- **Persistence:** clipboard via Room + SQLCipher (`clipboard/`); settings via DataStore Preferences (`settings/SettingsRepository.kt`). Always go through the repository layer, never the DAO/DataStore directly from UI/IME.
- **Privacy is a hard constraint (product promise):**
  - No `INTERNET` permission, no network calls, no telemetry. Adding any network egress requires an ADR in `docs/ard/`.
  - Never log decrypted clipboard text, the DB passphrase, or Keystore key material.
  - Keep `allowBackup="false"`; never add cloud sync/backup of clipboard data.
  - Don't rename/rotate the Keystore alias `superkeyboard_clipboard_key` casually — it orphans the encrypted DB.
  - AI toolbar features must run on-device or against the user's own endpoint — never a hard-coded third-party cloud.
- **DB migrations:** `ClipboardDatabase` currently uses `fallbackToDestructiveMigration()`. Replace with real `Migration`s before any schema change ships (P0).
- **Dependencies:** versions live in the catalog `gradle/libs.versions.toml` — add/bump there, reference via `libs.*`.
- **User-visible strings** go in `app/src/main/res/values/strings.xml` (not hard-coded).
<!-- KIT:SLOT-END conventions -->

---

<!-- KIT:SLOT-BEGIN skills-tools -->
## Skills / Tools

Relevant Claude Code skills for this stack:

- `/coding-standards` — apply with Kotlin/Android idioms in mind.
- `/code-review high`, `/vulnerability-scanner` — used by `code-quality-agent` (Gate #3).
- `/mobile-apk-builder` (or `/capacitor-apk-builder` for the web case — N/A here; this is native) — build an installable APK. Native build path is documented in `docs/mobile/Android_Build_and_Sideload.md`.
- **Not applicable** (web-only): `/web-design-guidelines`, browser-route `/performance`, `/agent-browser`, `/playwright-best-practices`, `/tailwind-css`, the React/Next/Supabase/Vercel skills. There is no web UI, dev server, or browser in this project.
<!-- KIT:SLOT-END skills-tools -->

---

## Post-Feature Gates (FIVE — produced by Phase 5.1 + Phase 5.4 + three sub-agents)

Phase 5 of `feature-lifecycle.md` requires **five** verbatim gate blocks before committing, in order:

1. **`RETROSPECTIVE GATE:`** — verbatim from Phase 5.1 (main agent). Unconditional retro sweep over every manual-test finding ∪ `fix(` commit, with Gap A / Gap B / Gap C (→ `docs/context/Implementation_Patterns.md`) / planning / Tier-3 / deliberately-not-lessoned lines. Zero-finding epic still prints it with all lines `none`.
2. **Test-Suite Summary Lines** — verbatim from Phase 5.4's pre-merge full-suite re-run. Any failures must already be in `docs/known-test-failures.md`.
3. **`CODE QUALITY GATE:`** — 10 items (Post-Test mode). Item 7 (`/vulnerability-scanner (2)`) must show `CLEAN`.
4. **`CONTEXT DOCS GATE:`** — CHANGELOG line must show `Entry added — MANDATORY`.
5. **`DOCUMENTATION GATE (User-Facing):`** — every decision-tree question answered YES/NO with evidence.

Do not commit until all five are present, verbatim, with no unresolved failure.

## Recommended Discipline Files

- **`docs/known-test-failures.md`** — date-stamped, approved pre-existing failures.
- **`docs/known-test-skips.md`** — baseline skip count + reasons.

Create them as empty files on the first feature that touches the (future) test baseline.

## Test Suite Self-Improvement (Gap A)

- **`docs/context/Unit_Test_Writing_Guide.md`** — read at every Phase 3.3 TDD failing test. Never hand-edit lessons; run a retro.
- **`docs/test-suite-misses.md`** — chronological log of unit-test misses.
- **`docs/prompts/test-suite-retro.md`** — the protocol that ties them together.

The testing-agent equivalent (Gap B) lives in `docs/prompts/testing-retro.md` + `docs/testing-agents/REGISTRY.md` + per-plan Miss Logs.

<!-- KIT:SLOT-BEGIN mobile-android -->
## Mobile (Android)

This project IS a native Android app and ships an APK. The canonical build/sideload reference is `docs/mobile/Android_Build_and_Sideload.md` (native Android, Kotlin DSL path). Read it before touching the build pipeline — it covers the persistent debug keystore pattern, `apksigner` signature verification, the Syncthing delivery loop, and troubleshooting. Manual on-device testing (install, enable the IME in Android Settings, exercise typing/clipboard/themes) is the primary Phase 4 verification surface.
<!-- KIT:SLOT-END mobile-android -->
