package dev.monts.gaca.ui.join

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.InvitePreview
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary

/** "Add a shared page": paste an invite link, see what it would add, then Trust and add. */
@Composable
fun JoinScreen(
    viewModel: JoinViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArtifact: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(PageBackground).windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary) }
            Text("Add a shared page", style = MaterialTheme.typography.headlineSmall, color = HeaderAccent)
        }
        when (val phase = state.phase) {
            JoinPhase.Enter -> {
                Text(
                    "Someone can share a page with you by sending a link. Paste it here to see what it is before you add it.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                )
                OutlinedTextField(
                    value = state.input, onValueChange = viewModel::setInput, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste the invite link") }, minLines = 2, maxLines = 4,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Look at it", enabled = state.input.isNotBlank(), onClick = viewModel::lookUp)
                    TextButton(onClick = { clipboardText(context)?.let(viewModel::setInput) }) { Text("Paste") }
                }
            }
            JoinPhase.Looking -> Working("Looking at the link…")
            is JoinPhase.Review -> Review(phase.preview, onAdd = viewModel::trustAndAdd, onCancel = viewModel::cancelReview)
            JoinPhase.Joining -> Working("Adding it…")
            is JoinPhase.Joined -> {
                Text("Added “${phase.page.title}”", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("It's in your Artifacts, under Shared with me.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phase.page.id.isNotBlank()) Button("Open it") { onOpenArtifact(phase.page.id) }
                    TextButton(onClick = onBack) { Text("Done") }
                }
            }
            JoinPhase.NeedsLink -> {
                Text(
                    "Link your server to GACA Cloud first (in Settings), then come back and paste the link again.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                )
                Button("Open Settings", onClick = onOpenSettings)
            }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot) }
    }
}

@Composable
private fun Review(preview: InvitePreview, onAdd: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OnAccent).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(preview.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = TextMeta, modifier = Modifier.size(16.dp))
            Text("Shared by ${preview.invitedBy ?: "someone"}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Text(
            preview.roleLabel, style = MaterialTheme.typography.labelMedium, color = HeaderAccent,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(ChipBackground).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(preview.youCan, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
    if (preview.alreadyMember) {
        Text("You're already in this page. Adding it again changes nothing.", style = MaterialTheme.typography.bodySmall, color = TextMeta)
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SeverityWarnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Only add pages from people you trust", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                "Once added, it appears in your list and opens in this app like your own pages. You'll be in it as ${preview.roleLabel.lowercase()}.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(if (preview.alreadyMember) "Open it" else "Trust and add", onClick = onAdd)
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** What is on the clipboard as text, or null. Read only when the person taps Paste. */
private fun clipboardText(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

@Composable
private fun Working(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun Button(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) HeaderAccent else ChipBackground)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) OnAccent else TextMeta)
    }
}
