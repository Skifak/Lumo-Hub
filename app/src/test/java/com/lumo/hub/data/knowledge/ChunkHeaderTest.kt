package com.lumo.hub.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

/** Шапки чанков (B1) и контекст чанка (B2) в индексируемом тексте. */
class ChunkHeaderTest {

    @Test
    fun chunkTextWithoutContext() {
        assertEquals(
            "[Документ «Паспорт», стр. 3]\n\nТекст.",
            KnowledgeRepository.chunkText("Паспорт", 3, "Текст.", null),
        )
    }

    @Test
    fun chunkTextWithContext() {
        assertEquals(
            "[Документ «Паспорт», стр. 3]\nКонтекст: Описание прибора\n\nТекст.",
            KnowledgeRepository.chunkText("Паспорт", 3, "Текст.", "Описание прибора"),
        )
    }

    @Test
    fun contextNewlinesAreFlattened() {
        assertEquals(
            "[Документ «Паспорт», стр. 1]\nКонтекст: Первая строка Вторая строка\n\nТекст.",
            KnowledgeRepository.chunkText("Паспорт", 1, "Текст.", "Первая строка\nВторая строка"),
        )
    }

    @Test
    fun stripPreambleRemovesHeaderAndContext() {
        val chunk = KnowledgeRepository.chunkText("Паспорт", 3, "Текст.", "Описание прибора")
        assertEquals("Текст.", KnowledgeRepository.stripPreamble(chunk))
    }

    @Test
    fun stripPreambleKeepsPlainText() {
        assertEquals("Просто текст", KnowledgeRepository.stripPreamble("Просто текст"))
    }

    @Test
    fun reheaderRewritesNameKeepingContext() {
        val chunk = KnowledgeRepository.chunkText("Старое имя", 2, "Текст.", "Описание прибора")
        assertEquals(
            "[Документ «Новое имя», стр. 2]\nКонтекст: Описание прибора\n\nТекст.",
            KnowledgeRepository.reheader(chunk, "Новое имя", 2),
        )
    }

    @Test
    fun reheaderAddsHeaderToPlainBody() {
        assertEquals(
            "[Документ «Новое имя», стр. 1]\n\nТекст.",
            KnowledgeRepository.reheader("Текст.", "Новое имя", 1),
        )
    }
}
