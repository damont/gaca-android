package dev.monts.gaca.ui.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.PublishVisibility
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary

/** Publish a page to the gallery: write the listing, see exactly what goes out, confirm, publish. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishSheet(viewModel: PublishViewModel, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phase = state.phase
    // A swipe must not hide the sheet while the cloud is being told to publish.
    ModalBottomSheet(onDismissRequest = { if (phase != PublishPhase.Publishing) onDismiss() }, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (state.alreadyPublished) "Publish a new version" else "Publish to the gallery",
                style = MaterialTheme.typography.titleMedium, color = TextPrimary,
            )
            when (phase) {
                PublishPhase.Loading -> Working("Getting ready…")
                is PublishPhase.NeedsLink -> {
                    Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    if (phase.canOpenSettings) Action("Open Settings") { onDismiss(); onOpenSettings() }
                    else Action("Try again", onClick = viewModel::load)
                }
                PublishPhase.Form -> Form(state, viewModel)
                PublishPhase.Checking -> Working("Checking what would be published…")
                is PublishPhase.Review -> Review(phase, viewModel)
                PublishPhase.Publishing -> Working("Publishing…")
                is PublishPhase.Done -> {
                    Text(phase.result.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Action("Done", onClick = onDismiss)
                }
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot) }
        }
    }
}

@Composable
private fun Form(state: PublishUiState, vm: PublishViewModel) {
    val listing = state.listing
    Text(
        "Publishing makes a public copy of the page and its shapes for others to download. Your data is never published, and the page stays where it is and stays yours.",
        style = MaterialTheme.typography.bodySmall, color = TextSecondary,
    )
    OutlinedTextField(
        value = listing.title, onValueChange = { vm.setListing(listing.copy(title = it)) },
        label = { Text("Title") }, singleLine = true, isError = listing.title.isNotBlank() && !listing.isValid,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = listing.description, onValueChange = { vm.setListing(listing.copy(description = it)) },
        label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
        supportingText = { Text("Written fresh for the public. Your page's own description is not used.") },
    )
    Text("Where does it show up?", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PublishVisibility.entries.forEach { v ->
            FilterChip(selected = listing.visibility == v, onClick = { vm.setListing(listing.copy(visibility = v)) }, label = { Text(v.label) })
        }
    }
    Text(listing.visibility.explain, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Action("Preview what goes out", enabled = listing.isValid, onClick = vm::preview)
}

@Composable
private fun Review(phase: PublishPhase.Review, vm: PublishViewModel) {
    val review = phase.review
    val report = review.report
    Text(
        when {
            report.blocked -> "The check found problems that stop this page being published."
            report.warnings > 0 -> "The check passed with ${report.warnings} warning${if (report.warnings == 1) "" else "s"}."
            review.hash == null -> "The cloud didn't give a preview to confirm, so nothing can be published."
            else -> "The check found no problems."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (report.blocked || review.hash == null) SeverityUrgentDot else TextPrimary,
    )
    report.findings.forEach { FindingRow(it) }

    val p = review.preview
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChipBackground).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("What the public will see", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(p.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(p.description ?: "No description", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            listOfNotNull(
                "page ${p.pageSizeText}", "${p.shapes} shape${if (p.shapes == 1) "" else "s"}",
                p.license?.let { "license $it" }, p.tags.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = TextMeta,
        )
        Text("It is shown with no data in it.", style = MaterialTheme.typography.bodySmall, color = TextMeta)
    }

    if (review.canPublish) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChipBackground).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("You are about to publish", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(phase.listing.summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Action("Publish", onClick = vm::publish)
    }
    TextButton(onClick = vm::backToForm) { Text(if (review.canPublish) "Change the listing" else "Back") }
}
