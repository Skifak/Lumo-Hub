package com.lumo.hub.ui.chats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ChatSummary
import com.lumo.hub.ui.components.ChatAvatar
import com.lumo.hub.ui.components.LumoOrb
import com.lumo.hub.ui.components.SectionTitle
import com.lumo.hub.ui.components.StaggerIn

/** Список чатов: заголовок, кнопка нового диалога, удаление с подтверждением. */
@Composable
fun ChatsScreen(
    chats: List<ChatSummary>,
    onBack: () -> Unit,
    onOpenChat: (ChatSummary) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ChatSummary?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.size(4.dp))
            SectionTitle(text = "Чаты")
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onNewChat,
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.shapes.small,
                        ),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Новый чат",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (chats.isEmpty()) {
            EmptyChats(onNewChat = onNewChat, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    StaggerIn(index = 0) {
                        ChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat) },
                            onDelete = { pendingDelete = chat },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить чат?") },
            text = { Text("«${chat.title}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteChat(chat.id)
                    pendingDelete = null
                }) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(title = chat.title, tintIndex = chat.id.hashCode())
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = chat.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = chat.time,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Удалить чат",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Empty state с CTA (DESIGN.md §4). */
@Composable
private fun EmptyChats(onNewChat: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LumoOrb(size = 88.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Пока нет чатов",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBackground,
        )
        Text(
            text = "Начните первый диалог — он появится здесь",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onNewChat,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Начать новый чат")
        }
    }
}
