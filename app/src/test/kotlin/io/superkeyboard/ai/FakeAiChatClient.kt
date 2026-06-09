package io.superkeyboard.ai

/**
 * Hand-written fake of [AiChatClient] for JVM unit tests (anti-pattern A2: fake at the real network
 * seam the engine talks to, no mocking framework). It records invocations and the last arguments so
 * the egress-guard test can assert [callCount] == 0, and lets each test configure the [AiResult] it
 * returns so error paths (HttpError / NetworkError) are exercised end-to-end (A3).
 *
 * It does NOT decide the engine's result — it only stands in for the network. The engine's mapping
 * of [AiResult] -> EngineResult is what the tests assert (A4: assert behaviour the fake can't fake).
 */
class FakeAiChatClient(
    var result: AiResult = AiResult.Success("FAKE")
) : AiChatClient {

    var callCount = 0
        private set

    var lastEndpointUrl: String? = null
        private set
    var lastApiKey: String? = null
        private set
    var lastModel: String? = null
        private set
    var lastMessages: List<ChatMessage>? = null
        private set

    override suspend fun complete(
        endpointUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): AiResult {
        callCount++
        lastEndpointUrl = endpointUrl
        lastApiKey = apiKey
        lastModel = model
        lastMessages = messages
        return result
    }
}
