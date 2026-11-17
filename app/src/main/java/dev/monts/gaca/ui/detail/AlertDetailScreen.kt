package dev.monts.gaca.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.monts.gaca.data.repo.Alert
import dev.monts.gaca.data.repo.AlertAction
import dev.monts.gaca.data.repo.AlertLink
import dev.monts.gaca.data.repo.CrewMember
import dev.monts.gaca.data.repo.Severity
import dev.monts.gaca.data.repo.crewLabel
import dev.monts.gaca.ui.common.ArtifactChip
import dev.monts.gaca.ui.common.DraftCard
import dev.monts.gaca.ui.inbox.severityColors
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentInk
import dev.monts.gaca.ui.theme.SuccessBg
import dev.monts.gaca.ui.theme.SuccessDot
import dev.monts.gaca.ui.theme.SuccessInk
import dev.monts.gaca.ui.theme.SuggestBackground
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.ui.theme.crewChipTint
import dev.monts.gaca.ui.theme.crewColor
import dev.monts.gaca.util.relativeTime

private fun severityLabel(severity: Severity): String = when (severity) {
    Severity.URGENT -> "Urgent"
    Severity.WARN -> "Heads up"
    Severity.INFO -> "FYI"
}

/**
 * One alert. This screen only shows what the API says (`alert.actions`, `alert.workflow`) and sends the
 * taps back: which actions can be tapped, what they read as, which buttons close the alert, and what the
 * status line says are all the API's decisions, so every app behaves the same.
 */
@Composable
fun AlertDetailScreen(
    viewModel: AlertDetailViewModel,
    onBack: () -> Unit,
    onOpenChat: (sessionId: String, draft: String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    LaunchedEffect(state.openChatSessionId) {
        val sessionId = state.openChatSessionId
        if (sessionId != null) {
            onOpenChat(sessionId, state.openChatDraft)
            viewModel.consumeOpenChat() // otherwise coming back here re-fires this every time
        }
    }

    // Coming back from the mail app (or anywhere): read the alert again, it may have moved.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 20.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ChipBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("ALERT", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HeaderAccent)
            }
            state.alert == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Couldn't load this alert", color = TextMeta)
            }
            else -> AlertDetailBody(state, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertDetailBody(state: AlertDetailUiState, viewModel: AlertDetailViewModel) {
    val alert = state.alert!!
    val workflow = alert.workflow

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.error != null) {
            Text(state.error, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentInk)
        }

        StatusRow(alert, state.crew)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(alert.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(
                "from ${alert.source} · ${relativeTime(alert.createdAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        if (!alert.body.isNullOrBlank()) {
            Text(alert.body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        // An HTML page (artifact) the crew member made for this alert, e.g. a comparison or a checklist.
        alert.artifactId?.let { ArtifactChip(it, "Open page") }

        if (alert.links.isNotEmpty()) {
            val uriHandler = LocalUriHandler.current
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alert.links.forEach { link -> LinkPill(link) { runCatching { uriHandler.openUri(link.url) } } }
            }
        }

        if (alert.dataRows.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ChipBackground).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                alert.dataRows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        if (alert.actions.isNotEmpty()) {
            ActionsSection(alert, state.busy, viewModel::run)
        }

        ChatEntry(alert, state.openingChat, viewModel::openChat)

        if (workflow.closeOptions.isNotEmpty()) {
            CloseSection(alert, viewModel::close)
        } else if (workflow.closedNote != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Text(workflow.closedNote, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Severity (or, when the API has something to say, where the alert stands) plus the kind and crew chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(alert: Alert, crew: List<CrewMember>) {
    val (dotColor, _) = severityColors(alert.severity)
    val label = alert.workflow.phaseLabel
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            val (bg, ink) = when (alert.workflow.phase) {
                "closed" -> if (alert.status == "done_by_gaca") SuccessBg to SuccessInk else ChipBackground to TextSecondary
                "waiting_on_you" -> SuggestBackground to HeaderAccent
                else -> ChipBackground to TextSecondary
            }
            val text = if (alert.workflow.phase == "closed") "$label · ${relativeTime(alert.statusChangedAt)}" else label
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(text, style = MaterialTheme.typography.labelMedium, color = ink)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
                Text(severityLabel(alert.severity), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(ChipBackground).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(alert.kind, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        // Which crew member posted this — it is also who the chat opens with.
        alert.crew?.let { crewName ->
            val member = crew.firstOrNull { it.name == crewName }
            val chipColor = member?.let { crewColor(it.color) } ?: TextSecondary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(member?.let { crewChipTint(it.color) } ?: ChipBackground)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(chipColor))
                Text(crewLabel(crewName, alert.crewRemoved), style = MaterialTheme.typography.labelSmall, color = chipColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LinkPill(link: AlertLink, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(14.dp))
        Text(link.label, style = MaterialTheme.typography.labelLarge, color = HeaderAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionsSection(alert: Alert, busy: Boolean, onRun: (Int) -> Unit) {
    val workflow = alert.workflow
    val (big, rows) = alert.actions.partition { it.prominent }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            workflow.actionsHeading?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            workflow.progressLabel?.let {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(SuccessBg).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = SuccessInk)
                }
            }
        }
        rows.forEach { ActionRow(it, busy, onRun) }
        big.forEach { BigActionButton(it, busy, onRun) }
    }
}

private fun iconFor(kind: String?): ImageVector = when {
    kind == null -> Icons.Filled.TaskAlt
    kind.startsWith("calendar") -> Icons.Filled.CalendarMonth
    kind.startsWith("email") -> Icons.Filled.MailOutline
    else -> Icons.Filled.TaskAlt
}

@Composable
private fun ActionRow(action: AlertAction, busy: Boolean, onRun: (Int) -> Unit) {
    when (action.state) {
        "running" -> RowCard(SuggestBackground) {
            Tile(PageBackground) { CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp) }
            Column(Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = TextPrimary)
                Text(action.note ?: "", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        "done" -> {
            val result = action.result
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowCard(SuccessBg) {
                    Tile(SuccessDot) { Icon(Icons.Filled.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(20.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(action.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = SuccessInk)
                        result?.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SuccessInk) }
                    }
                    val uriHandler = LocalUriHandler.current
                    result?.links?.firstOrNull()?.let { link ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { runCatching { uriHandler.openUri(link.url) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${link.label}", tint = SuccessInk, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                result?.draft?.let { DraftCard(it, title = action.label) }
            }
        }
        "failed" -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SeverityUrgentBg).padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(SeverityUrgentInk) { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = SeverityUrgentBg, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Couldn't: ${action.label}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = SeverityUrgentInk)
                    Text(action.result?.error ?: "", style = MaterialTheme.typography.bodyMedium, color = SeverityUrgentInk)
                }
            }
            OutlinedButton(
                onClick = { onRun(action.index) },
                enabled = action.canRun && !busy,
                modifier = Modifier.align(Alignment.End).heightIn(min = 44.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, SeverityUrgentInk),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SeverityUrgentInk),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again", style = MaterialTheme.typography.labelLarge)
            }
        }
        else -> {
            val enabled = action.canRun && !busy
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (action.canRun) 1f else 0.55f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuggestBackground)
                    .clickable(enabled = enabled) { onRun(action.index) }
                    .heightIn(min = 64.dp)
                    .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Tile(PageBackground) { Icon(iconFor(action.kind), contentDescription = null, tint = HeaderAccent, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(action.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = TextPrimary)
                    action.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
                }
                Box(Modifier.clip(RoundedCornerShape(16.dp)).border(1.5.dp, HeaderAccent, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 5.dp)) {
                    Text(action.cta, style = MaterialTheme.typography.labelLarge, color = HeaderAccent)
                }
            }
        }
    }
}

@Composable
private fun BigActionButton(action: AlertAction, busy: Boolean, onRun: (Int) -> Unit) {
    val enabled = action.canRun && !busy
    Button(
        onClick = { onRun(action.index) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HeaderAccent, contentColor = OnAccent),
    ) {
        if (action.state == "running") {
            CircularProgressIndicator(color = OnAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(action.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
        }
    }
}

@Composable
private fun RowCard(background: Color, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .heightIn(min = 64.dp)
            .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Tile(background: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(background), contentAlignment = Alignment.Center) { content() }
}

/** "Chat about this": the chat already carries the alert, so this just opens it. */
@Composable
private fun ChatEntry(alert: Alert, opening: Boolean, onClick: () -> Unit) {
    val prominent = alert.workflow.chatProminent
    val shape = RoundedCornerShape(14.dp)
    val ink = if (prominent) OnAccent else TextPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (prominent) Modifier.background(HeaderAccent) else Modifier.border(1.dp, BorderColor, shape))
            .clickable(enabled = !opening, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (opening) {
            CircularProgressIndicator(color = if (prominent) OnAccent else HeaderAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = if (prominent) OnAccent else HeaderAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Chat about this", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = ink)
            Text(alert.workflow.chatHint, style = MaterialTheme.typography.bodyMedium, color = if (prominent) SuggestBackground else TextSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = if (prominent) OnAccent else TextSecondary)
    }
}

/** The buttons the API offers for closing the alert, and its note on what closing means. */
@Composable
private fun CloseSection(alert: Alert, onClose: (String) -> Unit) {
    val workflow = alert.workflow
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
        workflow.closeHeading?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            workflow.closeOptions.forEach { option ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .then(if (option.primary) Modifier.border(1.5.dp, HeaderAccent, shape) else Modifier.background(ChipBackground))
                        .clickable { onClose(option.reason) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (option.primary) HeaderAccent else TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        workflow.closeHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
    }
}
