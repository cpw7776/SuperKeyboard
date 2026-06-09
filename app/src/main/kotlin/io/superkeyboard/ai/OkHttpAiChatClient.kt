package io.superkeyboard.ai

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [AiChatClient] (ADR D1/D7). Builds a single, reused [OkHttpClient] lazily — only the
 * first time a live request is actually made — so when AI is disabled (the engine short-circuits
 * first) no client is ever constructed and no socket is opened (privacy invariant 1 / ADR D3).
 *
 * Never logs the API key or the request/response bodies (privacy invariant 3). Failures map to
 * [AiResult] variants rather than throwing (I1). Connect/read/write timeouts are bounded at ~30s
 * (I7 / ADR D7) so a wedged endpoint can't block the IME indefinitely.
 */
class OkHttpAiChatClient : AiChatClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun complete(
        endpointUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): AiResult {
        // Belt-and-braces boundary validation (I2); the engine gates these first.
        if (endpointUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return AiResult.NetworkError("AI is not fully configured")
        }

        // Enforce HTTPS at the boundary (ADR D7 / threat model: MITM mitigated by HTTPS). The app
        // sets no cleartext policy and minSdk 24 permits cleartext by platform default, so without
        // this guard a typo'd/misconfigured http:// endpoint would transmit the Bearer API key and
        // the user's field contents in plaintext. Surface it (I1), never silently downgrade.
        if (!isHttps(endpointUrl)) {
            return AiResult.NetworkError("Endpoint must use https://")
        }

        val url = resolveUrl(endpointUrl)
        val requestJson = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model, messages = messages)
        )

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", JSON_MEDIA_TYPE)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Do NOT include the request (it carries field contents); a bounded body snippet
                    // from the server's own error response is safe and useful for the user.
                    return@use AiResult.HttpError(response.code, body.take(ERROR_SNIPPET_CHARS))
                }
                val parsed = json.decodeFromString(ChatResponse.serializer(), body)
                val text = parsed.choices.firstOrNull()?.message?.content
                if (text.isNullOrBlank()) {
                    AiResult.HttpError(response.code, "Empty response from endpoint")
                } else {
                    AiResult.Success(text)
                }
            }
        } catch (e: IOException) {
            // Timeouts and connection failures (no network, DNS, TLS). Message only — never the body.
            AiResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            // Malformed/unexpected response body that failed to parse. Surface, don't swallow (I1).
            AiResult.NetworkError("Unexpected response from endpoint")
        }
    }

    /**
     * True only if [endpointUrl] is an `https://` URL (case-insensitive, leading/trailing space
     * tolerated). Plain `http://` and any other scheme are rejected so secrets are never sent in
     * cleartext (ADR D7).
     */
    private fun isHttps(endpointUrl: String): Boolean =
        endpointUrl.trim().startsWith("https://", ignoreCase = true)

    /**
     * Resolve the user's endpoint into a full chat-completions URL: if it already ends with
     * `/chat/completions` use it as-is, else append it (tolerating a single trailing slash).
     */
    private fun resolveUrl(endpointUrl: String): String {
        val trimmed = endpointUrl.trim().trimEnd('/')
        return if (trimmed.endsWith(CHAT_COMPLETIONS_PATH)) trimmed
        else "$trimmed$CHAT_COMPLETIONS_PATH"
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val JSON_MEDIA_TYPE = "application/json"
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val ERROR_SNIPPET_CHARS = 300
    }
}
