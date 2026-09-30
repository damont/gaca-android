package dev.monts.gaca.ui.daily

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.BriefingItem
import dev.monts.gaca.data.repo.BriefingSection
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

/** The Today tab. Pure display: the API decides what sections exist, their order, and what's
 * in them; this just lays out whatever comes back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScreen(viewModel: DailyViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

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
                Text("Today", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                Text(state.date.format(dateFormatter), style = MaterialTheme.typography.bodyMedium, color = TextMeta)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previousDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", tint = HeaderAccent)
            }
            IconButton(onClick = viewModel::nextDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", tint = HeaderAccent)
            }
            if (!state.isToday) {
                TextButton(onClick = viewModel::today) { Text("Back to today", color = HeaderAccent) }
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

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            return@Column
        }

        val briefing = state.briefing
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            // Always a LazyColumn, even when empty — pull-to-refresh needs something scrollable.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (briefing == null || briefing.isEmpty) {
                    item {
                        Text(
                            emptyMessage(state.date, state.error != null),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMeta,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                        )
                    }
                } else {
                    if (!briefing.summary.isNullOrBlank()) {
                        item { SummaryCard(briefing.summary) }
                    }
                    items(briefing.sections, key = { it.key }) { SectionCard(it) }
                }
            }
        }
    }
}

private fun emptyMessage(date: LocalDate, failed: Boolean): String = when {
    failed -> "Couldn't load the briefing. Pull down to try again."
    date == LocalDate.now() -> "No briefing yet today. Your agent posts one each morning — pull down to check again."
    else -> "No briefing for this day."
}

@Composable
private fun SummaryCard(summary: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SuggestBackground).padding(16.dp),
    ) {
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

private fun iconFor(kind: String): ImageVector = when (kind) {
    "calendar" -> Icons.Filled.CalendarMonth
    "weather" -> Icons.Filled.WbSunny
    "news" -> Icons.Filled.Newspaper
    "sports" -> Icons.Filled.SportsSoccer
    "tasks" -> Icons.Filled.Checklist
    else -> Icons.Filled.AutoAwesome
}

@Composable
private fun SectionCard(section: BriefingSection) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ChipBackground.copy(alpha = 0.55f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(iconFor(section.kind), contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(22.dp))
            Column {
                Text(section.title, style = MaterialTheme.typography.titleMedium, color = HeaderAccent)
                if (!section.headline.isNullOrBlank()) {
                    Text(section.headline, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
        section.items.forEach { ItemRow(it) }
    }
}

@Composable
private fun ItemRow(item: BriefingItem) {
    val uriHandler = LocalUriHandler.current
    val tappable = item.link != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (tappable) {
                    // A bad or unhandled link must never crash the app.
                    Modifier.clickable { runCatching { uriHandler.openUri(item.link!!) } }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val dot = when (item.priority) {
            "high" -> SeverityUrgentDot
            "low" -> TextMeta.copy(alpha = 0.4f)
            else -> SeverityWarnDot
        }
        Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            val meta = listOfNotNull(item.time, item.location).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = TextMeta)
            }
            if (!item.body.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (tappable) {
                Text("Open link", style = MaterialTheme.typography.labelSmall, color = HeaderAccent)
            }
        }
    }
}
