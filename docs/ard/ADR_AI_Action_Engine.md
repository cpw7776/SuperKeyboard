# ADR — AI Action Engine (`E2-ai-action-engine`)

> **Status:** Accepted (2026-06-09), implementation in progress. **Context:** wires the 5 dead AI toolbar actions to a real engine; the app's **first network egress** (P1 #7). **Companions:** PRD `docs/prd/PRD_AI_Action_Engine.md`, Feature Architecture `docs/architecture/Feature_Architecture_AI_Action_Engine.md`, plan snapshot `docs/plans/2026-06-09-ai-action-engine.md`.

## Context

`toolbar/AIToolbarView.kt` ships five text actions — translate, rewrite, summarize, expand, presets — as `/* Phase 2 */` no-ops. This epic gives them a real engine that calls a **user-configured, OpenAI-compatible** `/chat/completions` endpoint (the user's reference provider is Venice). v1 ships the **external** path only; an on-device local model + local-first routing is a deliberate later epic (v2).

> **Privacy reconciliation (THE reason this ADR is mandatory).** SuperKeyboard's product promise is "completely private — nothing leaves the device." This epic introduces the first deliberate exception. It is reconciled with the promise by four hard invariants, all of which are testable and audited later by E6:
> 1. **Opt-in, default-OFF.** A master `ai_enabled` flag defaults to `false`. While false, no HTTP client is constructed and no socket is opened — enforced in `AiEngine`, asserted by a unit test (the client is never invoked when disabled).
> 2. **User-owned endpoint only.** The endpoint URL + API key + model are user-supplied. There is **no hard-coded third-party cloud**; with no configuration, the engine is `Unconfigured` and inert.
> 3. **No secret/plaintext logging.** The API key, the request body, and field contents are never logged. The key is Keystore-encrypted at rest.
> 4. **No egress of secrets.** AI actions are **disabled on password / secure input types** (`EditorInfo`), so the engine never transmits field contents the user marked secret.
>
> Adding `INTERNET` to the manifest grants the *OS capability*; the four invariants above are what keep the *promise*. The user-facing `about_privacy` / `about_description` strings are revised in this epic to state the egress is opt-in and to the user's own endpoint.

## Decisions

### D1 — Networking + JSON: OkHttp + kotlinx.serialization
- **Chosen:** `com.squareup.okhttp3:okhttp` for the HTTP call; `org.jetbrains.kotlinx:kotlinx-serialization-json` (+ the serialization Gradle plugin) for request/response DTOs.
- **Rejected — `HttpURLConnection` + `org.json` (zero new deps):** smallest supply chain, but manual timeout/TLS/error handling is easy to get subtly wrong; more boilerplate for no durable gain.
- **Rejected — Ktor client:** heavier transitive graph than OkHttp for a single POST.
- **Why:** OkHttp gives correct timeouts, TLS, and connection pooling out of the box and is trivially faked behind an interface; kotlinx.serialization is **compile-time / reflection-free**, so R8/minification (E4) needs only a minimal keep rule. Both are FOSS and standard in F-Droid apps.

### D2 — API key storage: Android-Keystore-encrypted file (mirror clipboard)
- **Chosen:** a reusable `util/KeystoreSecretBox` (AES-256-GCM, `[ivLen][iv][ciphertext]` format) mirroring the inline pattern in `ClipboardDatabase.kt`; `ai/AiKeyStore` wraps it with a **new** Keystore alias `superkeyboard_ai_key` and app-private file `ai_api_key.enc`.
- **Rejected — EncryptedSharedPreferences:** adds `androidx.security:security-crypto` (not currently present) for no gain over the existing pattern.
- **Rejected — plaintext DataStore:** weaker than the rest of the app; an API key is a credential.
- **Why:** consistency with the app's posture at zero new dependency. **Hard constraint:** never reuse `superkeyboard_clipboard_key`, and **do not refactor `ClipboardDatabase`** onto the shared box in this epic — touching the encrypted-DB path risks orphaning user data. The non-secret AI settings (`ai_enabled`, endpoint, model, target language, presets) live in DataStore; only the key is Keystore-wrapped.

### D3 — Toggle-OFF egress guarantee: engine-level gate + lazy client
- **Chosen:** `AiEngine.run(...)` returns a sealed `Disabled` / `Unconfigured` result *before* any network work when the flag is off or endpoint/key is missing; the `OkHttpClient` is built lazily, only on the live path, and reused thereafter.
- **Rejected — UI-only gate (hide buttons):** not a real guarantee (bypassable, not testable as egress).
- **Why:** the privacy invariant must live where it can be unit-tested. A test injects a recording `FakeAiChatClient` and asserts zero invocations when disabled. UI hinting ("set up AI in Settings") is added *on top* for UX, not as the guarantee.

### D4 — Output UX: in-keyboard preview-then-apply panel
- **Chosen:** `ime/AiPreviewView` (extends `LinearLayout`, owns a `CoroutineScope(Main)` + `destroy()`), swapped into `rootLayout` via the existing `removeAllViews()/addView()` + `showKeyboard()` overlay pattern — mirroring `ClipboardBottomSheet`. Shows loading → result with **Apply / Cancel**; Cancel cancels the request coroutine and restores the keyboard without mutating text.
- **Rejected — replace-in-place:** a bad/unexpected response would destroy the user's text with no undo.
- **Rejected — dialog/Activity:** breaks the IME interaction model.
- **Why:** non-destructive by default, and reuses a proven overlay pattern.

### D5 — Presets storage: DataStore JSON (no Room table)
- **Chosen:** presets (name + prompt template) serialized as a kotlinx.serialization JSON list under one DataStore key.
- **Rejected — Room table:** needs a schema/migration for a small user-edited list, and would collide with E3's migration lane.
- **Why:** simple CRUD, no migration, keeps the DB lane clean.

### D6 — Action model: typed enum + prompt templates (not stringly-typed)
- **Chosen:** `ai/AiAction` enum (`TRANSLATE`, `REWRITE`, `SUMMARIZE`, `EXPAND`, `PRESET`) carrying its system-prompt template; `TRANSLATE` reads the target language from settings; `PRESET` carries the user's custom prompt.
- **Why:** avoids I5 (stringly-typed across boundaries); the toolbar passes an enum, not a string, into the engine.

### D7 — Input bounds + error surfacing
- **Chosen:** validate at the boundary (I2) — non-blank endpoint (https preferred), present key, non-empty text; cap input length (~8k chars) with user feedback; explicit OkHttp connect/read/write timeouts (~30s). All failures map to a visible message in the preview (I1) — never swallowed, never silent.
- **Why:** network is user-blocking and untrusted; bounded, observable, cancellable.

## Consequences
- **Positive:** the 5 AI actions become real against any OpenAI-compatible endpoint; a reusable `KeystoreSecretBox` + `AiChatClient` seam exist for v2 (local model) to plug into; privacy invariants are codified and test-protected.
- **Negative / debt:** first `INTERNET` permission (permanent, OS-visible; affects future F-Droid anti-feature metadata); a new Keystore alias to never rotate casually; R8 keep rules to verify on a release build (coordinate with E4); v2 local-first routing deferred.
- **Cross-epic:** E4 must not add the serialization keep rules (E2 owns them); E6 privacy audit and the F-Droid packaging epic both run after this merges; E8 rebases on the toolbar changes here.

## Data flow / threats touched
New outbound flow: toolbar tap → `AiEngine` (gate + validate) → `OkHttpAiChatClient` → user's HTTPS endpoint → response rendered in `AiPreviewView` → on Apply, `InputConnection` replaces text. Threats: secret exfiltration (mitigated by default-OFF, password-field block, no-logging), MITM (HTTPS + user-owned endpoint), unbounded payload (length cap + timeouts). `/vulnerability-scanner` runs over the diff in the Phase 3.5 and Phase 5.5 gates.
