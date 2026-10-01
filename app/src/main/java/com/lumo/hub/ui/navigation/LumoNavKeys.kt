package com.lumo.hub.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Ключи навигации (Navigation 3). Мок-карусель редактирования и диалоги
 * открываются локально на экранах, в стек они не попадают.
 */
@Serializable
data object Dashboard : NavKeyMarker

@Serializable
data object ChatsList : NavKeyMarker

@Serializable
data object Settings : NavKeyMarker

@Serializable
data class Conversation(val chatId: String, val title: String) : NavKeyMarker

@Serializable
data object ComingSoon : NavKeyMarker

/** Маркер, объединяющий ключи с [androidx.navigation3.runtime.NavKey]. */
sealed interface NavKeyMarker : androidx.navigation3.runtime.NavKey
