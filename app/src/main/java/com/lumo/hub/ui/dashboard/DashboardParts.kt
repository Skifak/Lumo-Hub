package com.lumo.hub.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ChatSummary
import com.lumo.hub.data.WeatherRepository
import com.lumo.hub.network.ForecastResponse
import com.lumo.hub.theme.lumoVisual
import com.lumo.hub.ui.components.ChatAvatar
import com.lumo.hub.ui.components.SectionTitle
import com.lumo.hub.ui.components.StaggerIn
import java.util.Calendar

/** Приветствие по времени суток — компактный заголовок Dashboard. */
@Composable
fun GreetingBlock(modifier: Modifier = Modifier) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello =
        when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
    Text(
        text = hello,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun WeatherCard(repository: WeatherRepository, onClick: () -> Unit) {
    var forecast by remember { mutableStateOf<ForecastResponse?>(null) }
    val location = repository.savedLocation()
    LaunchedEffect(location) { if (location != null) forecast = runCatching { repository.forecast() }.getOrNull() }
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.size(8.dp))
                Text("Погода", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                Text("Открыть", style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Spacer(Modifier.height(8.dp))
            if (location == null) {
                Text("Добавьте город, чтобы увидеть прогноз", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else if (forecast?.current == null) {
                Text(location.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text("Загрузка прогноза", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${forecast!!.current!!.temperatureCelsius.formatOne()} °C", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                    Spacer(Modifier.size(8.dp))
                    Text(location.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

private fun Double.formatOne(): String = String.format(java.util.Locale.US, "%.1f", this)

/** Крупная hero-карточка AI Chat с CTA «Новый чат». */
@Composable
fun HeroChatCard(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = lumoVisual()
    Card(
        onClick = onNewChat,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            Modifier
                .background(visual.heroBrush)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "AI Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    HeroButton(text = "Новый чат", onClick = onNewChat)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Задайте вопрос или начните диалог со своим провайдером",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun HeroButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = Color.White,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Сетка сервисов: AI Chat активен, остальные — «Скоро». */
@Composable
fun ToolsGrid(
    onOpenChats: () -> Unit,
    onOpenComingSoon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(text = "Инструменты")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(
                title = "AI Chat",
                subtitle = "Диалоги с моделью",
                icon = Icons.Outlined.Psychology,
                active = true,
                onClick = onOpenChats,
                modifier = Modifier.weight(1f),
            )
            ToolCard(
                title = "Умные грядки",
                subtitle = "Скоро",
                icon = Icons.Outlined.Forest,
                active = false,
                onClick = onOpenComingSoon,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(
                title = "Модуль 3",
                subtitle = "Скоро",
                icon = Icons.Outlined.GridView,
                active = false,
                onClick = onOpenComingSoon,
                modifier = Modifier.weight(1f),
            )
            ToolCard(
                title = "Модуль 4",
                subtitle = "Скоро",
                icon = Icons.Outlined.GridView,
                active = false,
                onClick = onOpenComingSoon,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val visual = lumoVisual()
    val (tint, bg) = visual.avatarTints[if (active) 0 else 1]
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = colors.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) tint else visual.soon,
            )
        }
    }
}

/** Блок «Недавние чаты»: до трёх карточек-строк. */
@Composable
fun RecentChats(
    chats: List<ChatSummary>,
    onOpenChat: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionTitle(text = "Недавние чаты")
        Spacer(Modifier.height(12.dp))
        if (chats.isEmpty()) {
            Text(
                text = "Пока нет диалогов — начните первый чат",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chats.take(3).forEachIndexed { index, chat ->
                    StaggerIn(index = index) {
                        RecentChatRow(chat = chat, onClick = { onOpenChat(chat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentChatRow(
    chat: ChatSummary,
    onClick: () -> Unit,
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
            Spacer(Modifier.size(8.dp))
            Text(
                text = chat.time,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowOutward,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(16.dp),
            )
        }
    }
}
