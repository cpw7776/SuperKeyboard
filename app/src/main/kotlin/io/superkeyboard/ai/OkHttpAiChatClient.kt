package io.superkeyboard.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        // The OkHttp call is BLOCKING; run it off the main thread inside the client so EVERY caller is
        // main-safe (IME preview on aiScope=Main, settings Test Connection on viewModelScope=Main).
        // Without this, execute() on Main throws NetworkOnMainThreadException — which was first swallowed
        // by a generic catch and later left uncaught (it is a RuntimeException, not IOException) and
        // crashed the Settings app. complete() is suspend, so it resumes on the caller's dispatcher
        // (Main) and UI updates stay safe.
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    // All response→AiResult mapping lives in the pure ResponseMapper so the diagnosability
                    // paths (HTTP code + bounded body snippet) are JVM-unit-testable without a live server.
                    ResponseMapper.mapHttpResponse(
                        isSuccessful = response.isSuccessful,
                        code = response.code,
                        body = response.body?.string().orEmpty()
                    )
                }
            } catch (e: IOException) {
                // Timeouts and connection failures (no network, DNS, TLS). Message only — never the body.
                AiResult.NetworkError(e.message ?: "Network error")
            } catch (e: Exception) {
                // Defense-in-depth (I1): any unexpected throwable becomes a visible error message instead
                // of crashing the app / silently mislabeling. Message/class name only — never the request
                // body or API key.
                AiResult.NetworkError("Request failed: ${e.message ?: e.javaClass.simpleName}")
            }
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
    }
}
