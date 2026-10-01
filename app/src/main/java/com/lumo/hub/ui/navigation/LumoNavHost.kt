package com.lumo.hub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lumo.hub.data.ChatRepository
import com.lumo.hub.data.SettingsRepository
import com.lumo.hub.data.ThemeMode
import com.lumo.hub.network.OpenAiClient
import com.lumo.hub.network.OpenAiProvider
import com.lumo.hub.ui.chats.ChatsScreen
import com.lumo.hub.ui.comingsoon.ComingSoonScreen
import com.lumo.hub.ui.components.LumoBottomBar
import com.lumo.hub.ui.components.LumoTab
import com.lumo.hub.ui.conversation.ConversationScreen
import com.lumo.hub.ui.dashboard.DashboardScreen
import com.lumo.hub.ui.settings.SettingsScreen

/**
 * Корневая навигация Lumo Hub: три раздела с нижней панелью (Dashboard, Чаты,
 * Настройки) + вложенные экраны (разговор, заглушка) без панели.
 */
@Composable
fun LumoNavHost(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val chatRepository = remember(context) { ChatRepository(context) }
    val openAiClient = remember { OpenAiClient() }
    val backStack = rememberNavBackStack(Dashboard)

    // Безопасный back: на корневом разделе уводим на Dashboard, чтобы стек
    // никогда не оставался пустым.
    val goBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            backStack.clear()
            backStack.add(Dashboard)
        }
    }
    val lastKey = backStack.lastOrNull()
    val showBottomBar =
        lastKey is Dashboard || lastKey is ChatsList || lastKey is Settings
    val selectedTab =
        when {
            lastKey is ChatsList -> LumoTab.CHATS
            lastKey is Settings -> LumoTab.SETTINGS
            else -> LumoTab.HOME
        }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge: фон рисуется под статус-баром, контент — ниже него.
            .statusBarsPadding(),
    ) {
        NavDisplay(
            modifier = Modifier.weight(1f),
            backStack = backStack,
            onBack = { goBack() },
            entryProvider =
                entryProvider {
                    entry<Dashboard> {
                        DashboardScreen(
                            chats = chatRepository.chats.collectAsState().value,
                            onOpenChats = { backStack.add(ChatsList) },
                            onOpenChat = { chat ->
                                backStack.add(Conversation(chat.id, chat.title))
                            },
                            onNewChat = {
                                val chat = chatRepository.newChat()
                                backStack.add(Conversation(chat.id, chat.title))
                            },
                            onOpenComingSoon = { backStack.add(ComingSoon) },
                            onOpenSettings = { backStack.add(Settings) },
                        )
                    }
                    entry<ChatsList> {
                        ChatsScreen(
                            chats = chatRepository.chats.collectAsState().value,
                            onBack = { goBack() },
                            onOpenChat = { chat ->
                                backStack.add(Conversation(chat.id, chat.title))
                            },
                            onNewChat = {
                                val chat = chatRepository.newChat()
                                backStack.add(Conversation(chat.id, chat.title))
                            },
                            onDeleteChat = { chatRepository.deleteChat(it) },
                        )
                    }
                    entry<Conversation> { key ->
                        ConversationScreen(
                            title = key.title,
                            chatId = key.chatId,
                            onBack = { goBack() },
                        )
                    }
                    entry<Settings> {
                        val settings by settingsRepository.state.collectAsState()
                        SettingsScreen(
                            state = settings,
                            onThemeChange = { settingsRepository.setThemeMode(it) },
                            onProviderChange = { settingsRepository.updateProvider(it) },
                            onSaveProvider = { settingsRepository.saveProvider() },
                            onCheckConnection = { provider ->
                                runCatching {
                                    openAiClient.listModels(OpenAiProvider(provider.baseUrl, provider.apiKey)).size
                                }
                            },
                            onToggleApiKeyVisibility = {
                                settingsRepository.toggleApiKeyVisibility()
                            },
                        )
                    }
                    entry<ComingSoon> {
                        ComingSoonScreen(onBack = { goBack() })
                    }
                },
        )
        if (showBottomBar) {
            LumoBottomBar(
                selected = selectedTab,
                onSelect = { tab ->
                    val target =
                        when (tab) {
                            LumoTab.HOME -> Dashboard
                            LumoTab.CHATS -> ChatsList
                            LumoTab.SETTINGS -> Settings
                        }
                    if (backStack.lastOrNull() != target) {
                        backStack.clear()
                        backStack.add(target)
                    }
                },
            )
        }
    }
}

/** Тема, выбранная в настройках, поднимается сюда для [com.lumo.hub.theme.LumoTheme]. */
@Composable
fun rememberThemeMode(settingsRepository: SettingsRepository): ThemeMode =
    settingsRepository.state.collectAsStateWithLifecycle().value.themeMode
