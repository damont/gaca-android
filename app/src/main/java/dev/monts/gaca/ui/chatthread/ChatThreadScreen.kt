package dev.monts.gaca.ui.chatthread

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.ConversationMessage
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Severity
import dev.monts.gaca.push.ActiveChat
import dev.monts.gaca.ui.common.ChatComposer
import dev.monts.gaca.ui.common.rememberTakePhoto
import dev.monts.gaca.ui.common.ComposerError
import dev.monts.gaca.ui.common.CrewAvatar
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityInfoBg
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewSolid
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.relativeTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    onBack: () -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenCrew: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val sessionId = state.sessionDetail?.session?.id
    // Tell the push service which thread is on screen — only while RESUMED, so a
    // backgrounded app still gets its notifications.
    LifecycleResumeEffect(sessionId) {
        ActiveChat.sessionId = sessionId
        onPauseOrDispose { if (ActiveChat.sessionId == sessionId) ActiveChat.sessionId = null }
    }
    val resolver = LocalContext.current.contentResolver
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PhotoPrep.MAX_PHOTOS),
    ) { uris -> viewModel.addPhotos(resolver, uris) }
    val takePhoto = rememberTakePhoto { uri -> viewModel.addPhotos(resolver, listOf(uri)) }
    val member = state.crewMember

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            // Edge-to-edge: the keyboard's inset isn't reserved automatically.
            .imePadding(),
    ) {
        ThreadHeader(state, onBack, onOpenCrew)
        state.fromAlert?.let { AlertCard(it) { onOpenAlert(it.id) } }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ThreadBody(state, viewModel)
        }
        ComposerError(state.error.takeIf { state.sessionDetail != null })
        ChatComposer(
            text = state.messageText,
            photos = state.pendingPhotos,
            preparingPhotos = state.preparingPhotos,
            sending = state.sending,
            placeholder = "Message ${member?.name ?: "them"}…",
            sendColor = member?.let { crewSolid(it.color) } ?: HeaderAccent,
            onTextChange = viewModel::setMessageText,
            onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onTakePhoto = takePhoto,
            onRemovePhoto = viewModel::removePhoto,
            onSend = viewModel::send,
            skills = state.skills,
        )
    }
}

/** Centered avatar and crew name; under it, what the run is doing, else the chat's summary. Tap for Crew detail. */
@Composable
private fun ThreadHeader(state: ChatThreadUiState, onBack: () -> Unit, onOpenCrew: (String) -> Unit) {
    val member = state.crewMember
    val session = state.sessionDetail?.session
    val status = when {
        state.running && state.currentTool != null -> "Using ${state.currentTool}…"
        state.running && state.liveText.isEmpty() -> "Working…"
        state.running -> "Writing…"
        else -> session?.summary ?: session?.title
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Box(
            Modifier.align(Alignment.CenterStart).size(36.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        val open = member?.let { m -> Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenCrew(m.name) } } ?: Modifier
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).then(open).padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CrewAvatar(member?.name ?: session?.crew, member?.color, 22.dp)
                Text(member?.name ?: session?.crew ?: "Chat", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = TextPrimary)
            }
            if (status != null) {
                Text(status, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = TextMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Pinned at the top when the chat began from an alert: a severity-tinted bell, age, title, chevron. */
@Composable
private fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val (bg, ink) = when (alert.severity) {
        Severity.URGENT -> SeverityUrgentBg to SeverityUrgentDot
        Severity.WARN -> SeverityWarnBg to SeverityWarnDot
        Severity.INFO -> SeverityInfoBg to TextSecondary
    }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${alert.severity.name} ALERT · ${relativeTime(alert.createdAt)}", style = MaterialTheme.typography.labelSmall, color = ink)
            Text(alert.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMeta)
    }
}

@Composable
private fun ThreadBody(state: ChatThreadUiState, viewModel: ChatThreadViewModel) {
    val detail = state.sessionDetail
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HeaderAccent) }
        detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Couldn't load this chat", color = TextMeta)
        }
        else -> MessageList(state, detail.messages, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageList(state: ChatThreadUiState, messages: List<ConversationMessage>, viewModel: ChatThreadViewModel) {
    val listState = rememberLazyListState()
    val member = state.crewMember
    val crewName = state.sessionDetail?.session?.crew
    val rows = remember(messages) { threadRows(messages).asReversed() }
    val imeVisible = WindowInsets.isImeVisible
    // reverseLayout puts index 0 (the newest) at the bottom, so "scroll to the newest" is always
    // "scroll to 0" — never a chase after an unknown item height.
    LaunchedEffect(state.newMessageTick, state.liveText, imeVisible) {
        if (rows.isNotEmpty() || state.running) listState.animateScrollToItem(0)
    }
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        // Always a LazyColumn, even when empty: the pull gesture needs a scrollable child.
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
        ) {
            if (state.running) {
                item(key = "live-turn") { LiveTurn(member, crewName, state.currentTool, state.runStartedAtMillis, state.liveText) }
            }
            if (rows.isEmpty() && !state.running) {
                item(key = "empty") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Say hello to ${member?.name ?: "them"} to get started.", style = MaterialTheme.typography.bodyMedium, color = TextMeta, textAlign = TextAlign.Center, modifier = Modifier.padding(40.dp))
                    }
                }
            }
            items(rows, key = { it.message.id }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.divider?.let { DayDivider(it) }
                    if (row.message.role == "assistant") {
                        CrewTurn(row.message, member, crewName)
                    } else {
                        UserBubble(row.message, viewModel::loadImage)
                    }
                }
            }
        }
    }
}

internal data class ThreadRow(val message: ConversationMessage, val divider: String?)

/** Pairs each message with a "Today 8:42 AM" label when it follows the previous one by over an hour. */
internal fun threadRows(messages: List<ConversationMessage>): List<ThreadRow> {
    var previous: Instant? = null
    return messages.map { m ->
        val at = runCatching { Instant.parse(m.createdAt) }.getOrNull()
        val gap = previous != null && at != null && Duration.between(previous, at) > Duration.ofHours(1)
        if (at != null) previous = at
        ThreadRow(m, if (gap && at != null) dividerLabel(at) else null)
    }
}

private fun dividerLabel(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val local = at.atZone(zone)
    val time = local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    val today = LocalDate.now(zone)
    val day = when (local.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> local.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }
    return "$day $time"
}
