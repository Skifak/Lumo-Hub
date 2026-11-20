package com.lumo.hub.data.knowledge

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Upsert

enum class DocumentStatus { IMPORTING, READY, ERROR }

/**
 * Исходный документ в локальном хранилище: копия файла лежит в filesDir/docs,
 * извлечённый текст разбит на чанки в [ChunkEntity].
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mime: String,
    val path: String,
    val size: Long,
    val addedAt: Long,
    val pageCount: Int,
    val status: String = DocumentStatus.READY.name,
)

/**
 * Фрагмент документа. rowId автоинкрементный — FTS5-таблица с external
 * content связывается с чанками сквозной rowid-связкой. Текст дублируется
 * здесь как источник истины для цитат.
 */
@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val id: String,
    val docId: String,
    val ordinal: Int,
    val page: Int,
    val text: String,
)

/** Строка поиска FTS5. */
data class SearchHit(
    val docId: String,
    val docName: String,
    val page: Int,
    val ordinal: Int,
    val text: String,
    val snippet: String?,
)

@Dao
interface KnowledgeDao {
    @Query("SELECT count(*) FROM documents")
    suspend fun documentCount(): Int

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    suspend fun documents(): List<DocumentEntity>

    @Upsert
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM chunks WHERE docId = :docId")
    suspend fun deleteChunks(docId: String)

    @Insert
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    // Поиск выполняется через RoomDatabase.useReaderConnection напрямую —
    // FTS-таблица создаётся после Room-схемы, поэтому @Query здесь неприменим.

}

@Database(
    entities = [DocumentEntity::class, ChunkEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KnowledgeDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao
}
