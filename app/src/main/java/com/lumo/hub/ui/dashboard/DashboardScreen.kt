package com.lumo.hub.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.ChatSummary
import com.lumo.hub.data.WeatherRepository
import com.lumo.hub.ui.components.StaggerIn

/**
 * Dashboard в минимальном согласованном виде: приветствие, hero-карточка
 * AI Chat, сетка инструментов и недавние чаты. Верхняя панель (orb-логотип,
 * заголовок, настройки), подзаголовок, быстрые действия и composer убраны по
 * референсу — навигация и настройки доступны через нижнюю панель.
 */
@Composable
fun DashboardScreen(
    chats: List<ChatSummary>,
    weatherRepository: WeatherRepository,
    onOpenChats: () -> Unit,
    onOpenChat: (ChatSummary) -> Unit,
    onNewChat: () -> Unit,
    onOpenComingSoon: () -> Unit,
    onOpenWeather: () -> Unit,
) {
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
            Spacer(Modifier.height(16.dp))

            StaggerIn(index = 0) {
                GreetingBlock(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))

            StaggerIn(index = 1) {
                WeatherCard(repository = weatherRepository, onClick = onOpenWeather)
            }
            Spacer(Modifier.height(20.dp))

            StaggerIn(index = 2) {
                HeroChatCard(onNewChat = onNewChat)
            }
            Spacer(Modifier.height(24.dp))

            StaggerIn(index = 3) {
                ToolsGrid(onOpenChats = onOpenChats, onOpenComingSoon = onOpenComingSoon)
            }
            Spacer(Modifier.height(24.dp))

            StaggerIn(index = 4) {
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
