package dev.monts.gaca.ui.inbox

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Severity
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityInfoBg
import dev.monts.gaca.ui.theme.SeverityInfoDot
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor

/** Legal per api-contract.md §3's transition table: `delegated` and the two
 * terminal states only settle on their own, so a swipe there would just 409.
 * NEW covers both `new` and `completed` — both are triageable the same way. */
private fun InboxTab.isSwipeTriageable(): Boolean = this == InboxTab.NEW || this == InboxTab.SNOOZED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenAlert: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNewDelegation: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground) // paints edge-to-edge, behind the status bar
            .windowInsetsPadding(WindowInsets.statusBars), // ...while content stays clear of it —
            // the bottom nav bar below this screen (GacaNavHost) owns the bottom inset now
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Gaca", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
                val subtitle = if (state.activeTab != InboxTab.NEW) "" else {
                    val newCount = state.alerts.size
                    if (newCount == 0) "You're all caught up"
                    else "$newCount thing${if (newCount == 1) "" else "s"} need you"
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMeta)
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

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InboxTab.entries.forEach { tab ->
                TabPill(tab.label, tab == state.activeTab) { viewModel.selectTab(tab) }
            }
        }

        if (state.availableCrew.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabPill("All crew", state.crewFilter == null) { viewModel.selectCrewFilter(null) }
                state.availableCrew.forEach { member ->
                    CrewFilterPill(member, state.crewFilter == member.name) { viewModel.selectCrewFilter(member.name) }
                }
            }
        }

        // Built once per recomposition from the small crew list — cheap, and
        // lets each row look up its own poster's color by name in O(1).
        val crewByName = state.availableCrew.associateBy { it.name }

        val isEmpty = state.alerts.isEmpty() && !state.isLoading

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Always a LazyColumn, even for the empty states — PullToRefreshBox's
            // pull gesture is driven by nested scroll, which only fires from an
            // actually-scrollable child. A plain centered Box never dispatches
            // those events, so the empty inbox (the state you'll see most often)
            // would silently ignore a pull-down without this. fillParentMaxSize()
            // sizes the empty-state item to the viewport so it still centers
            // correctly instead of collapsing to its content height.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isEmpty) {
                    item {
                        Box(Modifier.fillParentMaxSize()) {
                            if (state.activeTab == InboxTab.NEW) EmptyRewardState() else QuietEmptyState(state.activeTab.label)
                        }
                    }
                } else {
                    items(state.alerts, key = { it.id }) { alert ->
                        key(alert.id) {
                            if (state.activeTab.isSwipeTriageable()) {
                                SwipeableAlertRow(
                                    alert = alert,
                                    crewByName = crewByName,
                                    onTap = { onOpenAlert(alert.id) },
                                    onDismissed = { viewModel.dismiss(alert.id) },
                                    onActed = { viewModel.act(alert.id) },
                                )
                            } else {
                                AlertRow(alert = alert, crewByName = crewByName, onTap = { onOpenAlert(alert.id) })
                            }
                        }
                    }
                }
            }
        }

        if (state.activeTab == InboxTab.NEW && state.alerts.isNotEmpty()) {
            Text(
                "swipe left to skip, right when it's handled",
                style = MaterialTheme.typography.bodySmall,
                color = TextMeta,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

        FloatingActionButton(
            onClick = onNewDelegation,
            modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
            containerColor = HeaderAccent,
            contentColor = OnAccent,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Start a delegation")
        }
    }
}

@Composable
private fun TabPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) HeaderAccent else ChipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) OnAccent else TextSecondary,
        )
    }
}

/** Same idea as [TabPill], but tinted in this crew member's own color instead
 * of the neutral palette — a solid fill when it's the active filter, a light
 * wash + dot otherwise, so you can tell crew apart before reading the name. */
@Composable
private fun CrewFilterPill(member: CrewMember, active: Boolean, onClick: () -> Unit) {
    val color = crewColor(member.color)
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) color else crewChipTint(member.color))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!active) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            }
            Text(
                member.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) OnAccent else color,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAlertRow(
    alert: Alert,
    crewByName: Map<String, CrewMember>,
    onTap: () -> Unit,
    onDismissed: () -> Unit,
    onActed: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDismissed(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onActed(); true }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
    ) {
        AlertRow(alert = alert, crewByName = crewByName, onTap = onTap)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (direction == SwipeToDismissBoxValue.StartToEnd) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(18.dp))
                Text("Handled", style = MaterialTheme.typography.labelSmall, color = HeaderAccent)
            }
        } else Spacer(Modifier)

        if (direction == SwipeToDismissBoxValue.EndToStart) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ignore", style = MaterialTheme.typography.labelSmall, color = TextMeta)
                Icon(Icons.Filled.Close, contentDescription = null, tint = TextMeta, modifier = Modifier.size(18.dp))
            }
        } else Spacer(Modifier)
    }
}

@Composable
private fun AlertRow(alert: Alert, crewByName: Map<String, CrewMember>, onTap: () -> Unit) {
    val (dotColor, cardBg) = severityColors(alert.severity)
    val poster = alert.crew?.let { crewByName[it] }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable(onClick = onTap)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                if (alert.links.isNotEmpty()) {
                    // A hint that there's a page to open in the alert itself.
                    Icon(Icons.Filled.Link, contentDescription = "Has a link", tint = TextMeta, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    dev.monts.gaca.util.relativeTime(alert.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMeta,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                alert.body ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(alert.kind, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                if (poster != null) {
                    val posterColor = crewColor(poster.color)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(crewChipTint(poster.color))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(posterColor))
                        Text(poster.name, style = MaterialTheme.typography.labelSmall, color = posterColor, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
                // A `completed` alert sits in the same tab as `new` ones —
                // this is what tells them apart at a glance.
                if (alert.effectiveStatus == "completed") {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(dotColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Hermes replied", style = MaterialTheme.typography.labelSmall, color = dotColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRewardState() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(ChipBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Nothing needs you right now",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Hermes is watching. You'll hear from it when something does.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMeta,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun QuietEmptyState(tabLabel: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            "Nothing ${tabLabel.lowercase()} right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMeta,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

internal fun severityColors(severity: Severity): Pair<Color, Color> = when (severity) {
    Severity.URGENT -> SeverityUrgentDot to SeverityUrgentBg
    Severity.WARN -> SeverityWarnDot to SeverityWarnBg
    Severity.INFO -> SeverityInfoDot to SeverityInfoBg
}
