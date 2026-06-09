# Known Test Skips

> Baseline skipped-test count + reasons. Phase 4.2 of `feature-lifecycle.md` uses this to detect skip-count drift (silently skipping a failing test to make the suite "pass").

> **Baseline established 2026-06-09** (`test-foundation` epic): the first test source sets exist. JVM suite (`KeyboardStateTest` 15 + `ClipboardRepositoryTest` 9 = 24 tests, 0 skipped). Instrumented suite (`ClipboardDatabaseEncryptionTest` = 3 tests, 0 skipped — the wrong-key negative test passed cleanly, so it was NOT `@Ignore`d). Baseline skip count: **0**.

**Baseline skip count:** 0

| Date | Test | Reason skipped | Approved by |
|------|------|----------------|-------------|
| — | — | — | — |
