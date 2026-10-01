package com.lumo.hub.ui.components

/**
 * Черновики ввода по чатам — только в памяти процесса (UI-слой).
 *
 * Слой данных (`data/`) намеренно не трогается: сохранение черновиков на диск
 * потребовало бы расширения [com.lumo.hub.data.ChatRepository]. В рамках сессии
 * приложения черновик живёт, после перезапуска процесса — сбрасывается.
 */
object ChatDraftStore {
    private val drafts = LinkedHashMap<String, String>()

    fun get(chatId: String): String? = drafts[chatId]

    fun set(chatId: String, value: String) {
        if (value.isEmpty()) drafts.remove(chatId) else drafts[chatId] = value
    }

    fun clear(chatId: String) = drafts.remove(chatId)
}
