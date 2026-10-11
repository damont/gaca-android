package dev.monts.gaca.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.monts.gaca.R
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.ui.common.BrandMark
import dev.monts.gaca.data.repo.Session
import dev.monts.gaca.ui.common.ChatComposer
import dev.monts.gaca.ui.common.ComposerError
import dev.monts.gaca.ui.common.CrewAvatar
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewColor
import dev.monts.gaca.ui.theme.crewInk
import dev.monts.gaca.ui.theme.crewSolid
import dev.monts.gaca.util.PhotoPrep
import dev.monts.gaca.util.relativeTime

/** The Chat tab: pick who to talk to, see recent chats, type — the first send creates the chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(
    viewModel: ChatHomeViewModel,
    onOpenThread: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.createdSessionId) {
        state.createdSessionId?.let {
            onOpenThread(it)
            viewModel.consumeCreatedSession()
        }
    }
    val resolver = LocalContext.current.contentResolver
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PhotoPrep.MAX_PHOTOS),
    ) { uris -> viewModel.addPhotos(resolver, uris) }
    val picked = state.picked

    Column(
        Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.statusBars).imePadding(),
    ) {
        HomeTitleBar(onOpenHistory, onOpenSettings)
        PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            HomeList(state, onSelect = viewModel::selectCrew, onOpenThread = onOpenThread, onOpenHistory = onOpenHistory)
        }
        ComposerError(state.error)
        ChatComposer(
            text = state.text,
            photos = state.photos,
            preparingPhotos = state.preparingPhotos,
            sending = state.sending,
            placeholder = "Message ${picked?.name ?: "your crew"}…",
            sendColor = picked?.let { crewSolid(it.color) } ?: HeaderAccent,
            onTextChange = viewModel::setText,
            onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::removePhoto,
            onSend = viewModel::send,
        )
    }
}

@Composable
private fun HomeTitleBar(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Chat", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 32.sp, lineHeight = 38.sp), color = HeaderAccent, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.Search, contentDescription = "Search all chats", tint = TextPrimary) }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextPrimary) }
    }
}

@Composable
private fun HomeList(state: ChatHomeUiState, onSelect: (String) -> Unit, onOpenThread: (String) -> Unit, onOpenHistory: () -> Unit) {
    val crewByName = state.crew.associateBy { it.name }
    val picked = state.picked
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
        if (state.crew.size > 1) {
            item { SectionLabel("New chat with", Modifier.padding(start = 22.dp, top = 12.dp, bottom = 6.dp)) }
            item { CrewPicker(state.crew, picked?.name, onSelect) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent", Modifier.weight(1f))
                if (state.sessions.isNotEmpty()) {
                    Text("See all", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = HeaderAccent, modifier = Modifier.clickable(onClick = onOpenHistory))
                }
            }
        }
        when {
            state.isLoading && state.sessions.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HeaderAccent) }
            }
            state.sessions.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandMark(R.drawable.ic_gaca_in_flight, "Gaca in flight", 132.dp)
                    Text(
                        if (state.crew.isEmpty()) "No crew configured yet — set one up in Settings first." else "Say something to start a new chat.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            else -> items(state.sessions.take(RECENT_CHATS), key = { it.id }) { session ->
                ChatSessionRow(session, crewByName[session.crew], onTap = { onOpenThread(session.id) })
            }
        }
    }
}

private const val RECENT_CHATS = 6

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp), color = TextMeta, modifier = modifier)
}

/** 54dp avatars in each crew member's color; the chosen one rings, the rest dim. */
@Composable
private fun CrewPicker(crew: List<CrewMember>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        crew.forEach { member ->
            val isSelected = member.name == selected
            Column(
                Modifier.width(68.dp).clickable { onSelect(member.name) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(62.dp)
                        .then(if (isSelected) Modifier.border(2.5.dp, crewColor(member.color), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    CrewAvatar(member.name, member.color, 54.dp, Modifier.alpha(if (isSelected) 1f else 0.55f))
                }
                Text(
                    member.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) crewInk(member.color) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One chat in a list: avatar, headline, time, preview, crew name, "from an alert". */
@Composable
internal fun ChatSessionRow(session: Session, crewMember: CrewMember?, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CrewAvatar(crewMember?.name ?: session.crew, crewMember?.color, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.summary ?: session.title ?: "New chat",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(session.lastActivityAt), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = TextMeta)
            }
            session.preview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (crewMember?.name ?: session.crew)?.let { name ->
                    Text(name, style = MaterialTheme.typography.labelMedium, color = crewMember?.let { crewInk(it.color) } ?: TextSecondary)
                }
                if (session.alertId != null) {
                    Text("·", style = MaterialTheme.typography.labelMedium, color = TextMeta)
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                    Text("from an alert", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
    }
}
