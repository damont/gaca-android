package dev.monts.gaca.ui.chatthread

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.MessageActivity
import dev.monts.gaca.data.repo.MessageArtifact
import dev.monts.gaca.ui.common.CrewAvatar
import dev.monts.gaca.ui.common.LocalArtifactLinks
import dev.monts.gaca.ui.common.MarkdownText
import dev.monts.gaca.ui.theme.ActivityPill
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.UserBubble
import dev.monts.gaca.ui.theme.crewInk
import dev.monts.gaca.ui.theme.crewTint
import kotlinx.coroutines.delay

/** Your message: a compact right-aligned slate bubble with a small tail corner, photos above the text. */
@Composable
fun UserBubble(message: ConversationMessage, imageLoader: suspend (String) -> Bitmap?) {
    Row(Modifier.fillMaxWidth().alpha(if (message.pending) 0.55f else 1f), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(UserBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            message.localPreviews.forEach { BubblePhoto(it) }
            if (message.localPreviews.isEmpty()) {
                message.attachments.forEach { a ->
                    val bitmap by produceState<Bitmap?>(null, a.url) { value = imageLoader(a.url) }
                    bitmap?.let { BubblePhoto(it) } ?: Box(Modifier.size(180.dp, 120.dp).clip(RoundedCornerShape(16.dp)).background(TextSecondary))
                }
            }
            if (message.displayContent.isNotBlank()) {
                SelectionContainer {
                    Text(message.displayContent, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 22.sp), color = OnAccent)
                }
            }
        }
    }
}

@Composable
private fun BubblePhoto(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(180.dp, 120.dp).clip(RoundedCornerShape(16.dp)),
    )
}

/** The small header over a crew member's turn: a 22dp avatar and their name in the crew's ink color. */
@Composable
private fun TurnHeader(member: CrewMember?, fallbackName: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CrewAvatar(member?.name ?: fallbackName, member?.color, 22.dp)
        Text(
            member?.name ?: fallbackName ?: "Crew",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = member?.let { crewInk(it.color) } ?: TextSecondary,
        )
    }
}

/** A crew member's reply: not a bubble, a full-width turn of activity chip, Markdown and page cards. */
@Composable
fun CrewTurn(message: ConversationMessage, member: CrewMember?, fallbackName: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TurnHeader(member, fallbackName)
        message.activity?.let { ActivityChip(it) }
        if (message.displayContent.isNotBlank()) {
            SelectionContainer { MarkdownText(message.displayContent) }
        }
        message.artifacts.forEach { PageCard(it, member) }
    }
}

/** A check plus the server's label ("Ran terminal 3× · 41s"); tap to see each tool. */
@Composable
private fun ActivityChip(activity: MessageActivity) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(ActivityPill).clickable { open = !open }.padding(horizontal = 12.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Text(activity.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = TextSecondary, modifier = Modifier.weight(1f, fill = false))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = TextMeta, modifier = Modifier.size(16.dp))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                activity.tools.forEach { t ->
                    val tally = buildString {
                        append("×${t.count}")
                        if (t.errors > 0) append(" · ${t.errors} failed")
                    }
                    Text("${t.name}  $tally", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = TextSecondary)
                }
            }
        }
    }
}

/** A white card for a page the crew member made: crew-tinted icon, title, description, chevron. */
@Composable
private fun PageCard(artifact: MessageArtifact, member: CrewMember?) {
    val env = LocalArtifactLinks.current
    val enabled = !artifact.missing && env != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .clickable(enabled = enabled) { env?.open?.invoke(artifact.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(member?.let { crewTint(it.color) } ?: ActivityPill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = member?.let { crewInk(it.color) } ?: HeaderAccent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(artifact.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = if (artifact.missing) TextMeta else TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val sub = if (artifact.missing) "This page is no longer available" else artifact.description
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = TextSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (!artifact.missing) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMeta)
    }
}

/** The turn in flight: header, a live activity pill (pulsing dot, tool, timer), then the streamed text with a caret. */
@Composable
fun LiveTurn(member: CrewMember?, fallbackName: String?, tool: String?, startedAtMillis: Long?, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TurnHeader(member, fallbackName)
        LiveActivityRow(member, tool, startedAtMillis)
        if (text.isNotEmpty()) MarkdownText("$text ▍")
    }
}

@Composable
private fun LiveActivityRow(member: CrewMember?, tool: String?, startedAtMillis: Long?) {
    val ink = member?.let { crewInk(it.color) } ?: TextSecondary
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val elapsed by produceState(0L, startedAtMillis) {
        while (true) {
            value = startedAtMillis?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0
            delay(1000)
        }
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(member?.let { crewTint(it.color) } ?: ActivityPill).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(pulse).clip(CircleShape).background(ink))
        Text(if (tool != null) "Using $tool" else "Working", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = ink)
        if (startedAtMillis != null) Text(formatElapsed(elapsed), style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = ink.copy(alpha = 0.75f))
    }
}

internal fun formatElapsed(seconds: Long): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${"%02d".format(seconds % 60)}s"

/** Small grey "Today 8:42 AM" between messages more than an hour apart. */
@Composable
fun DayDivider(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = TextMeta, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
