package dev.monts.gaca.ui.automations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.dayAndTime

/** The Automations tab: what the crew does on a schedule, whether the last run worked, and when it runs next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(viewModel: AutomationsViewModel, onOpenSettings: () -> Unit, onOpenAutomation: (crew: String, id: String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Automations", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                if (!state.isLoading && state.error == null) {
                    val n = state.automations.size
                    val failing = state.failingCount
                    val summary = "$n ${if (n == 1) "job" else "jobs"}" + if (failing > 0) " · $failing need${if (failing == 1) "s" else ""} attention" else ""
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary) }
        }

        if (state.automations.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationFilter.entries.forEach { f ->
                    val count = when (f) {
                        AutomationFilter.ALL -> state.automations.size
                        AutomationFilter.FAILING -> state.failingCount
                        AutomationFilter.PAUSED -> state.pausedCount
                    }
                    val selected = state.filter == f
                    Text(
                        "${f.label} $count",
                        fontSize = 14.sp,
                        color = if (selected) OnAccent else TextPrimary,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) HeaderAccent else ChipBackground)
                            .clickable { viewModel.setFilter(f) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        if (state.isLoading) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            return@Column
        }

        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Always a LazyColumn, even when empty — pull-to-refresh needs something scrollable.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { item { Note(it, SeverityUrgentDot) } }
                if (state.unavailable.isNotEmpty()) {
                    item { Note("Couldn't read ${state.unavailable.joinToString(", ")}'s automations right now.", SeverityUrgentDot) }
                }
                if (state.shown.isEmpty() && state.error == null) {
                    item {
                        Note(
                            if (state.automations.isEmpty()) "No automations yet. Ask a crew member in chat to do something on a schedule."
                            else "Nothing here.",
                            TextMeta,
                        )
                    }
                }
                items(state.shown, key = { "${it.crew}/${it.id}" }) { AutomationCard(it) { onOpenAutomation(it.crew, it.id) } }
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp))
}

@Composable
private fun AutomationCard(a: Automation, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = OnAccent,
        border = BorderStroke(1.dp, if (a.needsAttention) SeverityUrgentDot else BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatusPill(a.status)
            }
            Text(a.schedule, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            RunDots(a.recent, Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(lastRunLine(a, ::dayAndTime), style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                a.nextRun?.let { Text("Next ${dayAndTime(it)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
        }
    }
}
