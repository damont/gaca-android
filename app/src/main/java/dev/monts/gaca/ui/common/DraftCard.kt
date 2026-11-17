package dev.monts.gaca.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.monts.gaca.data.repo.Draft
import dev.monts.gaca.ui.theme.BorderColor
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.OnAccent
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary

/**
 * An email an agent wrote, laid out like a piece of paper. Hermes can only read mail, so nothing here
 * sends: "Open in mail" hands the API-built mailto link to the phone's mail app (recipient, subject and
 * body filled in) and the person reviews and sends it there. [title] labels the card ("Ask for a smaller
 * increase", "New draft"); [showNotSentNote] adds the one-line reminder that it has not been sent.
 */
@Composable
fun DraftCard(draft: Draft, title: String?, modifier: Modifier = Modifier, showNotSentNote: Boolean = true) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().background(ChipBackground).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!draft.to.isNullOrBlank()) HeaderLine("To", draft.to)
                if (!draft.subject.isNullOrBlank()) HeaderLine("Subject", draft.subject, bold = true)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ChipBackground))
            SelectionContainer {
                Text(
                    draft.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Button(
            onClick = { openInMail(context, draft.mailto) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HeaderAccent, contentColor = OnAccent),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in mail to review and send", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = { copyText(context, draft.copyText) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, HeaderAccent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderAccent),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Copy text", style = MaterialTheme.typography.labelLarge)
        }
        if (showNotSentNote) {
            Text(
                "Not sent. Hermes can only read your mail, so your mail app opens with this filled in and you send it yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun HeaderLine(label: String, value: String, bold: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(60.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
        )
    }
}

/** Hands a mailto link to whatever mail app the phone has. A phone with none must not crash the app. */
private fun openInMail(context: Context, mailto: String) {
    if (mailto.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Email draft", text))
}
