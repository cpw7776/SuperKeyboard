# Test Plan — AI Action Engine (`E2-ai-action-engine`)

> **Created:** 2026-06-09 (Phase 2.7, find-or-create — `test-foundation-tests.md` covers suite/build health only, not this feature's behavior; no scope overlap, so this is a new agent).
> **Adaptation note:** Native Android IME — verification is Gradle unit/instrumented tests + manual on-device checks, not browser automation. The async surface here is a **network** call to a user-configured OpenAI-compatible endpoint.

## Test Configuration
- `parallel_safe: false` — one physical test phone + one installed IME (same `applicationId`); manual passes serialize. JVM suite can run anywhere.
- `parallel_isolation: n/a`
- **Auth:** none in-app. The live-endpoint scenarios need the user's own endpoint URL + API key + model (Venice or any OpenAI-compatible). Treat the key as a fixture the human supplies on-device.
- **Surfaces:** Gradle JVM unit suite (engine logic + crypto + egress guard), app build, manual on-device (settings + 5 actions + privacy guarantees).
- **Devices:** manual on the **physical phone** via APK→`apk-releases/`→Syncthing (NOT emulator). Optional instrumented key-encryption test on the emulator AVD `Medium_Phone_API_36.1`.

## Scenarios

| # | Scenario | Steps | Expected | Expected duration |
|---|----------|-------|----------|-------------------|
| S1 | JVM unit suite green | `./gradlew :app:testDebugUnitTest --no-daemon` | All pass incl. AiEngine + KeystoreSecretBox + prompt + text-targeting tests; verbatim summary captured | ~1–3 min |
| S2 | **Egress guard (privacy)** | Inspect `AiEngineTest`: toggle OFF ⇒ injected `FakeAiChatClient.callCount == 0`, result `Disabled` | Test present and green — no client invocation when disabled | within S1 |
| S3 | App build not regressed | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL, APK produced | ~1–4 min |
| S4 | Settings persist + key masked (on-device) | Settings→AI: enable, enter endpoint+key+model+language; restart app | Values persisted; key field never shows stored plaintext | ~3 min manual |
| S5 | 5 actions end-to-end (on-device, live endpoint) | Select text; tap Rewrite/Summarize/Expand/Translate/Preset; Apply / Cancel | Preview loads; Apply replaces selection; Cancel restores; Translate honors target language; Preset uses custom prompt | ~6 min manual |
| S6 | Whole-field (no selection) | No selection; tap Summarize | Operates on whole field; Apply replaces it | ~1 min manual |
| S7 | **Disabled-state = no egress** | Toggle AI off (optionally airplane mode); tap an action | "Set up AI" hint; NO network request; text untouched | ~1 min manual |
| S8 | **Password field excluded** | Focus a password field; observe toolbar | AI actions unavailable/disabled; engine never receives field text | ~1 min manual |
| S9 | Error paths | Wrong key (401) / airplane mode / over-long input | Preview shows a clear error; original text untouched; no crash | ~2 min manual |

## Verdict legend
PASS / FAIL / SKIP / BLOCKED_NEEDS_FIXTURE (e.g. no endpoint/key supplied for S5) / REQUIRES_INPUT.

## Miss Log
_Empty. Entries land here via `docs/prompts/testing-retro.md` when a bug escapes this plan's scope._
