package dev.monts.gaca.ui.crewdetail

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import dev.monts.gaca.data.repo.CrewPurpose
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.CrewProfile
import dev.monts.gaca.data.repo.ProfileJob
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val whenFormatter = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")

/** An API timestamp in the phone's own zone and clock; falls back to the raw text if it can't be parsed. */
private fun formatWhen(iso: String?): String? = iso?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(whenFormatter) }.getOrDefault(it)
}

/** How a crew member is set up. Pure display — every grouping, ordering and phrase here comes
 * from the API; to change any of it, tap through to a chat with that crew member. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CrewDetailScreen(
    viewModel: CrewDetailViewModel,
    onBack: () -> Unit,
    onOpenThread: (sessionId: String, prefill: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile
    val accent = profile?.let { crewColor(it.color) } ?: HeaderAccent

    LaunchedEffect(state.openChatSessionId) {
        val sessionId = state.openChatSessionId
        if (sessionId != null) {
            onOpenThread(sessionId, "I'd like to change how you're set up: ")
            viewModel.consumeOpenChat()
        }
    }

    Column(Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, bottom = 10.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(ChipBackground).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            if (profile != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(profile.name.take(2).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = OnAccent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        if (profile.isDefault) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ChipBackground).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text("default", style = MaterialTheme.typography.labelSmall, color = HeaderAccent)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (profile.online) accent else SeverityUrgentDot))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (profile.online) "Online" else "Offline") + (profile.engine?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMeta,
                        )
                    }
                }
            } else {
                Text("Crew member", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(accent))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HeaderAccent) }
            profile == null -> Text(
                state.error ?: "Couldn't load this crew member.",
                style = MaterialTheme.typography.bodyMedium,
                color = SeverityUrgentDot,
                modifier = Modifier.padding(24.dp),
            )
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.error?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot) } }
                    if (profile.unavailable.isNotEmpty()) {
                        item {
                            Text(
                                "Couldn't read: ${profile.unavailable.joinToString(", ")}. Pull down to retry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SeverityWarnDot,
                            )
                        }
                    }
                    profile.purpose?.let { purpose ->
                        item { PurposeCard(purpose, profile.name) }
                    }
                    if (profile.modelName != null) {
                        item {
                            InfoCard("Model") {
                                Text(profile.modelName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                profile.modelProvider?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMeta) }
                            }
                        }
                    }
                    if (profile.channels.isNotEmpty()) {
                        item {
                            InfoCard("Reachable through") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profile.channels.forEach { c ->
                                        Chip(
                                            c.name + if (c.connected) "" else if (c.needsAttention) " · needs attention" else " · disconnected",
                                            dot = if (c.connected) accent else SeverityUrgentDot,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        InfoCard("Skills · ${profile.skillCount}") {
                            if (profile.skillCategories.isEmpty()) {
                                Text("None installed", style = MaterialTheme.typography.bodyMedium, color = TextMeta)
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profile.skillCategories.forEach { (name, count) -> Chip("$name · $count") }
                                }
                            }
                        }
                    }
                    if (profile.toolsets.isNotEmpty()) {
                        item {
                            InfoCard("Tools it can use") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profile.toolsets.forEach { Chip(it.label) }
                                }
                            }
                        }
                    }
                    item {
                        InfoCard("Scheduled · ${profile.jobs.size}") {
                            if (profile.jobs.isEmpty()) {
                                Text("Nothing scheduled", style = MaterialTheme.typography.bodyMedium, color = TextMeta)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { profile.jobs.forEach { JobRow(it) } }
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent)
                                .clickable(enabled = !state.startingChat, onClick = viewModel::startChat)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.startingChat) {
                                CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Change this in chat", style = MaterialTheme.typography.labelLarge, color = OnAccent)
                            }
                        }
                        Text(
                            "Setup is edited by talking to ${profile.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMeta,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ChipBackground.copy(alpha = 0.55f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = HeaderAccent)
        content()
    }
}

@Composable
private fun Chip(text: String, dot: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(PageBackground).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
private fun JobRow(job: ProfileJob) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(job.name, style = MaterialTheme.typography.bodyLarge, color = if (job.enabled) TextPrimary else TextMeta, modifier = Modifier.weight(1f, fill = false))
            if (!job.enabled) {
                Spacer(Modifier.width(8.dp))
                Text("paused", style = MaterialTheme.typography.labelSmall, color = SeverityWarnDot)
            }
        }
        Text(job.schedule, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        val meta = listOfNotNull(
            if (job.enabled) formatWhen(job.nextRun)?.let { "Next: $it" } else null,
            when (job.lastStatus) {
                null -> null
                "ok" -> "Last run succeeded"
                else -> "Last run: ${job.lastStatus}"
            },
            if (job.usesModel) null else "Runs a script, no AI",
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = TextMeta)
    }
}

/** What this crew member is dedicated to doing: the API's one-line summary up top, the full
 * standing instructions a tap away. A crew member with nothing specific set up says so plainly
 * instead of showing an empty card. */
@Composable
private fun PurposeCard(purpose: CrewPurpose, name: String) {
    InfoCard("Dedicated to") {
        if (purpose.isGeneric) {
            Text(
                "A general assistant — nothing specific has been set up for $name.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Text(
                "Tell it in chat what it should focus on, and it can take that on as its job.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMeta,
            )
            return@InfoCard
        }
        purpose.summary?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        val instructions = purpose.instructions
        if (!instructions.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            SelectionContainer {
                Text(
                    instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (expanded) "Show less" else "Show full instructions",
                style = MaterialTheme.typography.labelLarge,
                color = HeaderAccent,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
