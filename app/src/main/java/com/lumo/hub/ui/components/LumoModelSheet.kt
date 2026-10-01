package com.lumo.hub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumo.hub.theme.lumoVisual

/**
 * Кастомный глобальный picker моделей в стиле Lumo (замена стандартным
 * dropdown/exposed-меню): нижний лист с фирменным drag-handle, поиском по
 * длинным спискам, градиентным маркером выбора и мягкой подсветкой выбранного
 * пункта. Используется всеми экранами выбора моделей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoModelSheet(
    title: String,
    subtitle: String,
    models: List<String>,
    selected: String,
    emptyText: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val visual = lumoVisual()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(models, query) {
            if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.outlineVariant),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(visual.logoBrush),
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (models.size > 5) {
                ModelSearchField(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(12.dp))
            }

            when {
                models.isEmpty() -> {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (onOpenSettings != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onDismiss(); onOpenSettings() }) {
                            Text("Открыть настройки")
                        }
                    }
                }
                filtered.isEmpty() -> {
                    Text(
                        text = "По запросу «$query» моделей нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filtered, key = { it }) { model ->
                            val isSelected = model == selected
                            Surface(
                                onClick = { onSelect(model) },
                                shape = MaterialTheme.shapes.large,
                                color =
                                    if (isSelected) colors.primary.copy(alpha = 0.08f)
                                    else Color.Transparent,
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Маркер выбора: в выбранном — фирменный градиент,
                                    // в остальных — мягкая точка-заготовка.
                                    Box(
                                        Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.background(visual.logoBrush, CircleShape)
                                                } else {
                                                    Modifier.background(
                                                        colors.outlineVariant.copy(alpha = 0.55f),
                                                        CircleShape,
                                                    )
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isSelected,
                                            enter = scaleIn(initialScale = 0.5f, animationSpec = tween(160)) +
                                                fadeIn(tween(160)),
                                            exit = scaleOut(targetScale = 0.5f, animationSpec = tween(160)) +
                                                fadeOut(tween(160)),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = "Выбрана",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.size(12.dp))
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (isSelected) colors.primary else colors.onSurface,
                                        fontWeight =
                                            if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Компактный поиск по списку моделей внутри листа. */
@Composable
private fun ModelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Поиск модели…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                },
                singleLine = true,
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
            androidx.compose.animation.AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Очистить поиск",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
