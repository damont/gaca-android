package dev.monts.gaca.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.crewColor

/** A crew member's round avatar: their color with the first two letters of their name. */
@Composable
fun CrewAvatar(name: String?, colorHex: String?, size: Dp, modifier: Modifier = Modifier) {
    val fill: Color = colorHex?.let { crewColor(it) } ?: HeaderAccent
    Box(
        modifier.size(size).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (name ?: "?").take(2).replaceFirstChar { it.uppercase() },
            style = TextStyle(fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.SemiBold),
            color = OnAccent,
        )
    }
}
