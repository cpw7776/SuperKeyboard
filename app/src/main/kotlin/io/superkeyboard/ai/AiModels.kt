package io.superkeyboard.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat-completions DTOs (ADR D1). kotlinx.serialization, compile-time / reflection
 * -free so R8 needs only the keep rule added in Batch A. The Json instance that uses these must set
 * `ignoreUnknownKeys = true` (providers add fields like `usage`, `id`, `created`) — see
 * [OkHttpAiChatClient].
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    @SerialName("message") val message: ChatMessage
)
