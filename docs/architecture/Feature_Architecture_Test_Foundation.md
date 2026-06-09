# Feature Architecture — Test Suite Foundation (`test-foundation`)

> Companion to `docs/prd/PRD_Test_Foundation.md` and `docs/ard/ADR_Test_Foundation.md`. This is test *infrastructure*, so the usual "button-to-database flow" is reframed as "test-invocation-to-assertion flow."

## 1. Invocation-to-assertion flow

### JVM unit suite (`app/src/test/`)
```
./gradlew :app:testDebugUnitTest
  → Gradle/AGP unit-test task (runs on the host JVM, no device, Android stubbed)
    → JUnit4 discovers test classes
      → KeyboardStateTest        → new KeyboardState() → call methods → assert state (Truth)
      → ClipboardRepositoryTest  → new ClipboardRepository(FakeClipboardDao())
                                   → suspend calls under runTest → assert on fake's store / return
```
No Android framework, no Room, no SQLCipher in this set — `KeyboardState` is pure Kotlin and the repository talks only to the `ClipboardDao` interface, which the fake implements.

### Instrumented suite (`app/src/androidTest/`)
```
./gradlew connectedDebugAndroidTest  (AVD Medium_Phone_API_36.1 booted)
  → AndroidJUnitRunner instantiates SuperKeyboardApp (onCreate loads sqlcipher native lib)
    → @BeforeClass: System.loadLibrary("sqlcipher")   (defensive, idempotent)
    → @Before: reflect-reset ClipboardDatabase.instance; delete clipboard.db* + clipboard_passphrase
      → ClipboardDatabaseEncryptionTest
         ├─ at-rest:    create(ctx) → dao.insert(sentinel) → read raw bytes of clipboard.db*
         │              → assert sentinel (UTF-8 + UTF-16LE) absent in db/-wal/-shm
         ├─ round-trip: insert → query returns row → delete → gone
         └─ wrong-key:  open file w/ wrong passphrase via SupportOpenHelperFactory
                        → assert SQLiteException  (or @Ignore + logged skip)
```

## 2. Modular code & dependencies

- **New test-only modules:** `FakeClipboardDao` (reusable test double for any future repo test), `KeyboardStateTest`, `ClipboardRepositoryTest`, `ClipboardDatabaseEncryptionTest`.
- **New external deps (test scope only):** JUnit4, `kotlinx-coroutines-test`, Truth, `androidx.test` core/ext-junit/runner. Declared in `gradle/libs.versions.toml`, referenced via `libs.*`, wired as `testImplementation`/`androidTestImplementation` only — **zero new `implementation` deps**, so the shipped APK is unchanged.
- **Reused production surfaces (unchanged):** `ClipboardDao` interface (fake target), `ClipboardEntry` (incl. `DEFAULT_EXPIRY_MS`), `ClipboardDatabase.create()` (real encrypted path), `KeyboardState`.

## 3. Data architecture

- **JVM set:** no real persistence — `FakeClipboardDao` holds a `MutableList<ClipboardEntry>` + `MutableStateFlow` in memory.
- **Instrumented set:** the **real** SQLCipher-encrypted `clipboard.db` on the emulator's app-data dir, opened with the real Keystore-wrapped passphrase. Tests create and tear down this file per method (D5). No schema change, no migration (still `fallbackToDestructiveMigration` — untouched).

## 4. Integration points

- **Build pipeline:** new source sets + `testInstrumentationRunner` in `app/build.gradle.kts`. If removed, the suites can't run and the kit's 5th gate goes inert again.
- **Kit gate system:** activating the suite makes the Phase 5.4 Test-Suite Summary gate meaningful for every future epic. `docs/known-test-failures.md` / `docs/known-test-skips.md` become live baselines.
- **What breaks if removed:** nothing in the shipped app (test-scope only). The app's `assembleDebug`/`assembleRelease` paths are independent of the test source sets.

## 5. Security (`/vulnerability-scanner` lens)

- **Adds no network, no permission, no `implementation` dep.** Attack surface of the shipped APK unchanged.
- **Encryption-at-rest is asserted, not weakened.** The at-rest test is the regression guard for the core privacy promise.
- **Secret-handling discipline in tests:** never log the passphrase / Keystore material / decrypted text; the sentinel used in the at-rest test is a non-sensitive marker string. The wrong-key test supplies a throwaway wrong passphrase, never the real one.
- **Keystore alias `superkeyboard_clipboard_key` is not renamed/rotated** by any test.

## 6. Future modernization guide

- **Now:** JVM + instrumented foundations over 3 targets.
- **Next (deferred, see ADR consequences):** Robolectric or instrumented coverage for `Context`-coupled classes (`ClipboardManagerService`, `KeyboardService` auto-capitalize, custom-View rendering); CI wiring (GitHub Actions running the JVM set on push, instrumented on a managed device); real Room `Migration`s + a migration test (replacing `fallbackToDestructiveMigration`); Turbine + a `Clock` seam when Flow/time logic grows (D3/D4).
- **Scaling at 10×:** the `FakeClipboardDao` pattern generalizes to any future DAO; keep fakes hand-written and behavioural rather than reaching for a mocking framework.
