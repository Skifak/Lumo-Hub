package com.lumo.hub.ui.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumo.hub.data.ChatRepository
import com.lumo.hub.data.ChatRole
import com.lumo.hub.data.ModelsState
import com.lumo.hub.data.SettingsRepository
import com.lumo.hub.network.ChatCompletionRequest
import com.lumo.hub.network.ChatMessageRequest
import com.lumo.hub.network.ChatStreamEvent
import com.lumo.hub.network.OpenAiClient
import com.lumo.hub.network.OpenAiProvider
import com.lumo.hub.theme.lumoVisual
import com.lumo.hub.ui.components.ChatDraftStore
import com.lumo.hub.ui.components.LumoOrb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Ошибка отправки/стрима: показывается inline, в историю не пишется. */
private data class ChatUiError(val details: String, val retryText: String)

/**
 * Экран разговора: компактная шапка (название, роль • модель), пузыри сообщений
 * с Markdown и код-блоками, streaming-ответ с typing-индикатором и Stop,
 * inline-ошибки с Retry, per-chat draft, smart autoscroll и jump-to-bottom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    title: String,
    chatId: String = "draft",
    onBack: () -> Unit,
    settingsRepository: SettingsRepository,
    openAiClient: OpenAiClient,
    // UI-safe ограничение: NavHost не передаёт этот callback, поэтому кнопка
    // «Настройки» в inline-ошибке появляется только при явном подключении.
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { ChatRepository(context) }
    var conversation by remember(chatId) { mutableStateOf(repository.conversation(chatId)) }
    var input by remember(chatId) { mutableStateOf(ChatDraftStore.get(chatId).orEmpty()) }
    val modelsState by settingsRepository.state.collectAsStateWithLifecycle()
    val models = (modelsState.modelsState as? ModelsState.Ready)?.models.orEmpty()
    var selectedModel by remember(chatId, modelsState.provider.model) { mutableStateOf(conversation.model.ifBlank { modelsState.provider.model }) }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var streamJob by remember(chatId) { mutableStateOf<Job?>(null) }
    var autoScrollAfterUpdate by remember(chatId) { mutableStateOf(false) }
    var isStreaming by remember(chatId) { mutableStateOf(false) }
    var errorState by remember(chatId) { mutableStateOf<ChatUiError?>(null) }
    var roleSheet by remember { mutableStateOf(false) }
    var modelSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    // Название обновляется по первому сообщению — показываем актуальное из хранилища.
    val headerTitle = if (conversation.title != "Новый чат") conversation.title else title

    val isAtBottom by remember { derivedStateOf { listState.isAtBottom() } }
    val typingVisible = isStreaming && conversation.messages.lastOrNull()?.let { !it.isUser && it.text.isBlank() } == true

    DisposableEffect(chatId) {
        onDispose { streamJob?.cancel() }
    }

    // Открытие длинной истории: сразу вниз, без анимации.
    LaunchedEffect(chatId) {
        val count = repository.conversation(chatId).messages.size
        if (count > 0) runCatching { listState.scrollToItem(count - 1) }
    }

    // Smart autoscroll: тянемся за новым контентом только если пользователь у низа.
    LaunchedEffect(conversation.messages.size, conversation.messages.lastOrNull()?.text, typingVisible) {
        if (autoScrollAfterUpdate && isAtBottom && conversation.messages.isNotEmpty()) {
            autoScrollAfterUpdate = false
            val last = listState.layoutInfo.totalItemsCount
            if (last > 0) listState.animateScrollToItem(last - 1)
        }
    }

    val scrollToBottom: () -> Unit = {
        scope.launch {
            val last = listState.layoutInfo.totalItemsCount
            if (last > 0) listState.animateScrollToItem(last - 1)
        }
    }

    val copyText: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar("Скопировано в буфер обмена") }
    }

    fun validateProvider(): String? = when {
        modelsState.provider.baseUrl.isBlank() -> "Укажите адрес API в настройках"
        modelsState.provider.apiKey.isBlank() -> "Укажите API-ключ в настройках"
        selectedModel.trim().isBlank() -> "Выберите модель в настройках"
        else -> null
    }

    fun startStream(userText: String, assistantId: String) {
        val provider = modelsState.provider
        val model = selectedModel.trim()
        // Пустые ассистентские заглушки (после Stop до первого токена) не должны
        // попадать в контекст запроса.
        val history = repository.conversation(chatId).messages
            .filterNot { !it.isUser && it.text.isBlank() }
            .map { ChatMessageRequest(if (it.isUser) "user" else "assistant", it.text) }
        streamJob?.cancel()
        isStreaming = true
        var job: Job? = null
        job = scope.launch {
            var answer = ""
            var lastSavedAnswer = ""
            var lastSaveNanos = 0L
            try {
                openAiClient.stream(
                    OpenAiProvider(provider.baseUrl.trim(), provider.apiKey),
                    ChatCompletionRequest(model = model, messages = history),
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> {
                            answer += event.text
                            autoScrollAfterUpdate = listState.isAtBottom()
                            conversation = conversation.copy(
                                messages = conversation.messages.map { message ->
                                    if (message.id == assistantId) message.copy(text = answer) else message
                                },
                            )
                            val now = System.nanoTime()
                            if (lastSaveNanos == 0L || now - lastSaveNanos >= STREAM_SAVE_INTERVAL_NANOS) {
                                repository.updateMessage(chatId, assistantId, answer)
                                lastSavedAnswer = answer
                                lastSaveNanos = now
                            }
                        }
                        is ChatStreamEvent.Completed -> Unit
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Ошибка — inline UI с Retry; в историю ложное сообщение не пишем.
                errorState = ChatUiError(error.message ?: "Не удалось получить ответ", userText)
            } finally {
                // Частичный ответ сохраняется при завершении и при Stop.
                if (answer != lastSavedAnswer) {
                    repository.updateMessage(chatId, assistantId, answer)
                }
                // Гасим индикатор только если это всё ещё актуальный стрим:
                // finally отменённого job не должен перекрыть новый запуск.
                if (streamJob === job) {
                    isStreaming = false
                }
            }
        }
        streamJob = job
    }

    val sendMessage: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty() && !isStreaming) {
            // Валидация до записи сообщений: ошибки настроек не оставляют
            // фальшивых следов в истории.
            val validationError = validateProvider()
            if (validationError != null) {
                errorState = ChatUiError(validationError, text)
            } else {
                errorState = null
                streamJob?.cancel()
                val userMessage = repository.appendMessage(chatId, text, true)
                repository.setModel(chatId, selectedModel.trim())
                // Пустая ассистентская строка — только rendering-заглушка,
                // в запрос она не попадает.
                val assistantMessage = repository.appendMessage(chatId, "", false)
                conversation = conversation.copy(
                    model = selectedModel.trim(),
                    title = if (conversation.title == "Новый чат") {
                        text.replace(Regex("\\s+"), " ").take(36).let { if (text.length > 36) "$it…" else it }
                    } else conversation.title,
                    messages = conversation.messages + userMessage + assistantMessage,
                )
                input = ""
                ChatDraftStore.set(chatId, "")
                autoScrollAfterUpdate = true
                startStream(text, assistantMessage.id)
            }
        }
    }

    val retryLast: () -> Unit = retry@{
        val error = errorState ?: return@retry
        errorState = null
        val validationError = validateProvider()
        if (validationError != null) {
            errorState = ChatUiError(validationError, error.retryText)
        } else {
            repository.setModel(chatId, selectedModel.trim())
            val conv = repository.conversation(chatId)
            var messages = conv.messages
            // Убедимся, что запрос есть в истории (валидация могла упасть до записи).
            val lastUser = messages.lastOrNull { it.isUser }
            if (lastUser == null || lastUser.text != error.retryText) {
                messages = messages + repository.appendMessage(chatId, error.retryText, true)
            }
            val (updated, assistantId) = messages.lastOrNull()?.let { last ->
                if (!last.isUser) {
                    // Переиспользуем существующую заглушку/частичный ответ.
                    repository.updateMessage(chatId, last.id, "")
                    messages.map { if (it.id == last.id) it.copy(text = "") else it } to last.id
                } else {
                    val placeholder = repository.appendMessage(chatId, "", false)
                    (messages + placeholder) to placeholder.id
                }
            } ?: run {
                val placeholder = repository.appendMessage(chatId, "", false)
                listOf(placeholder) to placeholder.id
            }
            conversation = conv.copy(messages = updated)
            autoScrollAfterUpdate = true
            startStream(error.retryText, assistantId)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // ── Компактная шапка ────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Назад",
                        tint = colors.onBackground,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onBackground,
                        maxLines = 1,
                    )
                    // Роль + модель одной строкой; тап открывает bottom sheet роли.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).combinedClickableForSubtitle { roleSheet = true },
                    ) {
                        Text(
                            text = conversation.role.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "  •  ${selectedModel.ifBlank { "модель" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = "Изменить роль",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                IconButton(onClick = { modelSheet = true }, enabled = models.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "Выбрать модель",
                        tint = if (models.isEmpty()) colors.onSurfaceVariant.copy(alpha = 0.4f) else colors.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))

            // ── Сообщения ───────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val visibleMessages = conversation.messages.filterNot { !it.isUser && it.text.isBlank() }
                    if (visibleMessages.isEmpty() && !typingVisible) {
                        item(key = "empty-state") {
                            EmptyConversationState(
                                onSuggestion = { suggestion ->
                                    input = suggestion
                                    ChatDraftStore.set(chatId, suggestion)
                                    runCatching { focusRequester.requestFocus() }
                                },
                            )
                        }
                    }
                    items(visibleMessages, key = { it.id }) { message ->
                        MessageBubble(
                            text = message.text,
                            isUser = message.isUser,
                            time = message.time,
                            modifier = Modifier.animateItem(),
                            onCopy = copyText,
                        )
                    }
                    if (typingVisible) {
                        item(key = "typing-indicator") {
                            Row(Modifier.fillMaxWidth()) { TypingBubble() }
                        }
                    }
                }
                // Jump-to-bottom: только когда пользователь уехал вверх.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtBottom && conversation.messages.isNotEmpty(),
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 10.dp),
                ) {
                    Surface(
                        onClick = scrollToBottom,
                        shape = CircleShape,
                        color = colors.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Вниз к последнему сообщению",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp).size(20.dp),
                        )
                    }
                }
            }

            // ── Inline-ошибка: Retry / Настройки ────────────────────────────
            AnimatedVisibility(
                visible = errorState != null,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
            ) {
                errorState?.let { error ->
                    ErrorBanner(
                        error = error,
                        onRetry = retryLast,
                        onDismiss = { errorState = null },
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            // ── Composer ────────────────────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surfaceContainer,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    androidx.compose.material3.TextField(
                        value = input,
                        onValueChange = {
                            input = it
                            ChatDraftStore.set(chatId, it)
                        },
                        placeholder = {
                            Text(
                                "Сообщение…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                        colors =
                            androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        maxLines = 5,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    // Send ↔ Stop: во время стрима существующий cancellation flow.
                    AnimatedContent(
                        targetState = isStreaming,
                        label = "sendStop",
                        transitionSpec = {
                            (scaleIn(initialScale = 0.6f, animationSpec = tween(140)) + fadeIn(tween(140)))
                                .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(140)) + fadeOut(tween(140)))
                        },
                    ) { streaming ->
                        IconButton(
                            onClick = {
                                if (streaming) {
                                    // Stop: CancellationException → finally сохранит
                                    // уже полученный частичный ответ.
                                    streamJob?.cancel()
                                } else {
                                    sendMessage(input)
                                }
                            },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = if (streaming) colors.error else colors.primary,
                                    contentColor = if (streaming) colors.onError else colors.onPrimary,
                                ),
                        ) {
                            Icon(
                                if (streaming) Icons.Outlined.Stop else Icons.Outlined.Send,
                                contentDescription = if (streaming) "Остановить генерацию" else "Отправить",
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
        )
    }

    // ── Bottom sheet: роль чата ─────────────────────────────────────────────
    if (roleSheet) {
        ModalBottomSheet(onDismissRequest = { roleSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Роль чата",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = "Как Lumo ведёт себя в этом диалоге",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChatRole.entries.forEach { role ->
                    val selected = role == conversation.role
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .combinedClickable(onClick = {
                                    repository.setRole(chatId, role)
                                    conversation = repository.conversation(chatId)
                                    roleSheet = false
                                })
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (selected) colors.primary else colors.outlineVariant),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = role.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) colors.primary else colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    // ── Bottom sheet: модель ────────────────────────────────────────────────
    if (modelSheet) {
        ModalBottomSheet(onDismissRequest = { modelSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Модель",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (models.isEmpty()) {
                    Text(
                        text = "Нет доступных моделей — проверьте подключение в настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (onOpenSettings != null) {
                        TextButton(onClick = { modelSheet = false; onOpenSettings() }) { Text("Открыть настройки") }
                    }
                } else {
                    models.forEach { model ->
                        val selected = model.id == selectedModel
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(onClick = {
                                        selectedModel = model.id
                                        repository.setModel(chatId, model.id)
                                        conversation = repository.conversation(chatId)
                                        modelSheet = false
                                    })
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = model.id,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) colors.primary else colors.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Выбрана",
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** Subtitle-кликабельность (та же combinedClickable, что и у пузырей). */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableForSubtitle(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

/** Empty state (DESIGN.md §4): orb, приглашение и suggestion chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyConversationState(onSuggestion: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val suggestions =
        listOf(
            "Объясни простыми словами, как работает квантовый компьютер",
            "Помоги составить план на неделю",
            "Придумай идеи для пет-проекта",
            "Переведи текст на английский",
        )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LumoOrb(size = 84.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Чем помочь?",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
        )
        Text(
            text = "Задайте вопрос или выберите подсказку",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: ChatUiError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = error.details,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
                Row {
                    TextButton(onClick = onRetry) {
                        Text("Повторить", color = colors.onErrorContainer, fontWeight = FontWeight.SemiBold)
                    }
                    if (onOpenSettings != null) {
                        TextButton(onClick = onOpenSettings) {
                            Text("Настройки", color = colors.onErrorContainer)
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Скрыть ошибку",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Typing-индикатор: три пульсирующие точки до первого токена ответа. */
@Composable
private fun TypingBubble() {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier
            .padding(start = 28.dp)
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(560, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 180),
                    ),
                label = "typingDot$index",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        alpha = phase
                        scaleX = 0.6f + 0.4f * phase
                        scaleY = 0.6f + 0.4f * phase
                    }
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.55f + 0.45f * phase)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    time: String,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val visual = lumoVisual()
    val shape =
        if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            // Маркер ассистента: мини-orb в фирменном градиенте.
            Box(
                Modifier
                    .align(Alignment.Top)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(visual.logoBrush),
            )
        }
        Surface(
            shape = shape,
            color = if (isUser) colors.primary else colors.surfaceContainer,
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(shape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onCopy(text) },
                    ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val segments = remember(text) { splitFenceSegments(text) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    segments.forEach { segment ->
                        when (segment) {
                            is MdSegment.Code -> CodeBlock(code = segment.code, lang = segment.lang, onCopy = onCopy)
                            is MdSegment.Text ->
                                if (segment.raw.isNotBlank()) {
                                    MarkdownText(
                                        text = segment.raw,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) colors.onPrimary else colors.onSurface,
                                    )
                                }
                        }
                    }
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (isUser) colors.onPrimary.copy(alpha = 0.72f) else colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                )
            }
        }
    }
}

/** Блок кода: тёмная подложка, язык и копирование одним тапом. */
@Composable
private fun CodeBlock(code: String, lang: String, onCopy: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerHigh,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lang.ifBlank { "код" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopy(code) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Скопировать код",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Box(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurface,
                )
            }
        }
    }
}

private sealed interface MdSegment {
    data class Text(val raw: String) : MdSegment
    data class Code(val code: String, val lang: String) : MdSegment
}

/**
 * Деление сообщения на текст и fenced-блоки. Незакрытый ``` при стриминге
 * трактуется как код до конца — ничего не падает и не «мигает».
 */
private fun splitFenceSegments(source: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val text = StringBuilder()
    val code = StringBuilder()
    var inFence = false
    var lang = ""

    fun flushText() {
        if (text.isNotEmpty()) {
            segments += MdSegment.Text(text.toString().trimEnd('\n'))
            text.setLength(0)
        }
    }

    fun flushCode() {
        if (code.isNotEmpty()) {
            segments += MdSegment.Code(code.toString().trimEnd('\n'), lang)
            code.setLength(0)
            lang = ""
        }
    }

    source.replace("\r\n", "\n").split('\n').forEach { line ->
        val fence = line.trimStart().startsWith("```")
        if (fence) {
            if (inFence) {
                inFence = false
                flushCode()
            } else {
                inFence = true
                lang = line.trimStart().removePrefix("```").trim()
                flushText()
            }
        } else if (inFence) {
            code.appendLine(line)
        } else {
            text.appendLine(line)
        }
    }
    if (inFence) flushCode()
    flushText()
    return segments
}

/**
 * Небольшой defensive Markdown-рендерер (текстовые сегменты без fenced-блоков).
 * Незакрытые дескрипторы остаются обычным текстом — безопасно для стриминга.
 */
@Composable
private fun MarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = remember(text, colors.onSurfaceVariant, colors.surfaceVariant) {
            markdownToAnnotatedString(text, colors.onSurfaceVariant, colors.surfaceVariant)
        },
        style = style,
        color = color,
    )
}

private fun markdownToAnnotatedString(
    source: String,
    codeColor: Color,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    val lines = source.split('\n')
    lines.forEachIndexed { index, line ->
        val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line)
        val hrule = Regex("^\\s*([-*_])\\s*(?:\\1\\s*){2,}$").matchEntire(line)
        val quote = Regex("^\\s*>\\s?(.*)$").matchEntire(line)
        val list = Regex("^(\\s*)([-*+]\\s+|\\d+[.)]\\s+)(.*)$").matchEntire(line)
        when {
            heading != null -> withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = (22 - heading.groupValues[1].length * 2).sp,
                ),
            ) { appendInlineMarkdown(heading.groupValues[2], codeColor, codeBackground) }
            hrule != null -> withStyle(SpanStyle(color = codeColor)) { append("──────────────") }
            quote != null -> withStyle(SpanStyle(color = codeColor)) {
                append("▎ ")
                appendInlineMarkdown(quote.groupValues[1], codeColor, codeBackground)
            }
            list != null -> {
                append(list.groupValues[1])
                val marker = list.groupValues[2]
                // Нумерованные списки сохраняют свою нумерацию.
                append(if (marker.first().isDigit()) marker else "• ")
                appendInlineMarkdown(list.groupValues[3], codeColor, codeBackground)
            }
            else -> appendInlineMarkdown(line, codeColor, codeBackground)
        }
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    source: String,
    codeColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < source.length) {
        // Ссылки намеренно некликабельны: показ назначения безопаснее
        // активации непроверенных URL из стрима.
        if (source[index] == '[') {
            val closeLabel = source.indexOf(']', index + 1)
            val openUrl = if (closeLabel >= 0 && closeLabel + 1 < source.length && source[closeLabel + 1] == '(') closeLabel + 1 else -1
            val closeUrl = if (openUrl >= 0) source.indexOf(')', openUrl + 1) else -1
            if (closeLabel > index + 1 && closeUrl > openUrl + 1) {
                append(source.substring(index + 1, closeLabel))
                withStyle(SpanStyle(color = codeColor, textDecoration = TextDecoration.Underline)) {
                    append(" (${source.substring(openUrl + 1, closeUrl)})")
                }
                index = closeUrl + 1
                continue
            }
        }
        val marker = source[index]
        val markerLength = when {
            source.startsWith("**", index) || source.startsWith("__", index) || source.startsWith("~~", index) -> 2
            marker == '`' || marker == '*' || marker == '_' -> 1
            else -> 0
        }
        if (markerLength > 0) {
            val end = source.indexOf(source.substring(index, index + markerLength), index + markerLength)
            if (end > index + markerLength) {
                val content = source.substring(index + markerLength, end)
                val span = when {
                    marker == '`' -> SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor, background = codeBackground)
                    source.startsWith("~~", index) -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    markerLength == 2 -> SpanStyle(fontWeight = FontWeight.Bold)
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }
                withStyle(span) { append(content) }
                index = end + markerLength
                continue
            }
        }
        append(marker)
        index++
    }
}

private const val STREAM_SAVE_INTERVAL_NANOS = 200_000_000L

private fun LazyListState.isAtBottom(): Boolean {
    val totalItems = layoutInfo.totalItemsCount
    return totalItems > 0 && layoutInfo.visibleItemsInfo.lastOrNull()?.index == totalItems - 1
}
