# PRD — AI Action Engine (`E2-ai-action-engine`)

> **Status:** Approved scope + plan (2026-06-09). **Companions:** ADR `docs/ard/ADR_AI_Action_Engine.md`, Feature Architecture `docs/architecture/Feature_Architecture_AI_Action_Engine.md`, plan snapshot `docs/plans/2026-06-09-ai-action-engine.md`, test plan `docs/testing-agents/ai-action-engine-tests.md`.

## Goal
Wire the 5 AI toolbar actions (translate, rewrite, summarize, expand, presets) to a real, user-configured, OpenAI-compatible chat engine. External path only in v1; default-OFF; preview-then-apply; Keystore-encrypted API key. Honor the privacy invariants in the ADR.

## Out of scope (v1)
On-device local model & local-first routing (v2). TTS/STT (Phase-3 speech, separate). Streaming responses. Room-backed presets.

## User journey
User opens Settings → AI → enables AI, enters endpoint URL + API key + model (+ default translate language). In any text field, selects text (or none), taps an AI action → sees a loading preview → result appears → Apply replaces (or Cancel restores). With AI disabled, the actions show a "set up AI in Settings" hint and **no network call is made**. On password fields, AI actions are unavailable.

---

## Tasks

### Batch A — Foundation (deps, manifest, crypto)
- [ ] **A1** Add OkHttp + kotlinx-serialization-json to `gradle/libs.versions.toml`; add the `kotlin("plugin.serialization")` + libs to `app/build.gradle.kts`.
- [ ] **A2** Add `<uses-permission android:name="android.permission.INTERNET" />` to `AndroidManifest.xml` (keep `allowBackup="false"`).
- [ ] **A3** Add kotlinx.serialization keep-rule to `app/proguard-rules.pro` (E2 owns this; E4 does not).
- [ ] **A4** `util/KeystoreSecretBox.kt` — AES-256-GCM encrypt/decrypt for a given alias + app-private file, mirroring `ClipboardDatabase` crypto (`[ivLen][iv][ct]`).
  - **STOP: AI Test** — round-trip encrypt→decrypt returns original; ciphertext ≠ plaintext bytes; distinct alias from clipboard. Anti-patterns: A3 (cover empty/long input), A1 (assert behavior not impl).
- [ ] **A5** `ai/AiKeyStore.kt` — wraps `KeystoreSecretBox` with alias `superkeyboard_ai_key`, file `ai_api_key.enc`; `setKey`/`getKey`/`clear`. **Never reuse the clipboard alias; never log the key.**

### Batch B — Engine core (TDD)
- [ ] **B1** `ai/AiAction.kt` — enum {TRANSLATE, REWRITE, SUMMARIZE, EXPAND, PRESET} + `systemPrompt(targetLang, presetPrompt)` builder.
  - **STOP: AI Test** — each action yields the expected system prompt; TRANSLATE includes the target language; PRESET uses the user prompt. Anti-patterns: A1, I5 (typed, not stringly).
- [ ] **B2** `ai/AiModels.kt` — `@Serializable` `ChatRequest`/`ChatMessage`/`ChatResponse`/`Choice` DTOs (OpenAI-compatible shape).
- [ ] **B3** `ai/AiChatClient.kt` — interface `suspend fun complete(endpoint, apiKey, model, messages): AiResult` (sealed: `Success(text)` / `HttpError(code,msg)` / `NetworkError(msg)`).
- [ ] **B4** `ai/OkHttpAiChatClient.kt` — impl: lazy singleton `OkHttpClient` with ~30s timeouts; POST `/chat/completions`; Bearer auth; parse `choices[0].message.content`; map failures to sealed result (I1 — never swallow). Validate inputs (I2).
- [ ] **B5** `ai/AiEngine.kt` — `suspend fun run(action, inputText, settings, keyStore): EngineResult` (sealed: `Disabled` / `Unconfigured` / `TooLong` / `Result(text)` / `Error(msg)`). **Gate FIRST:** if `!enabled` → `Disabled` (no client touched); if endpoint/key blank → `Unconfigured`; if text blank/over cap → handled; else build prompt → call client lazily.
  - **STOP: AI Test** — (1) **toggle-OFF egress guard:** with `enabled=false`, inject a recording `FakeAiChatClient`, assert `callCount == 0` and result `Disabled`. (2) `Unconfigured` when endpoint/key missing. (3) happy path returns client text. (4) HttpError/NetworkError → `Error` with message. (5) over-cap input → `TooLong`. Anti-patterns: A2/A4 (fake the boundary, assert behavior), A3 (error/empty/boundary), A7 (`runTest`).

### Batch C — Settings (model + UI)
- [ ] **C1** `SettingsRepository.kt` — keys `ai_enabled` (Bool, default false), `ai_endpoint_url`, `ai_model`, `ai_target_lang` (default "English"), `ai_presets_json`; flows + suspend setters. API **key** routed through `AiKeyStore`, NOT DataStore.
- [ ] **C2** `SettingsViewModel.kt` — StateFlows + setters for the above.
- [ ] **C3** `settings/screens/AiSettingsScreen.kt` — master switch, endpoint/model/key fields (key field masked), default-language field, presets editor (add/edit/delete name+prompt). Mirror `ClipboardSettingsScreen.kt`.
- [ ] **C4** Route in `SettingsNavHost.kt` + entry in `MainSettingsScreen.kt`.
- [ ] **STOP: Human Verification** — settings persist across app restart; key field never shows plaintext after re-entry.

### Batch D — IME wiring + preview
- [ ] **D1** `toolbar/AIToolbarView.kt` — replace the 5 `/* Phase 2 */` no-ops with an `onAiAction: ((AiAction) -> Unit)?` callback; clipboard/tts/stt untouched.
- [ ] **D2** `ime/AiPreviewView.kt` — `LinearLayout` overlay: loading state, result text, Apply/Cancel; own `CoroutineScope(Main)` + `destroy()`; mirror `ClipboardBottomSheet`.
- [ ] **D3** `ime/KeyboardService.kt` — add a `CoroutineScope` (cancel in `onDestroy`); wire `toolbarView.onAiAction`; read field text (selection via `getSelectedText`, else whole field via `getExtractedText`/before+after cursor); show `AiPreviewView`; launch `AiEngine.run`; render result/error; Apply replaces text via `InputConnection`, Cancel restores keyboard.
  - **STOP: AI Test** — text-targeting helper: returns selection when present, whole field when not; Apply replaces exactly. Anti-patterns: A1, A3 (empty field, no-selection).
- [ ] **D4** **Disable AI actions on password/secure input types** (`EditorInfo` `TYPE_TEXT_VARIATION_PASSWORD` family) — engine never receives secret field contents.
- [ ] **STOP: Human Verification** — run each of the 5 actions end-to-end against a live endpoint; confirm preview→Apply/Cancel; confirm disabled-state hint + no network; confirm absent on a password field.

### Batch E — Strings + privacy copy
- [ ] **E1** Add to `strings.xml`: AI settings labels, preview title/Apply/Cancel/loading/error, "set up AI" hint. (Toolbar action labels already exist.)
- [ ] **E2** **Revise `about_privacy` / `about_description`** to state network egress is opt-in, default-off, and to the user's own endpoint only.

---

## Validation
- **Success criteria:** all 5 actions produce a preview and apply correctly against a real OpenAI-compatible endpoint; OFF = provably no egress (test + manual); key encrypted at rest; password fields excluded.
- **Edge cases:** empty field, no selection, over-length input, bad endpoint, wrong key (401), network down/timeout, Cancel mid-flight, app restart (settings persist), disabled state.

## Human Testing Plan (Phase 4 handoff)
1. Build `:app:assembleDebug`, sideload via Syncthing, enable SuperKeyboard.
2. Settings → AI: enable, enter Venice endpoint + key + model, set target language. Restart app → settings persisted, key field masked.
3. In a notes app: type a sentence, select it, tap **Rewrite** → preview shows → **Apply** replaces. Repeat **Summarize**, **Expand**, **Translate** (check target language), **Presets** (create one first).
4. With no selection, tap **Summarize** → operates on whole field.
5. Toggle AI **off** → tap an action → "set up AI" hint, **no** network (airplane mode optional check).
6. Focus a **password** field → AI actions unavailable.
7. Bad key / airplane mode → preview shows a clear error, original text untouched.
