package com.lumo.hub.data

import com.lumo.hub.network.ChatCompletionRequest
import com.lumo.hub.network.ChatMessageRequest
import com.lumo.hub.network.OpenAiClient
import com.lumo.hub.network.OpenAiProvider

/**
 * B2: контекстизация чанков при импорте по мотивам Contextual Retrieval
 * (Anthropic, 2024): каждый фрагмент получает короткое пояснение о своём
 * месте в документе (50–100 токенов), что улучшает и поиск, и ответы модели.
 * Ошибка или таймаут любого запроса даёт null — чанк сохраняется обычным,
 * импорт не прерывается.
 */
class ImportContextualizer(
    private val openAiClient: OpenAiClient,
    private val provider: OpenAiProvider,
    private val model: String,
) {

    /** Контекст одного чанка (до ~100 токенов) или null при ошибке/пустом ответе. */
    suspend fun contextFor(documentName: String, page: Int, chunkText: String): String? = try {
        val completion = openAiClient.complete(
            provider,
            ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessageRequest("system", SYSTEM_PROMPT),
                    ChatMessageRequest(
                        "user",
                        "Документ: $documentName, страница: $page.\n\nФрагмент:\n$chunkText",
                    ),
                ),
                temperature = 0.2,
            ),
        )
        completion.content.trim().takeIf { it.isNotEmpty() }?.take(CONTEXT_CHARS)
    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private companion object {
        /** Верхний предел короткого контекста (~50–100 токенов). */
        const val CONTEXT_CHARS = 600

        const val SYSTEM_PROMPT =
            "Ты добавляешь контекст к фрагментам документа для поисковой системы. " +
                "Напиши одно-два коротких предложения (не более 600 символов): о чём этот " +
                "фрагмент в рамках документа (тема, раздел, назначение). Только контекст: " +
                "без вступлений, без повторения текста фрагмента, без форматирования."
    }
}
