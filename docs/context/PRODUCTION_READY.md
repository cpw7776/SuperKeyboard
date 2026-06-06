# SuperKeyboard — Production Ready

> **Goal:** Ship a privacy-first Android keyboard to the Play Store. Target date: TBD (early development).
> This document consolidates all required work before a Play Store (or sideload-beta) release.
> **Last Updated:** 2026-06-06

---

## Release Criteria

| # | Requirement | Epic/Ticket | Priority | Status |
|---|-------------|-------------|----------|--------|
| 1 | Core typing reliable across input types (text, password, email, numeric) | — | P0 | In progress |
| 2 | Encrypted clipboard correct: pin/expiry/clear semantics, no plaintext leakage | — | P0 | Shipped (Phase 1), needs test coverage |
| 3 | Real Room migrations (remove `fallbackToDestructiveMigration`) | — | P0 | Not started |
| 4 | Release signing config + ProGuard/R8 rules validated | — | P0 | Not started |
| 5 | Automated test coverage (unit + instrumented) for clipboard, settings, key logic | — | P0 | Not started — no test source set exists |
| 6 | Privacy audit: confirm no network egress, no plaintext logging of sensitive data | — | P0 | Not started |
| 7 | AI toolbar actions wired to on-device / user-endpoint engines | — | P1 | Scaffolded only |
| 8 | Accessibility pass (TalkBack, key labels, contrast) | — | P1 | Not started |
| 9 | Play Store listing, privacy policy, data-safety form | — | P1 | Not started |

**Priority guide:** P0 blocks release · P1 should ship · P2 post-launch.

---

## P0 — Critical Blockers

### Real database migrations
- **What:** `ClipboardDatabase` uses `fallbackToDestructiveMigration()`.
- **Why:** Every schema bump wipes user clipboard history — unacceptable for a shipped app.
- **Current state:** Destructive fallback in place (schema v1).
- **What's needed:** Replace with explicit `Migration` objects before any schema change ships.
- **Status:** Open.

### Automated tests
- **What:** No `app/src/test/` or `app/src/androidTest/` source set exists.
- **Why:** The kit's lifecycle is TDD-driven; clipboard encryption and key/pin/expiry logic are high-risk and untested.
- **What's needed:** Stand up JVM unit tests (Robolectric or pure-Kotlin where possible) and instrumented tests for the SQLCipher path and IME behavior.
- **Status:** Open (tracked in `docs/KIT_DEVIATIONS.md`).

### Release signing + ProGuard
- **What:** Release build enables minify/shrink but signing config and verified ProGuard rules are not set up.
- **What's needed:** Keystore + signing config, verified `proguard-rules.pro` keep rules (Room, SQLCipher), tested release APK.
- **Status:** Open.

---

## Known Bugs

| # | Bug | Severity | Status | Report |
|---|-----|----------|--------|--------|
| — | None tracked yet | — | — | — |

(Several SQLCipher integration issues were fixed during Phase 1 — see CHANGELOG / git history commits `5204f34`–`feba9d3`.)

---

## Pre-Launch Checklist

- [ ] All P0 requirements met
- [ ] Unit + instrumented tests passing (`./gradlew test connectedDebugAndroidTest`)
- [ ] Release build succeeds (`./gradlew :app:assembleRelease`) with zero errors
- [ ] Real Room migrations in place (no destructive fallback)
- [ ] Release signing configured; APK installs & runs
- [ ] Privacy audit: no `INTERNET` permission, no network egress, no plaintext logging of clipboard/keys
- [ ] `allowBackup="false"` confirmed; clipboard data excluded from backups
- [ ] Accessibility pass (TalkBack)
- [ ] Play Store listing + privacy policy + data-safety form complete
- [ ] In-app About/Settings copy matches actual behavior
