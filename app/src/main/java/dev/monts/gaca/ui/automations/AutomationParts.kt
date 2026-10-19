package dev.monts.gaca.ui.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.data.repo.AutomationStatus
import dev.monts.gaca.data.repo.RunMark
import dev.monts.gaca.data.repo.RunStatus
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.SeverityInfoBg
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.SeverityUrgentInk
import dev.monts.gaca.ui.theme.SeverityWarnBg
import dev.monts.gaca.ui.theme.SeverityWarnDot
import dev.monts.gaca.ui.theme.SuccessBg
import dev.monts.gaca.ui.theme.SuccessDot
import dev.monts.gaca.ui.theme.SuccessInk
import dev.monts.gaca.ui.theme.TextSecondary

/** Colour and word for a job's overall state: the list's status pill. */
internal data class StatusLook(val label: String, val bg: Color, val ink: Color, val failed: Boolean = false)

internal fun statusLook(status: AutomationStatus): StatusLook = when (status) {
    AutomationStatus.OK -> StatusLook("OK", SuccessBg, SuccessInk)
    AutomationStatus.FAILED -> StatusLook("Failed", SeverityUrgentBg, SeverityUrgentInk, failed = true)
    AutomationStatus.RUNNING -> StatusLook("Running", SeverityWarnBg, TextSecondary)
    AutomationStatus.PAUSED -> StatusLook("Paused", ChipBackground, TextSecondary)
    AutomationStatus.DONE -> StatusLook("Done", ChipBackground, TextSecondary)
    AutomationStatus.NEVER_RUN -> StatusLook("Not run yet", SeverityInfoBg, TextSecondary)
}

@Composable
internal fun StatusPill(status: AutomationStatus) {
    val look = statusLook(status)
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(look.bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (status) {
            AutomationStatus.OK -> Icon(Icons.Filled.Check, contentDescription = null, tint = look.ink, modifier = Modifier.size(12.dp))
            AutomationStatus.FAILED -> Icon(Icons.Filled.Close, contentDescription = null, tint = look.ink, modifier = Modifier.size(12.dp))
            else -> Unit
        }
        Text(look.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = look.ink)
    }
}

/** The row of coloured squares, oldest left, newest right. */
@Composable
internal fun RunDots(recent: List<RunMark>, modifier: Modifier = Modifier) {
    if (recent.isEmpty()) return
    val failed = recent.count { it.status == RunStatus.FAILED }
    Row(
        modifier.semantics { contentDescription = "Last ${recent.size} runs, $failed failed, newest last" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recent.forEach { run ->
            val color = when (run.status) {
                RunStatus.OK -> SuccessDot
                RunStatus.FAILED -> SeverityUrgentDot
                RunStatus.RUNNING -> SeverityWarnDot
            }
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

/** The round tick / cross / dot at the start of a run's row. */
@Composable
internal fun RunBadge(status: RunStatus, size: Int = 24) {
    val (bg, ink) = when (status) {
        RunStatus.OK -> SuccessBg to SuccessInk
        RunStatus.FAILED -> SeverityUrgentBg to SeverityUrgentInk
        RunStatus.RUNNING -> SeverityWarnBg to TextSecondary
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        when (status) {
            RunStatus.OK -> Icon(Icons.Filled.Check, contentDescription = "Worked", tint = ink, modifier = Modifier.size((size / 2 + 2).dp))
            RunStatus.FAILED -> Icon(Icons.Filled.Close, contentDescription = "Failed", tint = ink, modifier = Modifier.size((size / 2 + 2).dp))
            RunStatus.RUNNING -> Box(Modifier.size(8.dp).clip(CircleShape).background(SeverityWarnDot))
        }
    }
}

/** "Last run today 7:30 · failed" / "Never run" for a list card. */
internal fun lastRunLine(a: Automation, whenText: (java.time.Instant) -> String): String {
    val at = a.lastRun ?: return "Not run yet"
    val how = when (a.lastStatus) {
        RunStatus.FAILED -> " · failed"
        RunStatus.RUNNING -> " · running"
        else -> ""
    }
    return "Last run ${whenText(at)}$how"
}
