package dev.monts.gaca.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Session
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor
import dev.monts.gaca.util.relativeTime

/** Every past chat, searchable. New chats are started from the Chat tab itself (a draft that
 * only becomes a saved session on its first send), so there is no "new chat" button here. */
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
    val shown = if (needle.isEmpty()) state.sessions else state.sessions.filter { s ->
        listOfNotNull(s.summary, s.title, s.preview, s.crew).any { it.contains(needle, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
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
            Column {
                Text("Chat history", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                val count = state.sessions.size
                if (count > 0) {
                    Text(
                        "$count conversation${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMeta,
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            placeholder = { Text("Search chats…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta) },
            singleLine = true,
        )

        val errorText = state.error
        if (errorText != null) {
            Text(
                errorText,
                style = MaterialTheme.typography.bodySmall,
                color = SeverityUrgentDot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HeaderAccent)
                }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (needle.isEmpty()) "No conversations yet." else "No chats match \"$needle\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMeta,
                        modifier = Modifier.padding(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { session ->
                        ChatSessionRow(session, crewByName[session.crew], onTap = { onOpenThread(session.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun ChatSessionRow(session: Session, crewMember: CrewMember?, onTap: () -> Unit) {
    val color = crewMember?.let { crewColor(it.color) } ?: TextSecondary
    val background = crewMember?.let { crewChipTint(it.color) } ?: ChipBackground
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onTap)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (crewMember?.name ?: session.crew ?: "?").take(2).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = OnAccent,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    session.summary ?: session.title ?: "New chat",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(session.createdAt), style = MaterialTheme.typography.bodySmall, color = TextMeta)
            }
            if (session.preview != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    session.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            (crewMember?.name ?: session.crew)?.let { crewName ->
                Spacer(Modifier.height(4.dp))
                Text(crewName, style = MaterialTheme.typography.labelSmall, color = color)
            }
            if (session.alertId != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                    Text("from an alert", style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}
