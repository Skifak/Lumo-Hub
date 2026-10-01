package com.lumo.hub.network

import kotlinx.serialization.Serializable

data class OpenAiProvider(val baseUrl: String, val apiKey: String)
@Serializable data class ChatMessageRequest(val role: String, val content: String)
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRequest>,
    val temperature: Double? = null,
    val stream: Boolean = false,
)
data class ModelInfo(val id: String, val ownedBy: String? = null)
data class ChatCompletion(val id: String?, val model: String?, val content: String)
sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data class Completed(val finishReason: String?) : ChatStreamEvent
}

sealed class OpenAiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Http(val code: Int, val body: String) : OpenAiException("HTTP $code: $body")
    class Network(cause: Throwable) : OpenAiException("Network error", cause)
    class InvalidResponse(cause: Throwable) : OpenAiException("Invalid API response", cause)
}

@Serializable internal data class ModelsResponse(val data: List<ModelJson> = emptyList())
@Serializable internal data class ModelJson(val id: String, val owned_by: String? = null)
@Serializable internal data class CompletionJson(val id: String? = null, val model: String? = null, val choices: List<ChoiceJson> = emptyList())
@Serializable internal data class ChoiceJson(val message: MessageJson? = null, val delta: DeltaJson? = null, val finish_reason: String? = null)
@Serializable internal data class MessageJson(val content: String? = null)
@Serializable internal data class DeltaJson(val content: String? = null)
