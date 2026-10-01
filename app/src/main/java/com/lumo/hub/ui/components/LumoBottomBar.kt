package com.lumo.hub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Пункты нижней навигации Lumo Hub (DESIGN.md §4). */
enum class LumoTab(val label: String, val icon: ImageVector) {
    HOME("Главная", Icons.Outlined.Home),
    CHATS("Чаты", Icons.Outlined.ChatBubbleOutline),
    SETTINGS("Настройки", Icons.Outlined.Settings),
}

/** Нижняя навигация: спокойная, без плашек-капсул, один акцент. */
@Composable
fun LumoBottomBar(
    selected: LumoTab,
    onSelect: (LumoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        LumoTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                        indicatorColor = colors.primary.copy(alpha = 0.12f),
                    ),
            )
        }
    }
}
