package com.lumo.hub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumo.hub.theme.lumoVisual

/** Маленькие «лего-детали» интерфейса: заголовок секции, чип «Скоро», аватар. */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
fun SoonChip(modifier: Modifier = Modifier) {
    Text(
        text = "Скоро",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = lumoVisual().soon,
        modifier =
            modifier
                .background(
                    lumoVisual().soon.copy(alpha = 0.13f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Аватар чата: первая буква в мягкой пастельной подложке. */
@Composable
fun ChatAvatar(
    title: String,
    tintIndex: Int,
    modifier: Modifier = Modifier,
) {
    val visual = lumoVisual()
    val pair = visual.avatarTints[tintIndex.mod(visual.avatarTints.size)]
    val (iconTint, bg) = pair
    Box(
        modifier =
            modifier
                .size(40.dp)
                .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = iconTint,
        )
    }
}
