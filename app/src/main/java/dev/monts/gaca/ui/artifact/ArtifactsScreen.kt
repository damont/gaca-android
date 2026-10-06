package dev.monts.gaca.ui.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.monts.gaca.data.repo.Artifact
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
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Artifact?>(null) }

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

        if (state.artifacts.isNotEmpty() || state.query.isNotEmpty()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                placeholder = { Text("Search pages…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMeta) },
                singleLine = true,
            )
        }
        Row(Modifier.padding(horizontal = 22.dp, vertical = 2.dp)) {
            FilterChip(
                selected = state.showArchived,
                onClick = { viewModel.setShowArchived(!state.showArchived) },
                label = { Text("Show archived") },
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
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HeaderAccent)
                }
                state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.artifacts.isEmpty()) {
                            "Nothing here yet. Ask your crew for a page — a Costco list, a trip planner, a tracker — and it will show up here."
                        } else {
                            "No pages match."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMeta,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(40.dp),
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visible, key = { it.id }) { artifact ->
                        ArtifactRow(
                            artifact,
                            onOpen = { onOpenArtifact(artifact.id) },
                            onArchive = { viewModel.archive(artifact) },
                            onDelete = { pendingDelete = artifact },
                        )
                    }
                }
            }
        }
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
private fun ArtifactRow(artifact: Artifact, onOpen: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OnAccent)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextMeta)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (artifact.archived) "Unarchive" else "Archive") },
                    onClick = { menuOpen = false; onArchive() },
                )
                DropdownMenuItem(text = { Text("Delete…") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}
