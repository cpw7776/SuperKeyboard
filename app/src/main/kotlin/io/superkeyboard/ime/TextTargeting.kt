package io.superkeyboard.ime

/**
 * Pure text-targeting logic for AI actions — NO Android types, so it is JVM-unit-testable.
 *
 * The IME calls [resolveTarget] with the current selection (from `ic.getSelectedText(0)`) and the
 * whole-field text (assembled from before/after-cursor or `getExtractedText`). The rule: act on the
 * selection when there is one, otherwise the whole field.
 */
data class TextTarget(val text: String, val hadSelection: Boolean)

/**
 * Resolve which text an AI action should operate on.
 *
 * @return [selectedText] (with `hadSelection = true`) when it is non-null and non-empty; otherwise
 *   [fullText] (with `hadSelection = false`). Empty everything yields empty text, no selection.
 */
fun resolveTarget(selectedText: CharSequence?, fullText: CharSequence): TextTarget {
    val selection = selectedText?.toString()
    return if (!selection.isNullOrEmpty()) {
        TextTarget(text = selection, hadSelection = true)
    } else {
        TextTarget(text = fullText.toString(), hadSelection = false)
    }
}
