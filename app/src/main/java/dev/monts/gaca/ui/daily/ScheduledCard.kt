package dev.monts.gaca.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monts.gaca.data.repo.Automation
import dev.monts.gaca.data.repo.AutomationStatus
import dev.monts.gaca.data.repo.RunStatus
import dev.monts.gaca.ui.automations.RunBadge
import dev.monts.gaca.ui.theme.ChipBackground
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.SeverityUrgentBg
import dev.monts.gaca.ui.theme.SeverityUrgentInk
import dev.monts.gaca.ui.theme.SuccessBg
import dev.monts.gaca.ui.theme.SuccessInk
import dev.monts.gaca.ui.theme.TextPrimary
import dev.monts.gaca.ui.theme.TextSecondary
import dev.monts.gaca.util.clockTime
import dev.monts.gaca.util.dayAndTime
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Scheduled today": the automations that have run so far today, failures first and highlighted, how many
 * worked, and what runs next. Tap a row for that job's history; "All automations" opens the tab.
 */
@Composable
fun ScheduledCard(
    automations: List<Automation>,
    onOpenAutomation: (crew: String, id: String) -> Unit,
    onOpenAutomations: () -> Unit,
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val ranToday = automations
        .filter { a -> a.lastRun?.atZone(zone)?.toLocalDate() == today }
        .sortedWith(compareBy({ it.lastStatus != RunStatus.FAILED }, { it.lastRun }))
    val next = automations
        .filter { it.status != AutomationStatus.PAUSED && it.status != AutomationStatus.DONE }
        .mapNotNull { a -> a.nextRun?.let { a to it } }
        .minByOrNull { it.second }
    if (ranToday.isEmpty() && next == null) return
    val failed = ranToday.count { it.lastStatus == RunStatus.FAILED }
    val worked = ranToday.count { it.lastStatus == RunStatus.OK }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ChipBackground.copy(alpha = 0.55f)).padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Scheduled today", style = MaterialTheme.typography.titleMedium, color = HeaderAccent)
            if (ranToday.isNotEmpty()) {
                val bad = failed > 0
                Text(
                    "$worked of ${ranToday.size} ran OK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (bad) SeverityUrgentInk else SuccessInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (bad) SeverityUrgentBg else SuccessBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        ranToday.forEach { a ->
            val bad = a.lastStatus == RunStatus.FAILED
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (bad) Modifier.background(SeverityUrgentBg) else Modifier)
                    .clickable { onOpenAutomation(a.crew, a.id) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RunBadge(a.lastStatus ?: RunStatus.OK)
                Text(a.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (bad) "Failed · " else "") + (a.lastRun?.let { clockTime(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad) SeverityUrgentInk else TextSecondary,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                next?.let { "Next: ${it.first.name} · ${dayAndTime(it.second)}" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "All automations ›",
                style = MaterialTheme.typography.labelLarge,
                color = HeaderAccent,
                modifier = Modifier.clickable(onClick = onOpenAutomations).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}
