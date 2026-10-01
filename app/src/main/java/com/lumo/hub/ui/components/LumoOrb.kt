package com.lumo.hub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumo.hub.theme.lumoVisual

/**
 * Декоративный светящийся orb — главный визуальный фокус Dashboard
 * (DESIGN.md §4). Без функциональной нагрузки: мягкая пульсация + блик.
 */
@Composable
fun LumoOrb(modifier: Modifier = Modifier, size: Dp = 148.dp) {
    val visual = lumoVisual()
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "orbScale",
    )
    val sheenShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(5200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "orbSheen",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // внешнее свечение
        Box(
            Modifier
                .size(size + 44.dp)
                .drawBehind {
                    drawOrbGlow(visual.glow)
                },
        )
        // само тело orb
        Box(
            Modifier
                .size(size)
                .scale(breathe)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(visual.orbBrush)
                .drawBehind {
                    drawOrbSheen(sheenShift, visual.sheen)
                },
        )
    }
}

private fun DrawScope.drawOrbGlow(glow: Color) {
    val radius = size.minDimension / 2f
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = center,
                radius = radius,
            ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawOrbSheen(shift: Float, sheen: Color) {
    val r = size.minDimension / 2f
    val cx = size.width * (0.28f + 0.14f * shift)
    val cy = size.height * 0.26f
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(sheen, Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 0.62f,
            ),
        radius = r * 0.62f,
        center = Offset(cx, cy),
    )
}
