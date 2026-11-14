package dev.monts.gaca.ui.artifact

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.EXPIRY_CHOICES_DAYS
import dev.monts.gaca.data.repo.Finding
import dev.monts.gaca.data.repo.FindingSeverity
import dev.monts.gaca.data.repo.ShareOptions
import dev.monts.gaca.data.repo.ShareRole
import dev.monts.gaca.data.repo.USE_CHOICES
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary

/** Share a page by link: pick what the person can do, confirm, check, create, copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(viewModel: ShareViewModel, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phase = state.phase
    // While something is being changed on the server, a swipe must not hide the sheet halfway through.
    val busy = phase == SharePhase.Moving || phase == SharePhase.Creating
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share “${state.title}”", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            when (phase) {
                SharePhase.Loading -> Working("Getting ready…")
                is SharePhase.NeedsLink -> {
                    Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    if (phase.canOpenSettings) Action("Open Settings") { onDismiss(); onOpenSettings() }
                    else Action("Try again", onClick = viewModel::load)
                }
                is SharePhase.CannotMove -> Text(phase.reason, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                SharePhase.Setup -> Setup(state, viewModel)
                SharePhase.Moving -> Working("Moving the page to the cloud and checking the copy. This can take a little while; keep the app open.")
                SharePhase.Checking -> Working("Checking the page…")
                is SharePhase.Review -> Review(state, phase, viewModel)
                SharePhase.Creating -> Working("Creating the link…")
                is SharePhase.Done -> Done(phase.invite.url, state.options, onDismiss)
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot) }
        }
    }
}

@Composable
private fun Setup(state: ShareUiState, vm: ShareViewModel) {
    Text("What can they do?", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShareRole.entries.forEach { role ->
            FilterChip(selected = state.options.role == role, onClick = { vm.setOptions(state.options.copy(role = role)) }, label = { Text(role.label) })
        }
    }
    Text(state.options.role.canDo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Link works for", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Picker("${state.options.expiryDays} ${if (state.options.expiryDays == 1) "day" else "days"}", EXPIRY_CHOICES_DAYS.map { it to "$it ${if (it == 1) "day" else "days"}" }) {
            vm.setOptions(state.options.copy(expiryDays = it))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Up to", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Picker("${state.options.maxUses} ${if (state.options.maxUses == 1) "person" else "people"}", USE_CHOICES.map { it to "$it ${if (it == 1) "person" else "people"}" }) {
            vm.setOptions(state.options.copy(maxUses = it))
        }
    }

    val preview = state.preview
    if (!state.inCloud && preview != null) {
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SeverityWarnBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("First, this page moves to the cloud", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                "Sharing needs the page and its data to live in your GACA Cloud account. It keeps its id and shows up the same here. " +
                    "A sealed backup of the copy on your server is kept for ${preview.backupDays} days.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            )
            if (preview.notCarried.isNotEmpty()) {
                Text("The cloud does not keep:", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                preview.notCarried.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
            if (preview.requiresAcknowledgement) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.acknowledged, onCheckedChange = vm::setAcknowledged)
                    Text("I've seen what is left behind", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }
    }
    Action(
        if (state.inCloud) "Check the page" else "Move to the cloud and check",
        enabled = state.canContinue, onClick = vm::continueToChecks,
    )
}

@Composable
private fun Review(state: ShareUiState, phase: SharePhase.Review, vm: ShareViewModel) {
    val report = phase.report
    Text(
        when {
            report.blocked -> "The check found problems that stop this page being shared."
            report.warnings > 0 -> "The check passed with ${report.warnings} warning${if (report.warnings == 1) "" else "s"}."
            else -> "The check found no problems."
        },
        style = MaterialTheme.typography.bodyMedium, color = if (report.blocked) SeverityUrgentDot else TextPrimary,
    )
    report.findings.forEach { FindingRow(it) }
    if (!report.blocked) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChipBackground).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("You are about to create a link", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(state.options.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("Treat it like a password. You can't see it again after this.", style = MaterialTheme.typography.bodySmall, color = TextMeta)
        }
        Action("Create link", onClick = vm::createLink)
    }
    TextButton(onClick = vm::backToSetup) { Text(if (report.blocked) "Back" else "Change options") }
}

@Composable
private fun FindingRow(finding: Finding) {
    val tag = when (finding.severity) { FindingSeverity.Block -> "Problem"; FindingSeverity.Warn -> "Warning"; FindingSeverity.Info -> "Note" }
    Column {
        Text(
            tag, style = MaterialTheme.typography.labelSmall,
            color = if (finding.severity == FindingSeverity.Block) SeverityUrgentDot else TextMeta,
        )
        Text(finding.message + (finding.location?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun Done(url: String, options: ShareOptions, onDone: () -> Unit) {
    val context = LocalContext.current
    Text("Your link is ready", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    Text(
        url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = HeaderAccent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ChipBackground).padding(12.dp),
    )
    Text(options.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Text("This is the only time it is shown. Copy it now.", style = MaterialTheme.typography.bodySmall, color = TextMeta)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("Copy link") {
            copySecret(context, url)
            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
        }
        TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            runCatching { context.startActivity(Intent.createChooser(send, "Send link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Send…") }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

/** Puts the link on the clipboard, marked sensitive so Android doesn't show it in the paste preview. */
private fun copySecret(context: Context, text: String) {
    val clip = ClipData.newPlainText("GACA link", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

@Composable
private fun Working(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) HeaderAccent else ChipBackground)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) OnAccent else TextMeta)
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "$label ▾", style = MaterialTheme.typography.labelMedium, color = HeaderAccent,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ChipBackground).clickable { open = true }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) }) }
        }
    }
}
