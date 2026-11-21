package com.lumo.hub.data

import android.content.Context

/**
 * Хранилище системной инструкции (SYSTEM.md, app-private). Правится только
 * базовая инструкция — блок протокола поиска ([ChatPrompts.SEARCH_PROTOCOL])
 * автодополняется в [SearchCycle] и в редакторе показывается read-only.
 * Отсутствующий или пустой файл = базовая инструкция по умолчанию.
 */
object SystemPromptStore {
    private const val FILE_NAME = "SYSTEM.md"

    /** Базовая инструкция: пользовательская из SYSTEM.md либо умолчание. */
    fun get(context: Context): String =
        runCatching { context.filesDir.resolve(FILE_NAME).takeIf { it.isFile }?.readText() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ChatPrompts.BASE_INSTRUCTION

    /** Сохранить базовую инструкцию в SYSTEM.md. */
    fun set(context: Context, text: String) {
        context.filesDir.resolve(FILE_NAME).writeText(text.trim())
    }

    /** Сброс к умолчанию: файл удаляется, [get] вернёт [ChatPrompts.BASE_INSTRUCTION]. */
    fun reset(context: Context) {
        context.filesDir.resolve(FILE_NAME).delete()
    }
}
