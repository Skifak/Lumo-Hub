package com.lumo.hub.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ProviderProfile
import com.lumo.hub.data.SettingsUiState
import com.lumo.hub.data.ModelsState
import com.lumo.hub.data.ThemeMode
import com.lumo.hub.network.ModelInfo
import com.lumo.hub.theme.lumoVisual
import com.lumo.hub.ui.components.SectionTitle
import com.lumo.hub.ui.components.StaggerIn
import com.lumo.hub.network.AppRelease
import com.lumo.hub.network.AppUpdateState
import kotlinx.coroutines.launch

/**
 * Настройки (DESIGN.md §4): профиль, внешний вид, AI-провайдер,
 * локальные данные.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (ThemeMode) -> Unit,
    onProviderChange: ((ProviderProfile) -> ProviderProfile) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onSaveProvider: () -> Unit = {},
    onCheckConnection: suspend (ProviderProfile) -> Result<List<ModelInfo>> = { Result.success(emptyList()) },
    updateState: AppUpdateState = AppUpdateState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: (AppRelease) -> Unit = {},
    onCancelUpdate: () -> Unit = {},
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(18.dp))

        StaggerIn(index = 0) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(
                                lumoVisual().logoBrush,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Локальный профиль",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Все данные остаются на устройстве",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        SectionTitle(text = "Обновление приложения")
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(when (val status = updateState) {
                    AppUpdateState.Idle -> "Проверить последнюю версию на GitHub"
                    AppUpdateState.Checking -> "Проверка обновлений…"
                    is AppUpdateState.Downloading -> if (status.progress > 0) "Скачивание APK… ${status.progress}%" else "Скачивание APK…"
                    is AppUpdateState.Current -> "Установлена последняя версия (${status.version})"
                    is AppUpdateState.Available -> "Доступна версия ${status.release.version}"
                    is AppUpdateState.Error -> status.message
                })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = updateState !is AppUpdateState.Checking && updateState !is AppUpdateState.Downloading, onClick = onCheckForUpdate) { Text("Проверить") }
                    when (val status = updateState) {
                        is AppUpdateState.Available -> Button(onClick = { onDownloadUpdate(status.release) }) { Text("Скачать") }
                        is AppUpdateState.Downloading -> OutlinedButton(onClick = onCancelUpdate) { Text("Отмена") }
                        else -> Unit
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        StaggerIn(index = 1) {
            Column {
                SectionTitle(text = "Внешний вид")
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        StaggerIn(index = 2) {
            Column {
                SectionTitle(text = "AI-провайдер")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.provider.baseUrl,
                    onValueChange = { value -> onProviderChange { it.copy(baseUrl = value) } },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.provider.apiKey,
                    onValueChange = { value -> onProviderChange { it.copy(apiKey = value) } },
                    label = { Text("API key") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation =
                        if (state.apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(onClick = onToggleApiKeyVisibility) {
                            Icon(
                                if (state.apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription =
                                    if (state.apiKeyVisible) "Скрыть ключ" else "Показать ключ",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                var modelMenu by remember { mutableStateOf(false) }
                val models = (state.modelsState as? ModelsState.Ready)?.models.orEmpty()
                Box {
                    OutlinedButton(
                        enabled = models.isNotEmpty(),
                        onClick = { modelMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(state.provider.model.ifBlank { "Выберите модель" }) }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.id) },
                                onClick = { onProviderChange { it.copy(model = model.id) }; modelMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = connectionState !is ConnectionState.Loading,
                        onClick = {
                            connectionState = ConnectionState.Loading
                            scope.launch {
                                if (state.provider.baseUrl.isBlank() || state.provider.apiKey.isBlank()) {
                                    connectionState = ConnectionState.Error("Укажите Base URL и API key")
                                } else {
                                     val result = onCheckConnection(state.provider)
                                     connectionState = result.fold(
                                         onSuccess = { models -> if (models.isEmpty()) ConnectionState.Error("Список моделей пуст") else ConnectionState.Success("Подключение успешно: ${models.size} моделей") },
                                        onFailure = { ConnectionState.Error(it.message ?: "Не удалось подключиться") },
                                    )
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Проверить подключение")
                    }
                    Button(
                        enabled = state.modelsState is ModelsState.Ready && models.isNotEmpty() && state.provider.model.isNotBlank(),
                        onClick = {
                            onSaveProvider()
                            connectionState = ConnectionState.Success("Настройки сохранены")
                        },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Сохранить")
                    }
                }
                Spacer(Modifier.height(6.dp))
                when (val status = connectionState) {
                    ConnectionState.Idle -> Unit
                    ConnectionState.Loading -> Text("Проверка подключения…", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    is ConnectionState.Success -> Text(status.message, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    is ConnectionState.Error -> Text(status.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
                when (val modelsStatus = state.modelsState) {
                    ModelsState.Idle -> Text("Проверьте подключение, чтобы загрузить модели", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    ModelsState.Loading -> Text("Загрузка моделей…", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    is ModelsState.Ready -> if (modelsStatus.models.isEmpty()) Text("Модели не найдены", style = MaterialTheme.typography.bodySmall, color = colors.error)
                    is ModelsState.Error -> Text(modelsStatus.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        }
        Spacer(Modifier.height(22.dp))

        StaggerIn(index = 3) {
            Column {
                SectionTitle(text = "Данные")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = { showClearDialog = true },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Очистить историю чатов",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Text(
                                text = "Удалить все локальные диалоги",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Очистить",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.error,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Lumo Hub · MVP-каркас интерфейса",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все чаты будут удалены с устройства.") },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(
                        "Очистить",
                        color = colors.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }
}

private sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Loading : ConnectionState
    data class Success(val message: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
