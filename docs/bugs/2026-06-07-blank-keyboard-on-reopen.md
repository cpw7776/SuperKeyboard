# Bug: Keyboard blank (no keys, dead touches) on reopen / layout change

- **Date:** 2026-06-07
- **Reported by:** on-device testing (GrapheneOS, Personal profile), first sideload of v0.1.1
- **Severity:** P0 — keyboard unusable after first dismissal
- **Status:** Fixed (commit pending) — shipped in v0.1.2
- **Affected files:** `app/src/main/kotlin/io/superkeyboard/ime/KeyboardView.kt`

## Symptom

The keyboard rendered correctly the first time it appeared, but on reopening (after the
input view was dismissed and shown again) the key area was blank — only the AI toolbar row
was visible. No keys drew, and no keys responded to touch (only the clipboard toolbar button,
which lives in the separate toolbar view, still worked). Two layout-polish issues were noticed
in the same session (dead space above the keys; toolbar icons clustered left).

## Root cause

`KeyboardView` derives each key's hit/draw rectangle (`Key.bounds`) in `layoutKeys()`, which was
**only** invoked from `onSizeChanged()`.

`KeyboardLayout.getLayout()` returns **freshly-constructed `Key` objects** (each with an empty
default `bounds = RectF()`) on every call. The `keyboardState` setter calls `rebuildLayout()`,
which swaps in those new keys and calls `invalidate()`.

On the **first** appearance the view is freshly attached and measured, so `onSizeChanged()` fires
once and `layoutKeys()` assigns bounds — keys draw fine. But when the input view is **reused at the
same size** (Android caches the IME input view across show/hide; also when switching layout pages
mid-session), `onSizeChanged()` does **not** fire. The new `Key` objects therefore keep their empty
bounds, so:

- `onDraw()` → `drawKey()` early-returns on `if (bounds.isEmpty) return` for every key → blank.
- `onTouchEvent()` → `flatKeys.find { it.contains(x, y) }` never matches (empty rect contains
  nothing) → dead keys.

This is a **state-rebuild / layout-derivation desync**: model rebuilt, layout-derived state not
recomputed because the only recompute trigger (`onSizeChanged`) is size-gated.

## Fix

`rebuildLayout()` now recomputes key bounds immediately whenever the view already has a non-zero
size, instead of relying solely on `onSizeChanged()`:

```kotlin
private fun rebuildLayout() {
    keys = KeyboardLayout.getLayout(keyboardState.layoutPage)
    flatKeys = keys.flatten()
    if (width > 0 && height > 0) {
        layoutKeys(width.toFloat(), height.toFloat())
    }
}
```

The `width == 0` first-build case is still handled by the existing `onSizeChanged` → `layoutKeys`
path. This also fixes the (latent) blanking that would have occurred on every symbols/emoji layout
switch.

## Layout polish shipped alongside (same session)

- **Dead space above keys:** `KeyboardView` was reserving an *internal* `theme.toolbarHeight`
  strip (in `onMeasure`/`layoutKeys`/`onDraw`) on top of the separate `AIToolbarView` sibling.
  Removed — keys now sit flush; the keyboard is pushed up. Touch Y mapping stays correct because
  the measured height and the key bounds both lost the strip together.
- **Toolbar icons clustered left:** `AIToolbarView` was a `HorizontalScrollView` with wrap-content
  buttons. Changed to a horizontal `LinearLayout` with equal-weight buttons (`width = 0, weight = 1`)
  so icons fill the line and shrink evenly as more actions are added.

## Why no test caught it

No unit/instrumented test source set exists yet (tracked P0 in `PRODUCTION_READY.md`). This fault
class is hard to unit-test (it needs view attach/measure lifecycle) but is a strong instrumented-test
candidate once the test surface exists. Captured as a Gap C lesson in `Implementation_Patterns.md`.
