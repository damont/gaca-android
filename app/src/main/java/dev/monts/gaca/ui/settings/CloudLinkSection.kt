package dev.monts.gaca.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.CloudLinkKind
import dev.monts.gaca.data.repo.CloudLinkStatus
import dev.monts.gaca.data.repo.UnlinkBlock
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextMeta
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.dayAndTime

/** "GACA CLOUD" in Settings: link this server to a cloud account (needed to share pages, publish them, or join someone else's). */
@Composable
fun CloudLinkSection(viewModel: CloudLinkViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("GACA CLOUD", style = MaterialTheme.typography.labelMedium, color = TextMeta, modifier = Modifier.weight(1f))
        if (state.loading || state.starting) {
            CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Link your server to a GACA Cloud account to share pages with other people or publish them. " +
            "Your phone never talks to the cloud itself; your own server does, on your behalf.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMeta,
    )
    Spacer(Modifier.height(10.dp))

    val attempt = state.attempt
    val status = state.status
    when {
        attempt != null -> Card {
            Text("Approve this code in your browser", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                attempt.userCode,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = HeaderAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Open the page below, sign in, and check that it shows this code. This screen updates by itself when you are done.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Open the page") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attempt.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                Pill("Cancel", subtle = true, onClick = viewModel::cancelLinking)
                if (state.error != null) Pill("Check again", onClick = viewModel::checkAgain)
            }
            if (state.error == null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = HeaderAccent, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text("Waiting for you to approve it…", style = MaterialTheme.typography.labelSmall, color = TextMeta)
                }
            }
        }
        status == null -> Unit // not loaded yet; the error (if any) shows below
        status.isLinked -> Card {
            Text("Linked", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(linkedLine(status), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (status.scope == "read") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "This link is read-only: it can list and read, but cannot move, share or publish. Unlink and link again to change that.",
                    style = MaterialTheme.typography.bodySmall, color = TextMeta,
                )
            }
            Spacer(Modifier.height(10.dp))
            Pill("Unlink", subtle = true, onClick = viewModel::askUnlink)
        }
        else -> Card {
            val line = when (status.kind) {
                CloudLinkKind.Invalid -> "The cloud no longer accepts this server's link. Link again to keep sharing."
                CloudLinkKind.Pending -> "A link was started but not finished. Start again to get a fresh code."
                else -> "Not linked."
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (state.ended != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.ended == LinkEnd.Denied) "The code was not approved." else "That code ran out of time.",
                    style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot,
                )
            }
            Spacer(Modifier.height(10.dp))
            Pill(if (status.kind == CloudLinkKind.Invalid) "Link again" else "Link GACA Cloud", onClick = viewModel::startLinking)
        }
    }

    state.notice?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.clickable(onClick = viewModel::dismissNotice))
    }
    state.error?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = SeverityUrgentDot)
        if (status == null && attempt == null) {
            Spacer(Modifier.height(4.dp))
            Pill("Try again", subtle = true, onClick = viewModel::load)
        }
    }

    if (state.confirmingUnlink) {
        val block = state.unlinkBlock
        AlertDialog(
            onDismissRequest = viewModel::dismissUnlink,
            title = { Text(if (block == null) "Unlink GACA Cloud?" else "Some pages live in the cloud") },
            text = { Text(if (block == null) UNLINK_TEXT else blockedText(block)) },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { viewModel.unlink(force = block != null) }) {
                    Text(if (block == null) "Unlink" else "Unlink anyway")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissUnlink) { Text("Cancel") } },
        )
    }
}

private const val UNLINK_TEXT =
    "This server will forget the link and the cloud will stop accepting it. Pages that only live on this server are not affected."

/** Plain-words version of what the server told us it would cut off. */
internal fun blockedText(block: UnlinkBlock): String {
    val parts = buildList {
        if (block.owned > 0) add("${block.owned} of your own")
        if (block.shared > 0) add("${block.shared} shared with you")
        if (block.inProgress > 0) add("${block.inProgress} move${if (block.inProgress == 1) "" else "s"} still running")
    }
    val what = if (parts.isEmpty()) "Some pages live in the cloud or a move is not finished." else "In the cloud: ${parts.joinToString(", ")}."
    return "$what If you unlink, this app can no longer open them until you link again (they stay safe in the cloud). " +
        "To keep access, cancel and bring your own pages home first."
}

internal fun linkedStatusLine(status: CloudLinkStatus): String =
    listOfNotNull(
        status.handle?.let { "Account $it" } ?: "Account linked",
        status.linkedAt?.let { "since ${dayAndTime(it)}" },
    ).joinToString(" · ")

private fun linkedLine(status: CloudLinkStatus) = linkedStatusLine(status)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OnAccent).border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(14.dp),
    ) { content() }
}

@Composable
private fun Pill(text: String, subtle: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (subtle) ChipBackground else HeaderAccent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (subtle) HeaderAccent else OnAccent)
    }
}
