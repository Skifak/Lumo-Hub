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
import java.io.File
import java.util.UUID

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
            .build()

    // Создание FTS-таблицы вне Room-миграций: схема статична (v1), а external
    // content-таблица со сквозной rowid-связкой даёт ранжирование и snippet.
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
        }
    }

    private val ftsReady = java.util.concurrent.atomic.AtomicBoolean(false)

    private suspend fun ensureFtsOnce() {
        if (!ftsReady.get()) {
            ensureFts()
            ftsReady.set(true)
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
     * текст (TXT/MD целиком, PDF постранично), режет на чанки по абзацам с
     * перекрытием и заполняет FTS-индекс.
     */
    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        ensureFtsOnce()
        val resolver = appContext.contentResolver
        val name = queryDisplayName(uri) ?: "doc-${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: guessMime(name)
        val id = "doc-${UUID.randomUUID()}"
        val target = File(docsDir, "$id${extensionFor(name, mime)}")

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать файл")

        val text = when {
            mime == "application/pdf" || target.extension == "pdf" -> extractPdfText(target)
            mime.startsWith("text/") || target.extension in listOf("txt", "md", "markdown") ->
                target.readText(Charsets.UTF_8)
            else -> error("Формат не поддерживается: $mime")
        }
        val pageCount = if (mime == "application/pdf") countPdfPages(target) else 1

        val chunks = chunk(text)
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
                ChunkEntity(
                    id = "$id#$index",
                    docId = id,
                    ordinal = index,
                    page = chunk.page,
                    text = chunk.text,
                )
            }
        )
        ImportResult(id, chunks.size, pageCount)
    }

    /**
     * Поиск по корпусу через FTS5 MATCH (триграммный токенизатор). Кавычки
     * удваиваются — синтаксис FTS5-запроса принимает строку в двойных кавычках.
     */
    suspend fun query(text: String, limit: Int = 8): List<SearchHit> {
        val match = text.trim().replace("\"", "\"\"")
        if (match.isEmpty()) return emptyList()
        ensureFtsOnce()
        return database.useReaderConnection { tx ->
            tx.usePrepared(
                """
                SELECT c.docId, d.name, c.page, c.ordinal, c.text,
                       snippet(chunks_fts, 0, '[', ']', '…', 8)
                FROM chunks_fts f
                JOIN chunks c ON c.rowId = f.rowid
                JOIN documents d ON d.id = c.docId
                WHERE chunks_fts MATCH ?
                ORDER BY rank
                LIMIT $limit
                """.trimIndent()
            ) { stmt ->
                stmt.bindText(1, "\"$match\"")
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

    /** Разбиение на чанки по абзацам с перекрытием ~150 символов. */
    private fun chunk(text: String): List<Chunk> {
        val paragraphs = text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val out = mutableListOf<Chunk>()
        val current = StringBuilder()
        var page = 1
        for (p in paragraphs) {
            if (current.isNotEmpty() && current.length + p.length > CHUNK_TARGET) {
                out += Chunk(current.toString().trim(), page)
                current.setLength(0)
                current.append(p.takeLast(CHUNK_OVERLAP))
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(p)
            }
        }
        if (current.isNotBlank()) out += Chunk(current.toString().trim(), page)
        return out
    }

    private fun extractPdfText(file: File): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(appContext)
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
        try {
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            // Страницы разделены маркером, чтобы чанк знал свою страницу.
            return (1..document.numberOfPages).joinToString("\n\n") { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(document).trim()
            }
        } finally {
            document.close()
        }
    }

    private fun countPdfPages(file: File): Int {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(appContext)
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
        try {
            return document.numberOfPages
        } finally {
            document.close()
        }
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

    companion object {
        private const val CHUNK_TARGET = 1200
        private const val CHUNK_OVERLAP = 150

        @Volatile private var instance: KnowledgeRepository? = null

        fun get(context: Context): KnowledgeRepository =
            instance ?: synchronized(this) {
                instance ?: KnowledgeRepository(context).also { instance = it }
            }
    }
}
