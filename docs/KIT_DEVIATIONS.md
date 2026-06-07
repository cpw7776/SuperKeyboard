# Kit Deviations

> **What this file is for.** This file records every deliberate way the project diverges from the AI Dev Workflow Kit baseline. Future kit upgrades read this file first so they can tell *intentional* deviations apart from *surprises that need user attention*. Without it, every upgrade re-asks the same questions and surfaces the same "this doesn't look right" warnings about choices the project has already made.
>
> **When to update.** Any time you make a project-level decision that the kit's vanilla doesn't anticipate — leaving a slot at its template default, rewriting a kit file end-to-end for a different stack, removing a kit section the project doesn't need, pinning a sub-agent to a non-default model, etc. The kit-adopt skill seeds an empty version of this file; the upgrade-kit prompt adds entries as it discovers them during inventory.
>
> **Optional but recommended.** If this file doesn't exist, upgrades still work — the upgrade-kit prompt will simply log every deviation it finds against a generic "deliberate deviation" category. Maintaining this file makes upgrade output cleaner and surfaces only genuine surprises.

---

## Gate count (machine-readable)

> Format: a single line `Gate count: <four|five> (<reason>)`. The kit default is **five**. Set **`four (suite-less)`** when the project has NO standalone automated test suite (Phase 4 is a build / compile / typecheck / sideload pipeline, not a test runner) and therefore runs four Phase 5.8 gates — Retrospective + Code Quality + Context Docs + Documentation — with **no Test-Suite gate block**; the build/sideload proof + any suite-level lesson fold into the Retrospective Gate's Gap A (or a project-named build gate). See `feature-lifecycle.md` Phase 5.8.
>
> **Why this line is load-bearing:** upgrades read it to branch deterministically. The historical v5.14 "four-gates" wording bug and a *legitimate* four-gate count are TEXTUALLY IDENTICAL — this flag is the only reliable way an upgrade can tell "fix this stale `four` to `five`" from "leave this correct `four` alone." Without it, a gate-count migration can corrupt a correct suite-less file. Set it once and every future upgrade auto-branches.

Gate count: five (project decision — five-gate model per CLAUDE.md "Post-Feature Gates (FIVE)"; the Test-Suite Summary gate activates once the P0 `app/src/test/` source set exists. The project is currently suite-less but deliberately keeps the five-gate model rather than the suite-less four-gate reduction.)

---

## Slots intentionally left as kit templates

> Format: `<file>:<slot-name>` — one-line reason.
>
> Use this when a `[CUSTOMIZE]` slot was deliberately NOT filled at install (the kit's template wording is acceptable as-is, or the project has no project-specific value to put there). Upgrades will not flag these as missing customizations.

- `.claude/agents/testing-agent.md:env-credentials` — re-mapped, not "filled": the app has no accounts/credentials, so the slot states "intentionally empty" rather than listing env vars.
- `.claude/agents/testing-agent.md:auth-section` — re-mapped to "no authentication exists" (single-user on-device IME). The OS-level IME-enablement step is the only access gate.
- `.claude/agents/docs-auditor-agent.md:tour-attributes` — permanent SKIP: no web onboarding tour / JSX framework exists in a native Android IME.

> _Note:_ All four sub-agents' `[CUSTOMIZE]` slots WERE customized for the Android/Kotlin/Gradle stack (not left at template). The bullets above only flag slots whose customization is a "this concept does not apply" statement rather than a concrete project value.

---

## Files structurally rewritten

> Format (machine-readable): one bullet per file, `` - `<file>` `` — one-line reason + scope of rewrite. The **leading backtick code-span is the parseable token**: keep the file path first and wrapped in backticks so an upgrade can extract the exact set without reading the prose.
>
> Use this when the kit's vanilla file is end-to-end inapplicable and the project owns the file outright. Upgrades will skip per-paragraph edits to these files and will not slot-wrap them.
>
> **Why this is load-bearing (like the `Gate count:` line above):** `upgrade-kit.md` Step 6 reads this set to decide whether a failed sanity check is a real `FAIL` or an `EXPECTED-FAIL (divergent)`. It greps this section for the backtick-wrapped paths — so a file omitted here will have its (legitimately-absent) kit anchor mis-reported as a real skipped edit, and a file path written without backticks won't be seen. List every wholesale rewrite, path-in-backticks-first. `feature-lifecycle.md` belongs here too if the project rewrote it wholesale — not only `testing-agent.md`.

- _(none — files were customized via slots, not rewritten end-to-end.)_ The `testing-agent.md` agent-intro slot redirects the whole browser-testing model to Gradle unit/instrumented tests + manual on-device testing, but the file's structure and anchors are otherwise the kit vanilla, so upgrades can still slot-merge it. If a future change rewrites it wholesale, record it here.

---

## Kit sections intentionally absent

> Format: `<file>:<section-name>` — one-line reason.
>
> Use this when the kit ships a section, table, or paragraph the project deliberately doesn't have (e.g. a simplification, a stack-irrelevant feature, a deprecated concept the project moved past). Upgrades will treat edits targeting these sections as `surface-absent` (continue, don't surface as surprise).

- Browser/web testing surfaces (Help Center, FAQ, onboarding tour, legal web pages, dev-server, agent-browser, localhost URLs, test credentials) are **all N/A** for this native Android IME. Where the kit assumes them, the slots were re-mapped to the Android equivalents (Gradle/adb/manual device testing; README + in-app Settings/About + strings.xml). Kit edits targeting those web concepts land as surface-absent.
- `context/API_REFERENCE.md` and `context/Project_Authentication.md` are **repurposed**, not literal: the former documents the Android component/service surface (no HTTP API), the latter the permission/privacy model (no auth). The `context-docs-agent` target-files slot reflects this.

---

## Sub-agent model overrides

> Format: `<agent-file>` — `model: <value>` (reason).
>
> Use this when an agent's frontmatter `model:` field is intentionally set to something other than the kit's default. Upgrades that bump model defaults will preserve these overrides rather than flipping them.

- _(none — all sub-agents keep the kit's default `model:` values.)_

---

## Project extensions to kit files

> Format: `<file>:<section-or-anchor>` — one-line reason + scope of extension.
>
> Use this when the project has ADDED content inside a kit-shipped file that isn't part of the kit's vanilla — extra sections, extra scenario rules, extra Quality Checklist items, extra agent rules, etc. This is distinct from "filled placeholders" (those are the expected case, not a deviation) and from "structurally rewritten" (those replace the whole file). Project extensions live alongside the kit's vanilla content; upgrades must preserve them through merges.
>
> Common case: a project has adopted an **opt-in kit pattern** from `docs/patterns/` (e.g. Pattern B — DB preconditions) which prescribes additions to multiple kit files. Record each addition site so the next upgrade can detect it and preserve it.

- _(none yet.)_

---

## Other project conventions the kit should know about

> Free-form bullets for project-level conventions that affect upgrades but don't fit the categories above. Examples: a project-specific deletion convention (move-to-deleted-dir instead of `rm`), a non-standard root file the kit's CLAUDE.md sync should preserve, a project changelog at the kit's `docs/KIT_CHANGELOG.md` location with project content (rare — most projects keep kit and project changelogs separate).

- **Stack:** native Android IME — Kotlin 2.0.21, Jetpack Compose (settings) + classic Views (keyboard), Gradle Kotlin DSL (AGP 8.7.3, wrapper Gradle 8.11.1), Room 2.6.1 + SQLCipher 4.6.0, DataStore. JVM target 17.
- **Test/build commands:** `./gradlew :app:testDebugUnitTest` (unit, once a source set exists), `./gradlew connectedDebugAndroidTest` (instrumented, needs device), `./gradlew :app:assembleDebug` (build). No npm/Node anywhere.
- **No test source set exists yet** (`app/src/test/`, `app/src/androidTest/` absent). The first test-bearing change must create one. Tracked as a P0 in `context/PRODUCTION_READY.md`.
- **Privacy is a hard product constraint:** no `INTERNET` permission, no network egress, clipboard encrypted at rest. Agents must never log decrypted clipboard text / passphrase / Keystore material, and adding any network call requires an ADR.
- The repo had pre-existing uncommitted Gradle-wrapper changes (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) from an earlier interrupted run; these are genuine project files (wrapper regeneration to Gradle 8.11.1) and were committed separately from the kit adoption, NOT part of it.
