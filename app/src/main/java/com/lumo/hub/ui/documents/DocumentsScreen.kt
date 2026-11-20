package com.lumo.hub.ui.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
fun DocumentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { KnowledgeRepository.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var documents by remember { mutableStateOf<List<DocumentEntity>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    suspend fun reload() {
        documents = repository.documents()
    }

    LaunchedEffect(Unit) { reload() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                try {
                    val result: ImportResult = repository.import(uri)
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
                            Text(
                                "${hit.docName}, стр. ${hit.page}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
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
                                Text(
                                    doc.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${doc.pageCount} стр. · ${(doc.size / 1024)} КБ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
