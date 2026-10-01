package com.lumo.hub.ui.comingsoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumo.hub.ui.components.LumoOrb
import com.lumo.hub.ui.components.SoonChip

/** Экран-заглушка для будущих модулей (DESIGN.md §4). */
@Composable
fun ComingSoonScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                    tint = colors.onBackground,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        LumoOrb(size = 110.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Этот модуль в работе",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        SoonChip()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Здесь появятся новые сервисы Lumo Hub. Мы сообщим, когда всё будет готово.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.weight(1.3f))
    }
}
