package com.lumo.hub.ui.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.knowledge.DocumentEntity
import com.lumo.hub.data.knowledge.ImportResult
import com.lumo.hub.data.knowledge.KnowledgeRepository
import com.lumo.hub.data.knowledge.SearchHit
import kotlinx.coroutines.launch

/**
 * Локальная база знаний: импорт файлов через SAF (TXT/MD/PDF), список
 * документов и FTS-поиск по корпусу. Индексируется всё автоматически после
 * импорта — отдельного UI для FTS5 нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    contextualize: (suspend (documentName: String, page: Int, chunkText: String) -> String?)? = null,
) {
    val context = LocalContext.current
    val repository = remember { KnowledgeRepository.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var documents by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var pendingCollision by remember { mutableStateOf<NameCollision?>(null) }

    suspend fun reload() {
        documents = repository.documents()
    }

    LaunchedEffect(Unit) { reload() }

    /** Идентификаторы чувствительных документов — для пометки в выдаче поиска. */
    val sensitiveIds = remember(documents) {
        documents.filter { it.sensitive }.map { it.id }.toSet()
    }

    /** Импорт с опциональным переименованием («Оставить оба») и удалением заменяемых. */
    suspend fun runImport(
        uri: Uri,
        targetName: String? = null,
        replaceIds: List<String> = emptyList(),
    ) {
        isImporting = true
        try {
            replaceIds.forEach { repository.deleteDocument(it) }
            val result: ImportResult = repository.import(uri, contextualize)
            if (targetName != null) repository.renameDocument(result.documentId, targetName)
            snackbar.showSnackbar(
                "Импортировано: ${result.chunkCount} фрагментов"
            )
            reload()
        } catch (error: Throwable) {
            snackbar.showSnackbar(error.message ?: "Не удалось импортировать файл")
        } finally {
            isImporting = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val displayName = displayNameOf(uri)
                val incoming = displayName.lowercase()
                val matches = repository.documents()
                    .filter { normalizeName(it.name) == incoming }
                    .sortedByDescending { it.addedAt }
                if (matches.isEmpty()) {
                    runImport(uri)
                } else {
                    val newest = matches.first()
                    pendingCollision = NameCollision(
                        uri = uri,
                        displayName = displayName,
                        matches = matches,
                        existingPreview = repository.previewText(newest.id),
                        incomingPreview = repository.previewIncoming(uri),
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Документы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isImporting) {
                                importLauncher.launch(
                                    arrayOf("text/*", "application/pdf", "text/markdown")
                                )
                            }
                        },
                        enabled = !isImporting,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Импортировать файл")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск по документам") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = {
                        isSearching = true
                        scope.launch {
                            try {
                                searchResults = repository.query(searchQuery)
                            } catch (error: Throwable) {
                                snackbar.showSnackbar(error.message ?: "Ошибка поиска")
                            } finally {
                                isSearching = false
                            }
                        }
                    },
                    enabled = !isSearching && searchQuery.isNotBlank(),
                ) {
                    Text("Найти")
                }
            }

            if (isImporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                    Spacer(Modifier.padding(8.dp))
                    Text("Импорт…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (searchResults.isNotEmpty()) {
                Text(
                    "Результаты поиска",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(searchResults) { hit ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${hit.docName}, стр. ${hit.page}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (hit.docId in sensitiveIds) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Чувствительный",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                            Text(
                                hit.snippet?.replace(Regex("\\[(.*?)\\]"), "[$1]") ?: hit.text.take(200),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Text(
                "Документы (${documents.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            if (documents.isEmpty() && !isImporting) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                ) {
                    Text(
                        "Пока нет документов",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/pdf")) }) {
                        Text("Импортировать первый файл")
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(documents) { doc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        doc.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (isAdditionalName(doc.name)) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                    RoundedCornerShape(8.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "Additional",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "${doc.pageCount} стр. · ${(doc.size / 1024)} КБ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    repository.setSensitive(doc.id, !doc.sensitive)
                                    reload()
                                }
                            }) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = if (doc.sensitive) {
                                        "Снять пометку «чувствительный»"
                                    } else {
                                        "Пометить «чувствительный»"
                                    },
                                    tint = if (doc.sensitive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    repository.deleteDocument(doc.id)
                                    reload()
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingCollision?.let { collision ->
        AlertDialog(
            onDismissRequest = { pendingCollision = null },
            title = {
                Text(
                    "Документ с таким именем уже существует" +
                        if (collision.matches.size > 1) {
                            " (${duplicatesLabel(collision.matches.size)})"
                        } else {
                            ""
                        }
                )
            },
            text = {
                Column {
                    Text("Уже есть:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        collision.existingPreview ?: "(предпросмотр недоступен)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Новый файл:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        collision.incomingPreview ?: "(предпросмотр недоступен)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCollision = null
                    scope.launch {
                        runImport(collision.uri, replaceIds = collision.matches.map { it.id })
                    }
                }) {
                    Text("Заменить")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingCollision = null
                        scope.launch {
                            var newName = "${collision.displayName} (Additional)"
                            var n = 2
                            while (repository.existsByName(newName) != null) {
                                newName = "${collision.displayName} (Additional $n)"
                                n++
                            }
                            runImport(collision.uri, targetName = newName)
                        }
                    }) {
                        Text("Оставить оба")
                    }
                    TextButton(onClick = { pendingCollision = null }) {
                        Text("Отмена")
                    }
                }
            },
        )
    }
}

/** Модель коллизии импорта: совпавшие документы и превью обеих сторон. */
private data class NameCollision(
    val uri: Uri,
    val displayName: String,
    val matches: List<DocumentEntity>,
    val existingPreview: String?,
    val incomingPreview: String?,
)

/** Признак «дополнительного» имени: « (Additional)» или « (Additional N)». */
private val AdditionalName = Regex("""\s\(Additional( \d+)?\)""")

private fun isAdditionalName(name: String): Boolean = AdditionalName.containsMatchIn(name)

/** Нормализация имени для коллизий: срезает суффикс « (Additional)», регистр не важен. */
private fun normalizeName(name: String): String =
    name.removeSuffix(" (Additional)").lowercase()

/** Отображаемое имя SAF-Uri: без префикса «primary:» и подпапок, расширение сохраняется. */
private fun displayNameOf(uri: Uri): String {
    val segment = uri.lastPathSegment ?: return "Документ"
    return segment.substringAfterLast(':').substringAfterLast('/').ifBlank { "Документ" }
}

/** Склонение слова «дубликат» для заголовка диалога. */
private fun duplicatesLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "дубликат"
        mod10 in 2..4 && mod100 !in 12..14 -> "дубликата"
        else -> "дубликатов"
    }
    return "$count $word"
}
