package dev.monts.gaca.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.Severity
import dev.monts.gaca.ui.common.ConversationBubble
import dev.monts.gaca.ui.inbox.severityColors
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.crewColor
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.relativeTime

private fun severityLabel(severity: Severity): String = when (severity) {
    Severity.URGENT -> "Urgent"
    Severity.WARN -> "Heads up"
    Severity.INFO -> "FYI"
}

@Composable
fun AlertDetailScreen(
    viewModel: AlertDetailViewModel,
    onBack: () -> Unit,
    onOpenChat: (sessionId: String, draft: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    LaunchedEffect(state.openChatSessionId) {
        val sessionId = state.openChatSessionId
        if (sessionId != null) {
            onOpenChat(sessionId, state.openChatDraft)
            viewModel.consumeOpenChat() // otherwise coming back here re-fires this every time
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ChipBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("ALERT", style = MaterialTheme.typography.labelMedium, color = TextMeta)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            state.alert == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Couldn't load this alert", color = TextMeta)
            }
            else -> AlertDetailBody(state, viewModel)
        }
    }
}

@Composable
private fun AlertDetailBody(state: AlertDetailUiState, viewModel: AlertDetailViewModel) {
    val alert = state.alert!!
    val (dotColor, _) = severityColors(alert.severity)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.error != null) {
            Text(state.error, style = MaterialTheme.typography.bodySmall, color = dev.monts.gaca.ui.theme.SeverityUrgentDot)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Text(severityLabel(alert.severity), style = MaterialTheme.typography.labelLarge, color = dotColor)
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(ChipBackground)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(alert.kind, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Text(alert.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(
            "from ${alert.source} · ${relativeTime(alert.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMeta,
        )
        if (!alert.body.isNullOrBlank()) {
            Text(alert.body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        // Whatever the run (or a person) last said about this alert — the
        // crew member's own output on a `completed` run, the error on a failed
        // one, or a note left when it was acted/dismissed. Also present in
        // CONVERSATION below when there's a session, but that's the full
        // transcript; this is the one-line version worth seeing immediately.
        if (!alert.resolutionNote.isNullOrBlank()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ChipBackground).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("NOTE", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                Text(alert.resolutionNote, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        if (alert.dataRows.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ChipBackground).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                alert.dataRows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMeta)
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        if (alert.suggestedActions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HERMES SUGGESTS", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                alert.suggestedActions.forEachIndexed { index, action ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuggestBackground)
                            .clickable { viewModel.pickSuggestedAction(index) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(action.label, style = MaterialTheme.typography.labelLarge, color = HeaderAccent)
                    }
                }
            }
        }

        if (state.conversationLoading || state.conversation.isNotEmpty()) {
            // Which crew member this alert's session is actually with — advisory (api-contract.md
            // §3), but the best available answer for "whose color are these replies in."
            val conversationCrewColor = state.crew.firstOrNull { it.name == alert.crew }?.color
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONVERSATION", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                if (state.conversationLoading) {
                    CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.conversation.forEach { message -> ConversationBubble(message, conversationCrewColor) }
                    }
                }
            }
        }

        // Only worth showing when there's an actual choice still to make — once
        // delegated, a session is bound to one crew member for its whole life
        // (no mid-thread swap), so the picker no longer means anything.
        if (state.crew.size > 1 && alert.sessionId == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CREW", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.crew.forEach { member ->
                        val selected = member.name == state.selectedCrew
                        val memberColor = crewColor(member.color)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) memberColor else ChipBackground)
                                .clickable { viewModel.selectCrew(member.name) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!selected) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(memberColor))
                                }
                                Text(
                                    member.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) OnAccent else TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // "Take this to chat" — the other half of the bidirectional alert↔chat link
        // (api-contract.md §6): the API opens (or reuses) the chat, marks the alert
        // handled, and hands back a draft that lands in the chat's message box for the
        // person to edit and send. The way to keep talking to this alert's crew member.
        val chatCrewMember = state.crew.firstOrNull { it.name == state.selectedCrew }
        if (state.selectedCrew != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))

            val chatColor = chatCrewMember?.let { crewColor(it.color) } ?: HeaderAccent
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(chatColor)
                    .clickable(enabled = !state.openingChat, onClick = viewModel::openFullChat)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (state.openingChat) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Column {
                        Text(
                            "Take this to chat with ${state.selectedCrew} →",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnAccent,
                        )
                        Text(
                            "Opens the chat with this alert ready to edit — nothing is sent until you send it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAccent.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        if (state.showSnoozeOptions) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.snoozeOptions().forEach { option ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChipBackground)
                            .clickable { viewModel.snooze(option) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            DashedActionButton("Snooze", onClick = viewModel::toggleSnooze)
        }

        OutlinedTextField(
            value = state.noteText,
            onValueChange = viewModel::setNoteText,
            placeholder = { Text("Add a note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ChipBackground)
                    .clickable(onClick = viewModel::dismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Ignore", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HeaderAccent)
                    .clickable(onClick = viewModel::act)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("I handled it", style = MaterialTheme.typography.labelLarge, color = OnAccent)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DashedActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextMeta)
    }
}
