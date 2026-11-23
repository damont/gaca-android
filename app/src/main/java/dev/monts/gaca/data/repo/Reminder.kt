package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.ReminderDto
import dev.monts.gaca.data.remote.ReminderUpdateRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A reminder as the app shows it: only what the little card in chat (and the Undo on a suggested-reminder
 * action) needs. There is no list of reminders in the app; the API keeps and expires them.
 */
data class Reminder(
    val id: String,
    val text: String,
    /** Null for a "later" note: it never pings. */
    val dueAt: Instant?,
    /** pending, fired, done, cancelled or expired. */
    val state: String,
    val sessionId: String?,
    /** The alert this reminder was suggested from, when it was. */
    val sourceAlertId: String?,
    val sourceKind: String,
    val createdAt: Instant?,
) {
    val isNote: Boolean get() = dueAt == null
}

fun ReminderDto.toDomain() = Reminder(
    id = id,
    text = text,
    dueAt = dueAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    state = state,
    sessionId = source.sessionId,
    sourceAlertId = source.alertId,
    sourceKind = source.kind,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
)

/** Plain calls, no cache: the card asks once after a reply and again when Undo is tapped. */
class RemindersRepository(private val api: GacaApi) {
    suspend fun listOpen(): List<Reminder> = api.listReminders("open").reminders.map { it.toDomain() }

    suspend fun cancel(id: String): Reminder = api.updateReminder(id, ReminderUpdateRequest("cancelled")).toDomain()
}

/** Slack for the clock difference between the reminder being written and the reply being stored. */
private val SLACK: Duration = Duration.ofSeconds(3)

/**
 * Which reminders each assistant reply made. The agent does not know its chat's id, so a reminder
 * belongs to the reply whose turn it was created in: after your message, up to the reply. One that
 * names a different chat is never matched, and one suggested from an alert is not a chat's.
 */
fun remindersByReply(
    messages: List<ConversationMessage>,
    reminders: List<Reminder>,
    sessionId: String,
): Map<String, List<Reminder>> {
    if (reminders.isEmpty()) return emptyMap()
    val eligible = reminders.filter { it.sourceKind != "alert" && (it.sessionId == null || it.sessionId == sessionId) && it.createdAt != null }
    if (eligible.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, List<Reminder>>()
    var promptAt: Instant? = null
    for (message in messages) {
        val at = runCatching { Instant.parse(message.createdAt) }.getOrNull() ?: continue
        if (message.role == "user") {
            promptAt = at
        } else if (message.role == "assistant" && promptAt != null) {
            val from = promptAt.minus(SLACK)
            val to = at.plus(SLACK)
            val made = eligible.filter { it.createdAt!! in from..to }
            if (made.isNotEmpty()) result[message.id] = made
        }
    }
    return result
}

/** "Tomorrow 10:00 AM": the day in plain words when it is close, then the time. */
fun Reminder.whenLabel(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val due = dueAt?.atZone(zone) ?: return null
    val today: LocalDate = now.atZone(zone).toLocalDate()
    val time = due.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    val day = when (due.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> due.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }
    return "$day $time"
}
