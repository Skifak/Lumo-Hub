package com.lumo.hub.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = LumoBlue,
        onPrimary = Color.White,
        primaryContainer = SurfaceTintLight,
        onPrimaryContainer = LumoBlueDeep,
        secondary = LumoLavender,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECE8FF),
        onSecondaryContainer = Color(0xFF3E3585),
        tertiary = LumoPeach,
        onTertiary = Color(0xFF4A2B12),
        tertiaryContainer = SurfacePeachTint,
        onTertiaryContainer = Color(0xFF5C3A1E),
        background = BgWarmWhite,
        onBackground = TextGraphite,
        surface = BgWarmWhite,
        onSurface = TextGraphite,
        surfaceContainerLow = Color(0xFFF2F1ED),
        surfaceContainer = SurfaceWhite,
        surfaceContainerHigh = SurfaceWhite,
        surfaceVariant = Color(0xFFEFF0F6),
        onSurfaceVariant = TextMutedLight,
        outline = Color(0xFFD9DCE6),
        outlineVariant = StrokeLight,
        inverseSurface = Color(0xFF2B2D38),
        inverseOnSurface = Color(0xFFF2F1F5),
        scrim = Color(0xFF17181D),
    )

private val DarkColors =
    darkColorScheme(
        primary = LumoBlueNight,
        onPrimary = Color(0xFF17205E),
        primaryContainer = SurfaceTintDark,
        onPrimaryContainer = Color(0xFFD9DEFF),
        secondary = LumoLavenderNight,
        onSecondary = Color(0xFF2A2358),
        secondaryContainer = Color(0xFF34305C),
        onSecondaryContainer = Color(0xFFE3DEFF),
        tertiary = LumoPeachNight,
        onTertiary = Color(0xFF432B14),
        tertiaryContainer = SurfacePeachTintDark,
        onTertiaryContainer = Color(0xFFF6D9BE),
        background = BgGraphite,
        onBackground = TextSoftWhite,
        surface = BgGraphite,
        onSurface = TextSoftWhite,
        surfaceContainerLow = Color(0xFF1A1C23),
        surfaceContainer = SurfaceGraphite,
        surfaceContainerHigh = SurfaceGraphiteHigh,
        surfaceVariant = Color(0xFF232633),
        onSurfaceVariant = TextMutedDark,
        outline = Color(0xFF484C5C),
        outlineVariant = StrokeDark,
        inverseSurface = Color(0xFFE9E8EE),
        inverseOnSurface = Color(0xFF1D1E24),
        scrim = Color(0xFF0B0C10),
    )

/**
 * Дополнительные «брендовые» атрибуты поверх Material 3: градиенты orb и
 * hero-карточки, оттенки аватаров и статус «Скоро». Держим отдельно, чтобы не
 * перегружать MaterialTheme.colorScheme.
 */
@Immutable
data class LumoVisual(
    val orbBrush: Brush,
    val heroBrush: Brush,
    val logoBrush: Brush,
    val soon: Color,
    val avatarTints: List<Pair<Color, Color>>,
    val sheen: Color,
    val glow: Color,
)

private val LightVisual =
    LumoVisual(
        orbBrush =
            Brush.linearGradient(
                0f to LumoBlue,
                0.55f to LumoLavender,
                1f to LumoPeach,
            ),
        heroBrush =
            Brush.linearGradient(
                0f to Color(0xFF6474FF),
                0.6f to Color(0xFF9B8CFF),
                1f to Color(0xFFFFB98F),
            ),
        logoBrush =
            Brush.linearGradient(0f to LumoBlue, 1f to LumoLavender),
        soon = StatusSoonLight,
        avatarTints =
            listOf(
                LumoBlue to SurfaceTintLight,
                LumoLavender to Color(0xFFECE8FF),
                LumoPeach to SurfacePeachTint,
                LumoTeal to Color(0xFFE2F5F0),
            ),
        sheen = Color(0x33FFFFFF),
        glow = Color(0x2E6474FF),
    )

private val DarkVisual =
    LumoVisual(
        orbBrush =
            Brush.linearGradient(
                0f to Color(0xFF7484E8),
                0.55f to Color(0xFF8E7BE0),
                1f to Color(0xFFD89E78),
            ),
        heroBrush =
            Brush.linearGradient(
                0f to Color(0xFF4653B8),
                0.6f to Color(0xFF6D5BC4),
                1f to Color(0xFFB0785A),
            ),
        logoBrush =
            Brush.linearGradient(0f to LumoBlueNight, 1f to LumoLavenderNight),
        soon = StatusSoonDark,
        avatarTints =
            listOf(
                LumoBlueNight to Color(0xFF2B3050),
                LumoLavenderNight to Color(0xFF322E54),
                LumoPeachNight to Color(0xFF3B3040),
                LumoTealNight to Color(0xFF243B38),
            ),
        sheen = Color(0x26FFFFFF),
        glow = Color(0x3D5F6FC4),
    )

val LocalLumoVisual = staticCompositionLocalOf { LightVisual }

/** Тема Lumo Hub: светлая и тёмная схемы + брендовые визуальные атрибуты. */
@Composable
fun LumoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val visual = if (darkTheme) DarkVisual else LightVisual
    CompositionLocalProvider(LocalLumoVisual provides visual) {
        MaterialTheme(
            colorScheme = colors,
            typography = LumoTypography,
            shapes = LumoShapes,
            content = content,
        )
    }
}

/** Быстрый доступ к брендовым атрибутам: `val lumo = lumoVisual()` */
@Composable
fun lumoVisual(): LumoVisual = LocalLumoVisual.current
