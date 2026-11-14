package dev.monts.gaca.ui.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import dev.monts.gaca.data.repo.Artifact
import dev.monts.gaca.data.repo.ArtifactBadge
import dev.monts.gaca.data.repo.ArtifactFilter
import dev.monts.gaca.data.repo.Template
import dev.monts.gaca.data.repo.badges
import dev.monts.gaca.data.repo.canManage
import dev.monts.gaca.data.repo.canShare
import dev.monts.gaca.data.repo.isSharedWithMe
import dev.monts.gaca.data.repo.peopleLine
import dev.monts.gaca.data.repo.sharedByLine
import dev.monts.gaca.data.repo.transferLine
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.SuccessBg
import dev.monts.gaca.ui.theme.SuccessInk
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactsScreen(
    viewModel: ArtifactsViewModel,
    onOpenSettings: () -> Unit,
    onOpenArtifact: (String) -> Unit,
    onJoinWithLink: () -> Unit,
    /** Builds the share sheet's ViewModel for one page (a fresh one each time the sheet opens). */
    shareFactory: (Artifact) -> androidx.lifecycle.ViewModelProvider.Factory,
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Artifact?>(null) }
    var pendingInstall by remember { mutableStateOf<Template?>(null) }
    var sharing by remember { mutableStateOf<Pair<Artifact, String>?>(null) }
    val gallery = state.filter == ArtifactFilter.Gallery

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Artifacts", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                Text("Pages your crew has made for you", style = MaterialTheme.typography.bodyMedium, color = TextMeta)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }

        if (state.artifacts.isNotEmpty() || state.query.isNotEmpty() || gallery) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                placeholder = { Text(if (gallery) "Search the gallery…" else "Search pages…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta) },
                singleLine = true,
            )
        }
        Row(Modifier.padding(horizontal = 22.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        if (state.filter == ArtifactFilter.Shared) {
            TextButton(onClick = onJoinWithLink, modifier = Modifier.padding(horizontal = 14.dp)) { Text("Join with a link") }
        }
        if (!gallery) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 2.dp)) {
                FilterChip(
                    selected = state.showArchived,
                    onClick = { viewModel.setShowArchived(!state.showArchived) },
                    label = { Text("Show archived") },
                )
            }
        }
        if (state.stale && !gallery) {
            Text(
                "The cloud could not be reached, so shared pages are as they were last time.",
                style = MaterialTheme.typography.bodySmall, color = TextMeta,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        state.notice?.let { notice ->
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall, color = SuccessInk,
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissNotice).padding(horizontal = 22.dp, vertical = 4.dp),
            )
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

        PullToRefreshBox(
            isRefreshing = if (gallery) state.gallery.loading && state.gallery.loaded else state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                gallery -> GalleryContent(
                    state = state,
                    onInstall = { pendingInstall = it },
                    onMore = viewModel::loadMoreGallery,
                    onRetry = viewModel::loadGallery,
                    onOpenSettings = onOpenSettings,
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HeaderAccent)
                }
                state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.artifacts.isEmpty() || state.filter == ArtifactFilter.Shared && state.artifacts.none { it.isSharedWithMe }) {
                            if (state.filter == ArtifactFilter.Shared) {
                                "Nothing has been shared with you yet. When someone sends you a link to a page, it will show up here."
                            } else {
                                "Nothing here yet. Ask your crew for a page — a Costco list, a trip planner, a tracker — and it will show up here."
                            }
                        } else {
                            "No pages match."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMeta,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(40.dp),
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    var draggingId by remember { mutableStateOf<String?>(null) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    // Reordering a filtered list would be ambiguous, so only offer it unfiltered.
                    val canReorder = state.query.isBlank()
                    val currentArtifacts by rememberUpdatedState(state.artifacts)

                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visible, key = { it.id }) { artifact ->
                            val dragging = draggingId == artifact.id
                            ArtifactRow(
                                artifact,
                                onOpen = { onOpenArtifact(artifact.id) },
                                onArchive = { viewModel.archive(artifact) },
                                onDelete = { pendingDelete = artifact },
                                onShare = if (artifact.canShare) {
                                    { sharing = artifact to java.util.UUID.randomUUID().toString() }
                                } else {
                                    null
                                },
                                onMoveToTop = if (canReorder && currentArtifacts.firstOrNull()?.id != artifact.id) {
                                    { viewModel.moveToTop(artifact) }
                                } else {
                                    null
                                },
                                dragHandle = if (!canReorder) null else Modifier.pointerInput(artifact.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = artifact.id; dragOffset = 0f },
                                        onDragEnd = { draggingId = null; dragOffset = 0f; viewModel.saveOrder() },
                                        onDragCancel = { draggingId = null; dragOffset = 0f; viewModel.saveOrder() },
                                    ) { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val items = listState.layoutInfo.visibleItemsInfo
                                        val me = items.firstOrNull { it.key == artifact.id } ?: return@detectDragGestures
                                        val center = me.offset + me.size / 2f + dragOffset
                                        val target = items.firstOrNull { it.key != artifact.id && center >= it.offset && center <= it.offset + it.size }
                                        if (target != null) {
                                            val from = currentArtifacts.indexOfFirst { it.id == artifact.id }
                                            val to = currentArtifacts.indexOfFirst { it.id == target.key }
                                            if (from >= 0 && to >= 0) {
                                                viewModel.move(from, to)
                                                // The row's layout position jumps to the target's slot; keep it under the finger.
                                                val newOffset = if (to > from) target.offset + target.size - me.size else target.offset
                                                dragOffset -= (newOffset - me.offset)
                                            }
                                        }
                                        // Keep the list scrolling while a row is held near an edge.
                                        val top = listState.layoutInfo.viewportStartOffset
                                        val bottom = listState.layoutInfo.viewportEndOffset
                                        if (center < top + 120) scope.launch { listState.scrollBy(-24f) }
                                        else if (center > bottom - 120) scope.launch { listState.scrollBy(24f) }
                                    }
                                },
                                modifier = if (dragging) {
                                    Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; shadowElevation = 12f }
                                } else {
                                    Modifier.animateItem()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    sharing?.let { (artifact, session) ->
        // `session` makes a new ViewModel every time the sheet opens, so an old link is never shown again.
        val shareVm = androidx.lifecycle.viewmodel.compose.viewModel<ShareViewModel>(key = "share-$session", factory = shareFactory(artifact))
        ShareSheet(shareVm, onDismiss = { sharing = null }, onOpenSettings = onOpenSettings)
    }

    pendingInstall?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text("Add “${template.title}”?") },
            text = { Text("A copy is made on your server and appears under Mine. It is private to you until you share it.") },
            confirmButton = { TextButton(onClick = { viewModel.install(template); pendingInstall = null }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { pendingInstall = null }) { Text("Cancel") } },
        )
    }

    pendingDelete?.let { artifact ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${artifact.title}”?") },
            text = { Text("The page and everything it stored will be gone for good. Archive it instead to just tidy it away.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(artifact); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ArtifactRow(
    artifact: Artifact,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)?,
    onMoveToTop: (() -> Unit)?,
    dragHandle: Modifier?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OnAccent)
            .clickable(onClick = onOpen)
            .padding(start = if (dragHandle != null) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle != null) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = TextMeta,
                modifier = Modifier.size(40.dp).padding(8.dp).then(dragHandle),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(artifact.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            artifact.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(
                artifact.crew,
                "updated ${relativeTime(artifact.updatedAt)}",
                if (artifact.archived) "archived" else null,
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = TextMeta)
            Spacer(Modifier.height(6.dp))
            BadgeRow(artifact)
        }
        if (onMoveToTop != null || onShare != null || artifact.canManage) Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextMeta)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onMoveToTop != null) {
                    DropdownMenuItem(text = { Text("Move to top") }, onClick = { menuOpen = false; onMoveToTop() })
                }
                if (onShare != null) {
                    DropdownMenuItem(text = { Text("Share…") }, onClick = { menuOpen = false; onShare() })
                }
                // Only the owner may archive or delete: the cloud refuses both to everyone else.
                if (artifact.canManage) {
                    DropdownMenuItem(
                        text = { Text(if (artifact.archived) "Unarchive" else "Archive") },
                        onClick = { menuOpen = false; onArchive() },
                    )
                    DropdownMenuItem(text = { Text("Delete…") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Private / Shared / Published, plus for somebody else's page the people icon, "Shared by …" and your role. */
@Composable
private fun BadgeRow(artifact: Artifact) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        artifact.badges.forEach { Tag(it.label, if (it == ArtifactBadge.Published) SuccessBg else ChipBackground, if (it == ArtifactBadge.Published) SuccessInk else HeaderAccent) }
        val who = artifact.sharedByLine ?: artifact.peopleLine
        if (who != null) {
            Icon(Icons.Filled.Group, contentDescription = "Shared", tint = TextMeta, modifier = Modifier.size(14.dp))
            Text(who, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (artifact.isSharedWithMe) Tag(artifact.role.label, ChipBackground, HeaderAccent)
        artifact.transferLine?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMeta) }
    }
}

@Composable
private fun Tag(text: String, background: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** The cloud's public templates: tap Add to get a private copy. */
@Composable
private fun GalleryContent(
    state: ArtifactsUiState,
    onInstall: (Template) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val g = state.gallery
    when {
        g.problem != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                Text(
                    when (val problem = g.problem) {
                        GalleryProblem.NotLinked -> "Link your server to GACA Cloud to browse the gallery."
                        GalleryProblem.NotConfigured -> "GACA Cloud isn't set up on this server."
                        is GalleryProblem.Failed -> problem.message
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium, color = TextMeta, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                when (g.problem) {
                    GalleryProblem.NotLinked -> Button(onClick = onOpenSettings) { Text("Open Settings") }
                    GalleryProblem.NotConfigured -> Unit
                    else -> TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
        !g.loaded || (g.loading && g.templates.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HeaderAccent)
        }
        state.visibleTemplates.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (g.templates.isEmpty()) "The gallery is empty for now." else "No templates match.",
                style = MaterialTheme.typography.bodyMedium, color = TextMeta, textAlign = TextAlign.Center, modifier = Modifier.padding(40.dp),
            )
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.visibleTemplates, key = { it.id }) { template ->
                TemplateRow(template, adding = g.installingId == template.id, enabled = g.installingId == null, onAdd = { onInstall(template) })
            }
            if (g.nextCursor != null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (g.loadingMore) CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = onMore) { Text("Show more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: Template, adding: Boolean, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OnAccent).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(template.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            template.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(template.author?.let { "by $it" }, template.category, if (template.downloads > 0) "${template.downloads} added" else null).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = TextMeta)
        }
        if (adding) {
            CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onAdd, enabled = enabled) { Text("Add") }
        }
    }
}
