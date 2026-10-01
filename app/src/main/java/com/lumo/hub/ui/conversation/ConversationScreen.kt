package com.lumo.hub.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ChatRepository
import com.lumo.hub.data.Conversation
import androidx.compose.ui.platform.LocalContext

/**
 * Экран разговора (mock): верхняя панель с названием и ролью, пузыри
 * сообщений, composer. Streaming-логика подключается на следующих этапах.
 */
@Composable
fun ConversationScreen(
    title: String,
    chatId: String = "draft",
    onBack: () -> Unit,
) {
    // В моке берём готовый диалог по title-подсказке; для новых чатов — пусто.
    val context = LocalContext.current
    val repository = remember { ChatRepository(context) }
    var conversation by remember(chatId) { mutableStateOf(repository.conversation(chatId)) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            listState.animateScrollToItem(conversation.messages.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // Шапка: назад, название, компактная роль
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = conversation.role.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (conversation.messages.isEmpty()) {
                item {
                    Text(
                        text = "Черновик: напишите первое сообщение — название чата появится автоматически.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            items(conversation.messages, key = { it.id }) { message ->
                MessageBubble(text = message.text, isUser = message.isUser, time = message.time)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Composer
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            "Сообщение…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (input.isNotBlank()) { repository.appendMessage(chatId, input.trim(), true); conversation = repository.conversation(chatId); input = "" } },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    time: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape =
                if (isUser) {
                    MaterialTheme.shapes.large
                } else {
                    MaterialTheme.shapes.large
                },
            color =
                if (isUser) {
                    colors.primary.copy(alpha = 0.14f)
                } else {
                    colors.surfaceContainer
                },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                )
            }
        }
    }
}
