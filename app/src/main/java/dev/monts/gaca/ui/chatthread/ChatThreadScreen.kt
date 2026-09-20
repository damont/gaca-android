package dev.monts.gaca.ui.chatthread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.ui.common.ConversationBubble
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.crewColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatThreadScreen(viewModel: ChatThreadViewModel, onBack: () -> Unit, onOpenAlert: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val member = state.crewMember
    val accent = member?.let { crewColor(it.color) } ?: HeaderAccent
    val listState = rememberLazyListState()
    val messages = state.sessionDetail?.messages.orEmpty()
    val liveText = state.liveAssistantText
    val imeVisible = WindowInsets.isImeVisible

    // The list below is reverseLayout — index 0 is the newest item and sits at the
    // bottom by construction, so "scroll to the newest message" is always just
    // "scroll to index 0," never a chase after an unknown item height. (A plain,
    // non-reversed list's animateScrollToItem(lastIndex) only guarantees the ITEM's
    // top is visible, not its tail end — for a long final reply that meant landing
    // at its start, not its most recent text.) Re-fires on send, refresh, a run
    // settling, every streamed delta, and the keyboard opening.
    LaunchedEffect(state.newMessageTick, liveText, imeVisible) {
        if (messages.isNotEmpty() || liveText != null) listState.animateScrollToItem(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            // Edge-to-edge (MainActivity's enableEdgeToEdge()) means the keyboard's own
            // inset isn't reserved automatically — without this, the keyboard just
            // overlaps the input row instead of the layout making room above it.
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            if (member != null) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(member.name.take(2).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = OnAccent)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.sessionDetail?.session?.title ?: member?.name ?: state.sessionDetail?.session?.crew ?: "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                val status = state.runStatus
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent))

        val alertId = state.sessionDetail?.session?.alertId
        if (alertId != null) {
            Row(
                Modifier
                    .padding(horizontal = 22.dp)
                    .padding(top = 12.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChipBackground)
                    .clickable { onOpenAlert(alertId) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = TextMeta, modifier = Modifier.size(14.dp))
                Text(
                    "From alert: ${state.fromAlertTitle ?: "…"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMeta,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMeta, modifier = Modifier.size(14.dp))
            }
        }

        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            state.sessionDetail == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Couldn't load this chat", color = TextMeta)
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    // Always a LazyColumn, even when empty — same reason as Inbox's:
                    // PullToRefreshBox's gesture needs an actually-scrollable child to
                    // dispatch from, which a plain centered Box never does.
                    //
                    // reverseLayout = true: index 0 renders at the BOTTOM, growing upward —
                    // the standard chat-list trick so the newest message is always anchored
                    // to the bottom (even when the whole thread is shorter than the screen),
                    // instead of chasing a scroll offset after the fact.
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (messages.isEmpty() && liveText == null) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Say hello to ${member?.name ?: "them"} to get started.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMeta,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(40.dp),
                                    )
                                }
                            }
                        } else {
                            if (liveText != null) {
                                // Reassembled from `assistant.delta` events, not yet a persisted
                                // Message — rendered with the same bubble so it looks identical to
                                // the real one it becomes once `run.completed` lands. Declared
                                // first so reverseLayout puts it at the very bottom — it's always
                                // the newest thing on screen while it exists.
                                item(key = "live-reply") {
                                    ConversationBubble(
                                        ConversationMessage(id = "live-reply", role = "assistant", content = liveText.ifEmpty { "…" }, createdAt = ""),
                                        member?.color,
                                    )
                                }
                            }
                            items(messages.asReversed(), key = { it.id }) { message -> ConversationBubble(message, member?.color) }
                        }
                    }
                }
            }
        }

        val errorText = state.error
        if (errorText != null && state.sessionDetail != null) {
            Text(
                errorText,
                style = MaterialTheme.typography.bodySmall,
                color = SeverityUrgentDot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.messageText,
                onValueChange = viewModel::setMessageText,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message${member?.let { " " + it.name } ?: ""}…") },
            )
            val canSend = state.messageText.isNotBlank() && !state.sending
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) accent else BorderColor)
                    .clickable(enabled = canSend, onClick = viewModel::send),
                contentAlignment = Alignment.Center,
            ) {
                if (state.sending) {
                    CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = OnAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
