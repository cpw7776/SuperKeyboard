package io.superkeyboard.ai

/**
 * The network seam for the AI engine (ADR D1/D3). Pure Kotlin interface — no Android types — so
 * [AiEngine] depends on this abstraction and can be unit-tested on the JVM against a hand-written
 * fake. The real implementation is [OkHttpAiChatClient]; tests use FakeAiChatClient.
 *
 * Implementations MUST NOT log the [apiKey] or the request/response bodies (privacy invariant 3).
 */
interface AiChatClient {
    /**
     * POST a chat-completions request to the user's OpenAI-compatible [endpointUrl] and return the
     * result. Never throws for an HTTP or network failure — those map to [AiResult] variants so the
     * caller surfaces them (I1: surface errors, don't swallow).
     */
    suspend fun complete(
        endpointUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): AiResult
}

/** Result of a single [AiChatClient.complete] call. */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class HttpError(val code: Int, val message: String) : AiResult
    data class NetworkError(val message: String) : AiResult
}
