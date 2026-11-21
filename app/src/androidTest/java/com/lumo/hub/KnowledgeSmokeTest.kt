package com.lumo.hub

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumo.hub.data.SearchCycle
import com.lumo.hub.data.knowledge.KnowledgeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Смоук-тест слоя знаний: импорт TXT → кириллический FTS-поиск → маркеры
 * страниц → разбор протокола SEARCH/READ → чувствительные документы.
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = KnowledgeRepository.get(context)

    @After
    fun cleanup() {
        runBlocking {
            repository.documents().forEach { repository.deleteDocument(it.id) }
        }
    }

    @Test
    fun importSearchAndProtocolSmoke() {
        val file = File(context.cacheDir, "тест-паспорт.txt")
        file.writeText(
            """
            Паспорт прибора Lumo.

            Заземление корпуса обязательно. Провод заземления подключается к клемме PE.
            """.trimIndent(),
        )
        val uri = Uri.fromFile(file)

        // Импорт: TXT → чанки с шапкой B1 и номером страницы.
        val result = runBlocking { repository.import(uri) }
        assertEquals(1, result.pageCount)
        assertTrue(result.chunkCount > 0)

        // Кириллический FTS-поиск: фрагмент приходит с шапкой и страницей.
        val hits = runBlocking { repository.query("заземление") }
        assertTrue(hits.isNotEmpty())
        val hit = hits.first()
        assertEquals("тест-паспорт.txt", hit.docName)
        assertEquals(1, hit.page)
        assertTrue(hit.text.startsWith("[Документ «тест-паспорт.txt», стр. 1]"))

        // READ: страница целиком — без шапки, текст склеен.
        val page = runBlocking { repository.pageText("тест-паспорт.txt", 1) }
        assertNotNull(page)
        assertFalse(page!!.startsWith("[Документ"))
        assertTrue(page.contains("Заземление корпуса обязательно"))

        // Фильтр doc: из протокола ограничивает выдачу документом.
        val filtered = runBlocking { repository.query("заземление", docFilter = "паспорт") }
        assertTrue(filtered.isNotEmpty())
        val wrongFilter = runBlocking {
            repository.query("заземление", docFilter = "несуществующий")
        }
        assertTrue(wrongFilter.isEmpty())

        // Разбор протокола: строка ответа модели → аргументы команды.
        val cmd = SearchCycle.SEARCH_CMD.matchEntire("SEARCH: заземление корпуса")
        assertNotNull(cmd)
        val args = SearchCycle.parseSearchArguments(cmd!!.groupValues[1].trim())
        assertEquals("заземление корпуса", args.query)
        assertNull(args.docFilter)
        val readArgs = SearchCycle.parseReadArguments("тест-паспорт.txt, стр. 1")
        assertEquals("тест-паспорт.txt", readArgs.documentName)
        assertEquals(1, readArgs.page)

        // Чувствительный документ: исключён из AI-контекста, в UI доступен.
        runBlocking { repository.setSensitive(result.documentId, true) }
        val aiHits = runBlocking { repository.query("заземление", excludeSensitive = true) }
        assertTrue(aiHits.isEmpty())
        assertNull(
            runBlocking {
                repository.pageText("тест-паспорт.txt", 1, excludeSensitive = true)
            },
        )
        val uiHits = runBlocking { repository.query("заземление") }
        assertTrue(uiHits.isNotEmpty())

        // Переименование переписывает шапку чанка (B1).
        runBlocking { repository.renameDocument(result.documentId, "новое имя.txt") }
        val renamed = runBlocking { repository.query("заземление") }
        assertEquals("новое имя.txt", renamed.first().docName)
        assertTrue(renamed.first().text.startsWith("[Документ «новое имя.txt», стр. 1]"))
    }
}
