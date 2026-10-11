package dev.monts.gaca.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.Session
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.crewSolid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** History: every past chat, with an always-visible search field, crew filter pills, and
 * Today / This week / Earlier groups. New chats start from the Chat tab's home. */
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Re-fetches each time this screen is shown — the ViewModel outlives a round trip into a thread.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val crewByName = state.crew.associateBy { it.name }
    val needle = state.query.trim()
    val shown = state.sessions.filter { s ->
        (state.crewFilter == null || s.crew == state.crewFilter) &&
            (needle.isEmpty() || listOfNotNull(s.summary, s.title, s.preview, s.crew).any { it.contains(needle, ignoreCase = true) })
    }

    Column(Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.statusBars)) {
        HistoryHeader(onBack)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            placeholder = { Text("Search chats") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        if (state.crew.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill("All", null, state.crewFilter == null) { viewModel.setCrewFilter(null) }
                state.crew.forEach { m ->
                    FilterPill(m.name, crewSolid(m.color), state.crewFilter == m.name) { viewModel.setCrewFilter(m.name) }
                }
            }
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp))
        }
        HistoryBody(state.isLoading && state.sessions.isEmpty(), shown, state.sessions.isEmpty(), needle) { session ->
            ChatSessionRow(session, crewByName[session.crew], onTap = { onOpenThread(session.id) })
        }
    }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 22.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("History", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
    }
}

@Composable
private fun FilterPill(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) (color ?: HeaderAccent) else ChipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) OnAccent else TextPrimary)
    }
}

@Composable
private fun HistoryBody(
    loading: Boolean,
    shown: List<Session>,
    noSessions: Boolean,
    needle: String,
    row: @Composable (Session) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HeaderAccent) }
            shown.isEmpty() -> Text(
                when {
                    noSessions -> "No conversations yet."
                    needle.isEmpty() -> "No chats with this crew member."
                    else -> "No chats match \"$needle\"."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMeta,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                groupByRecency(shown).forEach { (title, sessions) ->
                    item(key = "h-$title") { SectionLabel(title, Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp)) }
                    sessions.forEach { s -> item(key = s.id) { row(s) } }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** Today / This week / Earlier, by each chat's last activity. */
internal fun groupByRecency(sessions: List<Session>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, List<Session>>> {
    val today = LocalDate.now(zone)
    val weekAgo = today.minusDays(7)
    fun dayOf(s: Session): LocalDate? = runCatching { Instant.parse(s.lastActivityAt).atZone(zone).toLocalDate() }.getOrNull()
    val groups = sessions.groupBy { s ->
        val day = dayOf(s)
        when {
            day == null -> "Earlier"
            day == today -> "Today"
            day >= weekAgo -> "This week"
            else -> "Earlier"
        }
    }
    return listOf("Today", "This week", "Earlier").mapNotNull { title -> groups[title]?.let { title to it } }
}
