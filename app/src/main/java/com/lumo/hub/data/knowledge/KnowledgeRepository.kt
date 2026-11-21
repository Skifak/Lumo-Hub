package com.lumo.hub.data.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room3.Room
import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/** Результат импорта: сколько чанков получилось и сколько страниц прочитано. */
data class ImportResult(val documentId: String, val chunkCount: Int, val pageCount: Int)

/**
 * Локальное хранилище документов и FTS-индекса. Всё работает поверх bundled
 * SQLite (FTS5 + триграммный токенизатор), сеть не используется. UI-слоя нет —
 * импорт и поиск вызываются из кода приложения.
 */
class KnowledgeRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val docsDir = File(appContext.filesDir, "docs").apply { mkdirs() }

    private val database: KnowledgeDatabase =
        Room.databaseBuilder<KnowledgeDatabase>(
            appContext,
            appContext.getDatabasePath("lumo_knowledge.db").absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2)
            .build()

    // Создание FTS-таблицы вне Room-миграций: схема документов версионируется
    // Room-миграцией (v2 — флаг sensitive), а external content-таблица со
    // сквозной rowid-связкой даёт ранжирование и snippet.
    private suspend fun ensureFts() {
        database.useWriterConnection { connection ->
            connection.usePrepared(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
                    text,
                    content='chunks',
                    content_rowid='rowId',
                    tokenize='trigram'
                )
                """.trimIndent()
            ) { it.step() }
            connection.usePrepared(
                """
                CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN
                    INSERT INTO chunks_fts(rowid, text) VALUES (new.rowId, new.text);
                END
                """.trimIndent()
            ) { it.step() }
            connection.usePrepared(
                """
                CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN
                    INSERT INTO chunks_fts(chunks_fts, rowid, text) VALUES ('delete', old.rowId, old.text);
                END
                """.trimIndent()
            ) { it.step() }
            connection.usePrepared(
                """
                CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE ON chunks BEGIN
                    INSERT INTO chunks_fts(chunks_fts, rowid, text) VALUES ('delete', old.rowId, old.text);
                    INSERT INTO chunks_fts(rowid, text) VALUES (new.rowId, new.text);
                END
                """.trimIndent()
            ) { it.step() }
        }
    }

    private val ftsReady = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun ensureFtsOnce() {
        if (!ftsReady.get()) {
            ensureFts()
            backfillChunkHeaders()
            ftsReady.set(true)
        }
    }

    /**
     * B1-бэкфилл для документов, импортированных до появления шапок: чанк без
     * шапки `[Документ «X», стр. N]` получает её первой строкой. Идемпотентно
     * (NOT LIKE); триггер chunks_au синхронизирует FTS.
     */
    private suspend fun backfillChunkHeaders() {
        database.useWriterConnection { connection ->
            connection.usePrepared(
                """
                UPDATE chunks SET text = (
                    SELECT '[Документ «' || d.name || '», стр. ' || chunks.page || ']' || char(10) || char(10) || chunks.text
                    FROM documents d WHERE d.id = chunks.docId
                )
                WHERE text NOT LIKE '[Документ «%'
                  AND docId IN (SELECT id FROM documents)
                """.trimIndent()
            ) { it.step() }
        }
    }

    suspend fun documentCount(): Int = database.knowledgeDao().documentCount()
    suspend fun documents(): List<DocumentEntity> = database.knowledgeDao().documents()
    suspend fun document(id: String): DocumentEntity? = database.knowledgeDao().document(id)

    suspend fun deleteDocument(id: String) {
        ensureFtsOnce()
        val dao = database.knowledgeDao()
        val doc = dao.document(id) ?: return
        // Триггеры синхронизируют FTS при удалении чанков.
        dao.deleteChunks(id)
        dao.deleteDocument(id)
        runCatching { File(doc.path).delete() }
    }

    /**
     * Импорт документа из SAF-Uri: копирует файл в filesDir/docs, извлекает
     * текст (TXT/MD целиком, PDF постранично с координатной склейкой строк и
     * таблиц), режет на чанки по абзацам с перекрытием и заполняет FTS-индекс.
     */
    suspend fun import(
        uri: Uri,
        contextualize: (suspend (documentName: String, page: Int, chunkText: String) -> String?)? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        ensureFtsOnce()
        val resolver = appContext.contentResolver
        val name = queryDisplayName(uri) ?: "doc-${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: guessMime(name)
        val id = "doc-${UUID.randomUUID()}"
        val target = File(docsDir, "$id${extensionFor(name, mime)}")

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать файл")

        val isPdf = mime == "application/pdf" || target.extension == "pdf"
        val pages = when {
            isPdf -> extractPdfPages(target)
            mime.startsWith("text/") || target.extension in listOf("txt", "md", "markdown") ->
                listOf(TextPage(1, target.readText(Charsets.UTF_8)))
            else -> error("Формат не поддерживается: $mime")
        }
        val pageCount = pages.size

        val chunks = chunk(pages)
        val dao = database.knowledgeDao()
        dao.upsertDocument(
            DocumentEntity(
                id = id,
                name = name,
                mime = mime,
                path = target.absolutePath,
                size = target.length(),
                addedAt = System.currentTimeMillis(),
                pageCount = pageCount,
                status = DocumentStatus.READY.name,
            )
        )
        dao.insertChunks(
            chunks.mapIndexed { index, chunk ->
                // B2: короткий контекст чанка от модели; ошибка/таймаут →
                // обычный чанк, импорт не прерывается.
                val context = contextualize?.let { fn ->
                    try {
                        fn(name, chunk.page, chunk.text)
                    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                }
                ChunkEntity(
                    id = "$id#$index",
                    docId = id,
                    ordinal = index,
                    page = chunk.page,
                    text = chunkText(name, chunk.page, chunk.text, context),
                )
            }
        )
        ImportResult(id, chunks.size, pageCount)
    }

    /**
     * Превью документа: начало первого чанка (ordinal = 0), не более [chars]
     * символов; «…» при усечении. null — если чанков нет или текст пуст.
     */
    suspend fun previewText(documentId: String, chars: Int = 100): String? =
        withContext(Dispatchers.IO) {
            val text = database.useReaderConnection { tx ->
                tx.usePrepared(
                    "SELECT text FROM chunks WHERE docId = ? AND ordinal = 0 LIMIT 1"
                ) { stmt ->
                    stmt.bindText(1, documentId)
                    if (stmt.step()) stmt.getText(0) else null
                }
            }
            text?.takeIf { it.isNotBlank() }?.let { previewOf(stripPreamble(it), chars) }
        }

    /**
     * Превью импортируемого файла до импорта: начало текста (TXT/MD) или первой
     * страницы PDF. Любая ошибка чтения даёт null и не блокирует импорт.
     */
    suspend fun previewIncoming(uri: Uri, chars: Int = 100): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = appContext.contentResolver
                val mime = resolver.getType(uri).orEmpty()
                val isPdf = mime == "application/pdf" ||
                    uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)
                val text = if (isPdf) {
                    resolver.openInputStream(uri)?.use { firstPdfPage(it) }
                } else {
                    resolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
                text?.takeIf { it.isNotBlank() }?.let { previewOf(it, chars) }
            }.getOrNull()
        }

    /**
     * Последний добавленный документ с именем [name] (без учёта регистра),
     * null — если такого нет. Используется для проверки коллизий импорта.
     */
    suspend fun existsByName(name: String): DocumentEntity? =
        documents()
            .filter { it.name.equals(name, ignoreCase = true) }
            .maxByOrNull { it.addedAt }

    /**
     * Переименование документа после импорта (режим «Оставить оба»): имя
     * меняется в documents и в шапках чанков (B1), контекст B2 сохраняется.
     */
    suspend fun renameDocument(id: String, name: String) {
        ensureFtsOnce()
        val dao = database.knowledgeDao()
        for (chunk in dao.chunksByDoc(id)) {
            dao.updateChunkText(chunk.rowId, reheader(chunk.text, name, chunk.page))
        }
        database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE documents SET name = ? WHERE id = ?") { stmt ->
                stmt.bindText(1, name)
                stmt.bindText(2, id)
                stmt.step()
            }
        }
    }

    /** Обрезка превью до [chars] символов с «…» при усечении. */
    private fun previewOf(text: String, chars: Int): String {
        val taken = text.take(chars).trim()
        return if (text.length > chars) "$taken…" else taken
    }

    /** Первая страница PDF одним проходом: переиспользует [GlyphStripper] и [layoutPage]. */
    private fun firstPdfPage(source: InputStream): String {
        PDFBoxResourceLoader.init(appContext)
        val document = PDDocument.load(source)
        try {
            if (document.numberOfPages < 1) return ""
            val stripper = GlyphStripper()
            return layoutPage(stripper.collectPage(document, 1))
        } finally {
            document.close()
        }
    }

    /** Пометка «чувствительный документ»: исключён из AI-контекста, в UI доступен полностью. */
    suspend fun setSensitive(documentId: String, value: Boolean) {
        database.knowledgeDao().setSensitive(documentId, value)
    }

    /**
     * Поиск по корпусу через FTS5 MATCH (триграммный токенизатор). Кавычки
     * удваиваются — синтаксис FTS5-запроса принимает строку в двойных кавычках.
     * [docFilter] — подстрока имени документа (без учёта регистра, кириллица
     * учитывается): поиск ограничивается подходящими документами.
     * [excludeSensitive] — исключить чувствительные документы (путь AI);
     * UI-поиск оставляет значение по умолчанию — полный доступ.
     */
    suspend fun query(
        text: String,
        limit: Int = 8,
        docFilter: String? = null,
        excludeSensitive: Boolean = false,
    ): List<SearchHit> {
        val match = text.trim().replace("\"", "\"\"")
        if (match.isEmpty()) return emptyList()
        ensureFtsOnce()
        val filterIds = docFilter?.trim()?.takeIf { it.isNotEmpty() }?.let { filter ->
            documents().filter { it.name.contains(filter, ignoreCase = true) }.map { it.id }
        }
        if (filterIds != null && filterIds.isEmpty()) return emptyList()
        val filterClause = if (filterIds != null) {
            "AND c.docId IN (${filterIds.joinToString(",") { "?" }})"
        } else {
            ""
        }
        val sensitiveClause = if (excludeSensitive) "AND d.sensitive = 0" else ""
        return database.useReaderConnection { tx ->
            tx.usePrepared(
                """
                SELECT c.docId, d.name, c.page, c.ordinal, c.text,
                       snippet(chunks_fts, 0, '[', ']', '…', 8)
                FROM chunks_fts f
                JOIN chunks c ON c.rowId = f.rowid
                JOIN documents d ON d.id = c.docId
                WHERE chunks_fts MATCH ?
                $filterClause
                $sensitiveClause
                ORDER BY rank
                LIMIT $limit
                """.trimIndent()
            ) { stmt ->
                stmt.bindText(1, "\"$match\"")
                filterIds?.forEachIndexed { index, id -> stmt.bindText(2 + index, id) }
                val out = mutableListOf<SearchHit>()
                while (stmt.step()) {
                    out += SearchHit(
                        docId = stmt.getText(0),
                        docName = stmt.getText(1),
                        page = stmt.getLong(2).toInt(),
                        ordinal = stmt.getLong(3).toInt(),
                        text = stmt.getText(4),
                        snippet = stmt.getText(5),
                    )
                }
                out
            }
        }
    }

    /**
     * Полный текст страницы документа для команды READ: сначала точное имя,
     * затем последний документ, чьё имя содержит [documentName] без учёта
     * регистра. Тексты чанков страницы склеиваются через «\n\n», не более
     * 6000 символов; null — если документа, страницы или текста нет.
     * [excludeSensitive] — путь AI: чувствительный документ не отдаёт текст.
     */
    suspend fun pageText(
        documentName: String,
        page: Int,
        excludeSensitive: Boolean = false,
    ): String? {
        val doc = documents().firstOrNull { it.name == documentName }
            ?: documents().filter { it.name.contains(documentName, ignoreCase = true) }.maxByOrNull { it.addedAt }
            ?: return null
        if (excludeSensitive && doc.sensitive) return null
        val chunks = database.knowledgeDao().chunksByPage(doc.id, page)
        if (chunks.isEmpty()) return null
        return chunks.joinToString("\n\n") { stripPreamble(it.text) }
            .take(PAGE_TEXT_LIMIT).takeIf { it.isNotBlank() }
    }

    /**
     * Разбиение на чанки по абзацам с перекрытием ~150 символов. Страница
     * чанка — страница его первого абзаца (для цитат [документ, стр. N]).
     */
    private fun chunk(pages: List<TextPage>): List<Chunk> {
        val out = mutableListOf<Chunk>()
        val current = StringBuilder()
        var chunkPage = 1
        for (page in pages) {
            val paragraphs = page.text.split(ParagraphBreak)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { hardSplit(it, CHUNK_TARGET) }
            for (p in paragraphs) {
                if (current.isEmpty()) chunkPage = page.page
                if (current.isNotEmpty() && current.length + p.length > CHUNK_TARGET) {
                    val finished = current.toString().trim()
                    out += Chunk(finished, chunkPage)
                    current.setLength(0)
                    // Перекрытие: хвост предыдущего чанка во главе следующего.
                    current.append(finished.takeLast(CHUNK_OVERLAP)).append("\n\n")
                    chunkPage = page.page
                } else if (current.isNotEmpty()) {
                    current.append("\n\n")
                }
                current.append(p)
            }
        }
        if (current.isNotBlank()) out += Chunk(current.toString().trim(), chunkPage)
        return out
    }

    /** Деление длинного абзаца по границам слов, чтобы чанк не превышал цель. */
    private fun hardSplit(paragraph: String, limit: Int): List<String> {
        if (paragraph.length <= limit) return listOf(paragraph)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (word in paragraph.split(' ')) {
            if (sb.isNotEmpty() && sb.length + word.length + 1 > limit) {
                out += sb.toString()
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /**
     * Извлечение текста PDF постранично координатными эвристиками: строки
     * группируются по y, ячейки таблиц — по широким x-зазорам. pdfbox-android
     * отдаёт глифы с координатами (TextPosition), готовый текст stripper'а не
     * используется — строки склеиваются заново.
     */
    private fun extractPdfPages(file: File): List<TextPage> {
        PDFBoxResourceLoader.init(appContext)
        val document = PDDocument.load(file)
        try {
            val stripper = GlyphStripper()
            return (1..document.numberOfPages).map { pageNo ->
                TextPage(pageNo, layoutPage(stripper.collectPage(document, pageNo)))
            }
        } finally {
            document.close()
        }
    }

    /** Накопитель глифов страницы; на выход stripper'а не пишет. */
    private class GlyphStripper : PDFTextStripper() {
        private val glyphs = mutableListOf<TextPosition>()

        fun collectPage(document: PDDocument, pageNo: Int): List<TextPosition> {
            glyphs.clear()
            startPage = pageNo
            endPage = pageNo
            getText(document)
            return glyphs.toList()
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (textPositions != null) glyphs += textPositions
        }
    }

    /** Склейка глифов в текст страницы: строки по y, ячейки « | » по x-зазорам. */
    private fun layoutPage(glyphs: List<TextPosition>): String {
        if (glyphs.isEmpty()) return ""
        val rows = mutableListOf<MutableList<TextPosition>>()
        var rowY = Float.NaN
        for (g in glyphs.sortedWith(compareBy({ it.yDirAdj }, { it.xDirAdj }))) {
            if (rowY.isNaN() || abs(g.yDirAdj - rowY) > ROW_Y_TOLERANCE) {
                rows += mutableListOf(g)
                rowY = g.yDirAdj
            } else {
                rows.last().add(g)
            }
        }
        val out = StringBuilder()
        var prevRowY = Float.NaN
        var prevRowSize = 12f
        for (row in rows) {
            row.sortBy { it.xDirAdj }
            val line = rowText(row)
            if (line.isBlank()) continue
            if (!prevRowY.isNaN()) {
                val gap = row.first().yDirAdj - prevRowY
                val paraBreak = gap > GAP_PARAGRAPH * max(prevRowSize, rowFontSize(row))
                out.append(if (paraBreak) "\n\n" else "\n")
            }
            out.append(line)
            prevRowY = row.first().yDirAdj
            prevRowSize = rowFontSize(row)
        }
        return out.toString()
    }

    private fun rowText(row: List<TextPosition>): String {
        val sb = StringBuilder()
        var prev: TextPosition? = null
        for (g in row) {
            val word = g.unicode?.trim().orEmpty()
            if (word.isEmpty()) {
                if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' ')
                continue
            }
            if (prev != null && sb.isNotEmpty()) {
                val gap = g.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                val size = max(prev.fontSize, g.fontSize)
                if (gap > GAP_CELL * size) {
                    if (sb.endsWith(" ")) sb.setLength(sb.length - 1)
                    sb.append(" | ")
                } else if (gap > GAP_SPACE * size && !sb.endsWith(" ")) {
                    sb.append(' ')
                }
            }
            sb.append(word)
            prev = g
        }
        return sb.toString().trim()
    }

    private fun rowFontSize(row: List<TextPosition>): Float {
        val avg = row.map { it.fontSize }.average().toFloat()
        return if (avg > 0f) avg else 12f
    }

    private fun queryDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "md", "markdown" -> "text/markdown"
        "txt" -> "text/plain"
        else -> "text/plain"
    }

    private fun extensionFor(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext.isNotEmpty() -> ".$ext"
            mime == "application/pdf" -> ".pdf"
            else -> ".txt"
        }
    }

    private data class Chunk(val text: String, val page: Int)
    private data class TextPage(val page: Int, val text: String)

    companion object {
        private val ParagraphBreak = Regex("\n{2,}")

        // B1: шапка чанка — первая строка «[Документ «X», стр. N]»; B2 — строка
        // «Контекст: …» сразу за шапкой. Обе входят в индексируемый текст чанка.
        private val ChunkHeader = Regex("""^\[Документ «.+», стр\. \d+]""")
        private val ChunkPreamble = Regex("""^\[Документ «.+», стр\. \d+](?:\nКонтекст: .*)?\n*""")

        fun chunkHeader(name: String, page: Int): String = "[Документ «$name», стр. $page]"

        /** Текст чанка с шапкой B1 и опциональным контекстом B2 (Contextual Retrieval). */
        fun chunkText(name: String, page: Int, body: String, context: String?): String = buildString {
            append(chunkHeader(name, page))
            if (!context.isNullOrBlank()) {
                append("\nКонтекст: ").append(context.trim().replace('\n', ' '))
            }
            append("\n\n").append(body)
        }

        /** Текст без шапки и контекста: превью и склейка страницы. */
        fun stripPreamble(text: String): String = ChunkPreamble.replaceFirst(text, "")

        /** Новая шапка вместо старой (переименование), контекст B2 сохраняется. */
        fun reheader(text: String, name: String, page: Int): String {
            val body = if (ChunkHeader.containsMatchIn(text)) {
                ChunkHeader.replaceFirst(text, "")
            } else {
                "\n\n$text"
            }
            return chunkHeader(name, page) + body
        }

        private const val CHUNK_TARGET = 1200
        private const val CHUNK_OVERLAP = 150
        private const val PAGE_TEXT_LIMIT = 6000

        // Координатные эвристики склейки PDF (× размер шрифта): пробел /
        // граница ячейки / новый абзац. Допуск группировки в строку — пункты.
        private const val GAP_SPACE = 0.25f
        private const val GAP_CELL = 1.5f
        private const val GAP_PARAGRAPH = 1.5f
        private const val ROW_Y_TOLERANCE = 2f

        @Volatile private var instance: KnowledgeRepository? = null

        fun get(context: Context): KnowledgeRepository =
            instance ?: synchronized(this) {
                instance ?: KnowledgeRepository(context).also { instance = it }
            }
    }
}
