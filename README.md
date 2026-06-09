# SuperKeyboard
This is the most helpful keyboard and completely private

## Development

### Building

```
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:compileDebugKotlin     # compile-only check
```

### Running tests

```
./gradlew :app:testDebugUnitTest --no-daemon   # JVM unit suite (24 tests)
./gradlew connectedDebugAndroidTest            # instrumented suite — requires a booted AVD or connected device (3 tests)
```

The instrumented suite (`connectedDebugAndroidTest`) verifies clipboard encryption-at-rest using a real SQLCipher + Android Keystore stack on the emulator. See `docs/prd/PRD_Test_Foundation.md` for details.
