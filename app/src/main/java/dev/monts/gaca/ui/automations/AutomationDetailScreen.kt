package dev.monts.gaca.ui.automations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.data.repo.AutomationRun
import dev.monts.gaca.data.repo.RunStatus
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.clockTime
import dev.monts.gaca.util.dayAndTime
import dev.monts.gaca.util.durationLabel

/** One automation: when it runs, "Run now", and its run history with each run's output. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDetailScreen(
    viewModel: AutomationDetailViewModel,
    onBack: () -> Unit,
    onOpenThread: (sessionId: String, prefill: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val automation = state.automation

    LaunchedEffect(state.openChatSessionId) {
        val sessionId = state.openChatSessionId
        if (sessionId != null) {
            onOpenThread(sessionId, state.openChatPrefill)
            viewModel.consumeOpenChat()
        }
    }

    Column(Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Text(automation?.name ?: "Automation", style = MaterialTheme.typography.titleLarge, color = HeaderAccent, modifier = Modifier.weight(1f), maxLines = 2)
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HeaderAccent) }
            return@Column
        }
        if (automation == null) {
            Text(state.error ?: "This automation couldn't be loaded.", color = TextMeta, modifier = Modifier.padding(24.dp))
            return@Column
        }

        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Summary(automation, state, viewModel) }
                state.error?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot) } }
                item {
                    Text(
                        "RUN HISTORY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
                state.runsError?.let { item { Text("Couldn't read the run history: $it", style = MaterialTheme.typography.bodyMedium, color = TextMeta) } }
                if (state.runs.isEmpty() && state.runsError == null) {
                    item { Text("No runs yet.", style = MaterialTheme.typography.bodyMedium, color = TextMeta, modifier = Modifier.padding(4.dp)) }
                }
                items(state.runs, key = { it.id }) { run ->
                    RunRow(run, open = run.id in state.expanded, asking = state.startingChat, onToggle = { viewModel.toggle(run.id) }, onAsk = { viewModel.askAbout(run) })
                }
            }
        }
    }
}

@Composable
private fun Summary(a: Automation, state: AutomationDetailUiState, viewModel: AutomationDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(a.status)
            Text(
                a.schedule + (a.cronExpr?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Button(
            onClick = viewModel::runNow,
            enabled = !state.requestingRun,
            colors = ButtonDefaults.buttonColors(containerColor = HeaderAccent, contentColor = OnAccent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (state.requestingRun) CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Run now")
        }
        if (state.runRequested) {
            Text("Requested. It starts within about a minute — pull down to see it.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Success", state.successLine ?: "—", Modifier.weight(1f))
            Stat("Typical run", state.typicalRun ?: "—", Modifier.weight(1f))
            Stat("Next", a.nextRun?.let { dayAndTime(it) } ?: "—", Modifier.weight(1f))
        }
        RunDots(a.recent)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = OnAccent, border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
private fun RunRow(run: AutomationRun, open: Boolean, asking: Boolean, onToggle: () -> Unit, onAsk: () -> Unit) {
    val failed = run.status == RunStatus.FAILED
    val output = run.error ?: run.summary
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = OnAccent,
        border = BorderStroke(1.dp, if (failed) SeverityUrgentDot else BorderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RunBadge(run.status)
                Column(Modifier.weight(1f)) {
                    Text(run.startedAt?.let { dayAndTime(it) } ?: "Unknown time", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (!open && output != null) {
                        Text(output, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                run.durationSeconds?.let { Text(durationLabel(it), fontSize = 13.sp, color = TextSecondary) }
            }
            if (open) {
                if (output != null) {
                    Text(
                        output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(PageBackground).padding(10.dp),
                    )
                } else {
                    Text("It had nothing to say.", style = MaterialTheme.typography.bodySmall, color = TextMeta, modifier = Modifier.padding(top = 8.dp))
                }
                if (failed) {
                    TextButton(
                        onClick = onAsk,
                        enabled = !asking,
                        modifier = Modifier.padding(top = 6.dp).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = HeaderAccent),
                    ) { Text("Ask about this") }
                }
            }
        }
    }
}
