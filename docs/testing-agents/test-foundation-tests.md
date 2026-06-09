# Test Plan — Test Suite Foundation (`test-foundation`)

> **Created:** 2026-06-09 (Phase 2.7, find-or-create — no existing agent overlapped; this is the first plan in the registry).
> **Adaptation note:** This project is a **native Android IME** — there is no dev server, DOM, or browser. Per CLAUDE.md, the testing-agent verifies via **Gradle unit/instrumented tests + manual on-device checks**, not browser automation. Scenarios below are native-shaped.

## Test Configuration

- `parallel_safe: false` — single emulator + one shared Gradle build/install; instrumented runs serialize. Run sequentially.
- `parallel_isolation: n/a`
- **Auth:** none (offline, no accounts).
- **Surfaces:** Gradle JVM unit suite, Gradle instrumented suite, app build + on-device sanity.
- **Devices — two distinct surfaces, do NOT conflate:**
  - **Instrumented Gradle tests** (`connectedDebugAndroidTest`, S2) run on the **emulator** AVD `Medium_Phone_API_36.1` (fallback `Pixel_9_Pro_Fold`; binary at `~/Library/Android/sdk/emulator/emulator`) — required because instrumented tests need a device and no physical one is attached over adb. Boot for S2, shut down after.
  - **Manual/on-device sanity (S4)** is done on the **physical phone** by sideloading the APK via Syncthing — **never the emulator**. Build → copy to `apk-releases/SuperKeyboard-v<version>-<descriptor>.apk` → Syncthing pushes → tap to update in place (`versionCode`+`versionName` must bump). See `docs/mobile/Android_Build_and_Sideload.md`.

## Scenarios

| # | Scenario | Steps | Expected | Expected duration |
|---|----------|-------|----------|-------------------|
| S1 | JVM unit suite green | `./gradlew :app:testDebugUnitTest --no-daemon` | All tests pass; 0 unexpected skips; verbatim summary captured | ~1–3 min (cold) |
| S2 | Instrumented suite green | Boot AVD; `./gradlew connectedDebugAndroidTest` | All pass; encryption-at-rest test confirms sentinel absent from `clipboard.db*`; round-trip passes | ~3–8 min (incl. boot+install) |
| S3 | App build not regressed | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL, APK produced | ~1–4 min |
| S4 | On-device sanity (physical phone, via APK→Syncthing — NOT emulator) | Build+deliver APK to `apk-releases/`; tap on phone to update; enable IME in Android Settings; type keys; copy text → check clipboard history | Keyboard renders & types; clipboard entry appears | ~3 min manual |
| S5 | RED-confidence spot check (optional, A7) | Temporarily break a tested production line (e.g. dedup `delete`); re-run S1; revert | A unit test goes RED, then green after revert | ~2 min |

## Verdict legend

PASS / FAIL / SKIP / BLOCKED_NEEDS_FIXTURE (e.g. no emulator can boot) / REQUIRES_INPUT.

## Miss Log

_Empty. Entries land here via `docs/prompts/testing-retro.md` when a bug escapes this plan's scope._
