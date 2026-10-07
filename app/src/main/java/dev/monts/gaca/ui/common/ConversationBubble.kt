package dev.monts.gaca.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.monts.gaca.ui.theme.BorderColor
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.extractArtifactRefs
import androidx.compose.runtime.remember
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.crewColor

/**
 * One turn of a conversation with a crew member — shared by [dev.monts.gaca.ui.detail.AlertDetailScreen]'s
 * "CONVERSATION" section and [dev.monts.gaca.ui.chatthread.ChatThreadScreen]'s full thread, so the two
 * never drift apart in behavior. The crew member's replies sit left-aligned in a solid bubble of
 * their own color (`crewColorHex`, when known; [HeaderAccent] otherwise, e.g. a session whose crew
 * member no longer resolves); the user's own messages sit right-aligned in the neutral chip color.
 */
@Composable
fun ConversationBubble(
    message: ConversationMessage,
    crewColorHex: String? = null,
    /** Fetches a photo by its API-relative url (with auth). When null, photos aren't shown. */
    imageLoader: (suspend (String) -> Bitmap?)? = null,
) {
    val fromCrew = message.role == "assistant"
    val bubbleColor = if (fromCrew) crewColorHex?.let { crewColor(it) } ?: HeaderAccent else ChipBackground
    val textColor = if (fromCrew) {
        if (bubbleColor.luminance() > 0.5f) TextPrimary else OnAccent
    } else {
        TextPrimary
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromCrew) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            message.localPreviews.forEach { PhotoThumb(it) }
            if (imageLoader != null && message.localPreviews.isEmpty()) {
                message.attachments.forEach { attachment ->
                    val bitmap by produceState<Bitmap?>(null, attachment.url) { value = imageLoader(attachment.url) }
                    bitmap?.let { PhotoThumb(it) } ?: Box(
                        Modifier.fillMaxWidth().height(120.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(BorderColor),
                    )
                }
            }
            val (text, artifactRefs) = remember(message.content) { extractArtifactRefs(message.content) }
            if (text.isNotBlank()) {
                // Long-press to select and copy, e.g. to paste a reply somewhere else.
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            // Pages the crew member made show as tappable chips instead of a raw gaca:// address.
            artifactRefs.forEach { ref -> ArtifactChip(ref.id, ref.label, Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun PhotoThumb(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Photo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}
