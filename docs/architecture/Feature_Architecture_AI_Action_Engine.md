# Feature Architecture — AI Action Engine (`E2-ai-action-engine`)

> **Companions:** PRD `docs/prd/PRD_AI_Action_Engine.md`, ADR `docs/ard/ADR_AI_Action_Engine.md`. **Status:** in implementation (2026-06-09).

## 1. Button-to-Result Flow (the new outbound path)

```
User taps an AI button in AIToolbarView (translate/rewrite/summarize/expand/presets)
  → AIToolbarView.onAiAction(action: AiAction)              [toolbar/AIToolbarView.kt]
  → KeyboardService handler                                  [ime/KeyboardService.kt]
      ├─ guard: if EditorInfo is password/secure → ignore (D4)
      ├─ read field text: ic.getSelectedText() ?: whole-field via getExtractedText
      ├─ show AiPreviewView (loading)                         [ime/AiPreviewView.kt]
      └─ scope.launch { AiEngine.run(action, text, settings, keyStore) }   [ai/AiEngine.kt]
            ├─ if !ai_enabled            → EngineResult.Disabled      (no client built — privacy gate)
            ├─ if endpoint/key blank     → EngineResult.Unconfigured
            ├─ if text blank / > cap     → handled (TooLong)
            └─ else build messages (AiAction.systemPrompt + user text)  [ai/AiAction.kt, AiModels.kt]
                  → AiChatClient.complete(...)                 [ai/AiChatClient.kt → OkHttpAiChatClient.kt]
                       → HTTPS POST {endpoint}/chat/completions  (Bearer key)  ← ONLY network egress
                       → parse choices[0].message.content
                  → AiResult.Success/HttpError/NetworkError → EngineResult.Result/Error
  → AiPreviewView renders result or error (on Main)
      ├─ Apply  → ic.replace selection / whole field; showKeyboard()
      └─ Cancel → cancel coroutine; showKeyboard() (text untouched)
```

Completion behavior: the panel is always dismissed back to the keyboard via the existing `showKeyboard()` (`removeAllViews()` + re-add toolbar + keyboardView). The request coroutine is cancelled on Cancel and on `onDestroy`.

## 2. Modular Code & Dependencies
- **New package `io.superkeyboard.ai`:** `AiAction` (enum + prompt builder), `AiModels` (`@Serializable` DTOs), `AiChatClient` (interface seam), `OkHttpAiChatClient` (impl), `AiEngine` (orchestration + privacy gate), `AiKeyStore` (encrypted key).
- **Reused / mirrored:** `util/KeystoreSecretBox` (new, extracted from the AES-GCM pattern inline in `clipboard/ClipboardDatabase.kt` — **clipboard left untouched**); the `ClipboardBottomSheet` overlay pattern (→ `AiPreviewView`); the `ClipboardManagerService` constructor-injection pattern (→ engine wiring in `KeyboardService`); the `ClipboardSettingsScreen` Compose pattern (→ `AiSettingsScreen`).
- **External libs (new):** OkHttp (HTTP), kotlinx-serialization-json + Gradle plugin (JSON). Both behind the `AiChatClient` / DTO seam.

## 3. Data Architecture
- **No DB / no Room migration.** Settings via DataStore (existing `SettingsRepository`): `ai_enabled` (Bool, default **false**), `ai_endpoint_url`, `ai_model`, `ai_target_lang` (default "English"), `ai_presets_json` (serialized list).
- **API key:** NOT in DataStore — Keystore-wrapped (`AiKeyStore` + `KeystoreSecretBox`, alias `superkeyboard_ai_key`, file `ai_api_key.enc`, AES-256-GCM).
- **In-memory:** lazily-built singleton `OkHttpClient`; the decrypted key held only for the duration of a request.

## 4. Integration Points
- **AIToolbarView ↔ KeyboardService:** new `onAiAction` callback alongside the existing `onClipboardClick`. Removing it reverts the 5 buttons to no-ops; clipboard/tts/stt unaffected.
- **SettingsRepository/ViewModel:** additive keys; existing settings unaffected.
- **Manifest:** `INTERNET` added — used *only* by `OkHttpAiChatClient` on the live path.
- **Cross-epic:** shares `app/build.gradle.kts`/`libs.versions.toml` (additive, merge-clean) with E3/E4; shares `proguard-rules.pro` + toolbar + `strings.xml` with E4/E8 (merge E2 before E8). If the AI feature is removed, the keyboard + clipboard + settings all still function.

## 5. Security (`/vulnerability-scanner` at Phase 3.5 + 5.5)
- **Auth/secrets:** API key Keystore-encrypted at rest; masked in UI; never logged; transmitted only as a Bearer header to the user's own endpoint.
- **Egress control:** default-OFF master gate enforced in `AiEngine` before any client construction (unit-tested); AI actions blocked on password/secure fields.
- **Input validation (I2):** endpoint non-blank (https preferred), key present, text non-empty and length-capped; timeouts bound the request.
- **Error handling (I1):** all HTTP/network failures surfaced to the preview, never swallowed; original text never mutated on failure.
- **Transport:** HTTPS (user-configured endpoint); MITM risk owned by the user's endpoint choice, documented in settings copy.

## 6. Future Modernization Guide
- **v2 — local model + local-first routing:** add a second `AiChatClient` impl (on-device); `AiEngine` chooses local-first with external fallback. The interface seam means no caller changes.
- **Streaming:** OkHttp supports SSE; `AiPreviewView` could stream tokens — deferred.
- **Tech debt:** consider migrating `ClipboardDatabase`'s inline crypto onto `KeystoreSecretBox` once E2 ships and the encrypted-DB path can be re-tested safely. R8 keep rules to verify on the first release build (with E4). Presets may outgrow a single DataStore JSON blob → revisit Room then (post-E3 migrations).
- **Scaling at 10×:** the only network dependency is the user's endpoint; no app-side scaling concern. Watch payload size (length cap) and timeout tuning.
