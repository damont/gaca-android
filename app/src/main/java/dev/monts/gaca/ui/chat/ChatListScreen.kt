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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenSettings: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Re-fetches every time this screen is navigated back to, not just once at
    // construction — this ViewModel outlives a round trip into a thread and back
    // (same NavBackStackEntry), so without this a session created in that round
    // trip would never show up until the app restarted. Same pattern InboxScreen
    // already uses for its own pull-to-refresh-adjacent reload.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.createdSessionId) {
        val createdId = state.createdSessionId
        if (createdId != null) {
            onOpenThread(createdId)
            viewModel.consumeCreatedSession() // otherwise coming back here re-fires this every time
        }
    }

    val crewByName = state.crew.associateBy { it.name }

    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("Chat", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                    val count = state.sessions.size
                    Text(
                        if (count == 0) "" else "$count conversation${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMeta,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
                }
            }

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
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HeaderAccent)
                    }
                    state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No conversations yet — tap \"New chat\" to start one.",
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
                        items(state.sessions, key = { it.id }) { session ->
                            ChatSessionRow(session, crewByName[session.crew], onTap = { onOpenThread(session.id) })
                        }
                        item { Spacer(Modifier.height(72.dp)) } // clears the "New chat" pill
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::openCrewPicker,
            modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
            containerColor = HeaderAccent,
            contentColor = OnAccent,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Start a new chat")
        }
    }

    if (state.showCrewPicker) {
        CrewPickerSheet(
            crew = state.crew,
            creating = state.creatingSession,
            onPick = viewModel::startSession,
            onDismiss = viewModel::dismissCrewPicker,
        )
    }
}

@Composable
private fun ChatSessionRow(session: Session, crewMember: CrewMember?, onTap: () -> Unit) {
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

/** "5. Choose crew (sheet)" — lists this account's real crew members (name, color,
 * default flag). No fabricated per-member description; the API doesn't have one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrewPickerSheet(
    crew: List<CrewMember>,
    creating: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 26.dp)) {
            Text("Start a chat with…", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            if (crew.isEmpty()) {
                Text("No crew configured yet — set one up in Settings first.", style = MaterialTheme.typography.bodySmall, color = TextMeta)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    crew.forEach { member ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(crewChipTint(member.color))
                                .clickable(enabled = !creating) { onPick(member.name) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(crewColor(member.color)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    member.name.take(2).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnAccent,
                                )
                            }
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(member.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                if (member.isDefault) {
                                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(OnAccent).padding(horizontal = 7.dp, vertical = 1.dp)) {
                                        Text("DEFAULT", style = MaterialTheme.typography.labelSmall, color = crewColor(member.color))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (creating) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}
