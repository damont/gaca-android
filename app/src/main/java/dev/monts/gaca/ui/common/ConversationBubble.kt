package dev.monts.gaca.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor

/**
 * One turn of a conversation with a crew member — shared by [dev.monts.gaca.ui.detail.AlertDetailScreen]'s
 * "CONVERSATION" section and [dev.monts.gaca.ui.chatthread.ChatThreadScreen]'s full thread, so the two
 * never drift apart in behavior. The crew member's own replies sit right-aligned and tinted in
 * their own color (`crewColorHex`, when known); everything else (the user's own messages, tool
 * activity) sits left-aligned in the neutral chip color. Falls back to the old fixed
 * [SuggestBackground] when no color is available, e.g. a session whose crew member no longer
 * resolves.
 */
@Composable
fun ConversationBubble(message: ConversationMessage, crewColorHex: String? = null) {
    val fromCrew = message.role == "assistant"
    val bubbleColor = if (fromCrew) {
        crewColorHex?.let { crewChipTint(it) } ?: SuggestBackground
    } else {
        ChipBackground
    }
    val labelColor = if (fromCrew && crewColorHex != null) crewColor(crewColorHex) else TextMeta

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromCrew) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                message.role.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(message.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
