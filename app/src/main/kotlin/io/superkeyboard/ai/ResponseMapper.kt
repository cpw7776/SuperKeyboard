package io.superkeyboard.ai

import kotlinx.serialization.json.Json

/**
 * Pure, Android/OkHttp-free mapping from an HTTP chat-completions response to an [AiResult]. Extracted
 * from [OkHttpAiChatClient] so every error path is JVM-unit-testable without standing up a live server
 * (see ResponseMappingTest). This is the fix for the Phase-4 bug where a misconfigured endpoint
 * (e.g. a base URL that returns a 200 HTML login page) was mapped to a bare, opaque "Unexpected
 * response from endpoint" with no HTTP status and no body — undiagnosable.
 *
 * Privacy invariant 3: the request body and the API key are NEVER reflected here — only the server's
 * own response [body] is snippetted back to the user, and only a bounded slice of it.
 */
object ResponseMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Map a completed HTTP response to an [AiResult].
     *
     *  - non-2xx                         -> [AiResult.HttpError] (code + bounded body snippet)
     *  - 2xx + parseable, non-blank text -> [AiResult.Success]
     *  - 2xx + parseable but empty/blank -> [AiResult.HttpError] (carries the code so it's diagnosable)
     *  - 2xx + unparseable body          -> [AiResult.NetworkError] with the code AND a body snippet,
     *    so the user can SEE it's HTML / the wrong URL — the key diagnosability fix (I1).
     */
    fun mapHttpResponse(isSuccessful: Boolean, code: Int, body: String): AiResult {
        if (!isSuccessful) {
            // The server's own error response is safe to reflect (never the request); bound it.
            return AiResult.HttpError(code, body.take(ERROR_SNIPPET_CHARS))
        }

        val parsed = try {
            json.decodeFromString(ChatResponse.serializer(), body)
        } catch (_: Exception) {
            // Body that doesn't parse as a ChatResponse (HTML page, plain text, garbage). Surface the
            // HTTP code AND a bounded snippet of the server's body so the misconfiguration is visible.
            return AiResult.NetworkError(
                "Unexpected response (HTTP $code): ${body.take(BODY_SNIPPET_CHARS)}"
            )
        }

        val text = parsed.choices.firstOrNull()?.message?.content
        return if (text.isNullOrBlank()) {
            // Parsed fine but no usable content — keep the code so the user knows it was a 2xx reply.
            AiResult.HttpError(code, "Empty response from endpoint")
        } else {
            AiResult.Success(text)
        }
    }

    private const val ERROR_SNIPPET_CHARS = 300
    private const val BODY_SNIPPET_CHARS = 200
}
