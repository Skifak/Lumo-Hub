package com.lumo.hub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Появление контента: короткая функциональная анимация (DESIGN.md §5) —
 * лёгкий fade + сдвиг вверх со stagger-задержкой по индексу.
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val shift = remember { Animatable(14f) }
    LaunchedEffect(Unit) {
        val delayMillis = (index.coerceAtLeast(0) * 70).coerceAtMost(500)
        delay(delayMillis.toLong())
        launch { alpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
        launch { shift.animateTo(0f, tween(340, easing = FastOutSlowInEasing)) }
    }
    Box(
        modifier =
            modifier.graphicsLayer {
                this.alpha = alpha.value
                translationY = shift.value
            },
    ) {
        content()
    }
}
