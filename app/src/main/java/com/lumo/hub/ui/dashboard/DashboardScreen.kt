package com.lumo.hub.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ChatRepository
import com.lumo.hub.data.ChatSummary
import com.lumo.hub.theme.lumoVisual
import com.lumo.hub.ui.components.LumoOrb
import com.lumo.hub.ui.components.StaggerIn

/**
 * Dashboard (DESIGN.md §4): верхняя панель, приветствие, orb, быстрые
 * action-карточки, composer, сетка сервисов и недавние чаты.
 */
@Composable
fun DashboardScreen(
    chats: List<ChatSummary>,
    onOpenChats: () -> Unit,
    onOpenChat: (ChatSummary) -> Unit,
    onNewChat: () -> Unit,
    onOpenComingSoon: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val chatRepository = remember(context) { ChatRepository.get(context) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            StaggerIn(index = 0) {
                DashboardTopBar(onOpenSettings = onOpenSettings)
            }
            Spacer(Modifier.height(22.dp))

            // Приветствие + orb: центральный визуальный фокус
            StaggerIn(index = 1) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GreetingBlock(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(18.dp))
                    LumoOrb()
                }
            }
            Spacer(Modifier.height(24.dp))

            StaggerIn(index = 2) {
                HeroChatCard(onNewChat = onNewChat)
            }
            Spacer(Modifier.height(24.dp))

            // Быстрые action-карточки + composer
            StaggerIn(index = 3) {
                QuickActionsRow(onNewChat = onNewChat, onOpenChats = onOpenChats)
            }
            Spacer(Modifier.height(12.dp))
            StaggerIn(index = 4) {
                ComposerField(
                    onSend = { text ->
                        // UI-safe через существующие callbacks: текст композера
                        // становится первым сообщением нового чата, навигация —
                        // через уже подключённый onOpenChat (NavHost не меняем).
                        val chat = chatRepository.newChat()
                        chatRepository.appendMessage(chat.id, text, true)
                        onOpenChat(chat)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))

            StaggerIn(index = 5) {
                ToolsGrid(onOpenChats = onOpenChats, onOpenComingSoon = onOpenComingSoon)
            }
            Spacer(Modifier.height(24.dp))

            StaggerIn(index = 6) {
                RecentChats(
                    chats = chats,
                    onOpenChat = onOpenChat,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Ряд быстрых действий под hero-карточкой. */
@Composable
private fun QuickActionsRow(
    onNewChat: () -> Unit,
    onOpenChats: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        QuickAction(
            icon = Icons.Outlined.Add,
            label = "Новый чат",
            onClick = onNewChat,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(12.dp))
        QuickAction(
            icon = Icons.Outlined.Edit,
            label = "Черновик",
            onClick = onOpenChats,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val visual = lumoVisual()
    val (tint, bg) = visual.avatarTints[0]
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(bg, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
            )
        }
    }
}

/**
 * Composer на Dashboard: поле ввода + кнопка отправки. Текст уходит первым
 * сообщением в новый чат.
 */
@Composable
private fun ComposerField(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val submit: () -> Unit = {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            text = ""
        }
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        border = null,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        "Спросите что-нибудь…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                },
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = colors.onSurface,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
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
                onClick = { submit() },
                enabled = text.isNotBlank(),
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
