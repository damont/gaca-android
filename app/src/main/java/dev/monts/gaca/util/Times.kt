package dev.monts.gaca.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads the API's ISO-8601 timestamps, with a "Z" or a "+00:00" offset; null when absent or unreadable. */
fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
}

/** "7:30 AM" in the phone's zone. */
fun clockTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    at.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

/** "Today 7:30 AM", "Tomorrow 7:30 AM", "Yesterday 7:30 AM", else "Sat 7:30 AM" / "Oct 4 7:30 AM". */
fun dayAndTime(at: Instant, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = at.atZone(zone)
    val day = local.toLocalDate()
    val label = when {
        day == today -> "Today"
        day == today.plusDays(1) -> "Tomorrow"
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) && day.isBefore(today.plusDays(7)) ->
            local.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> local.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
    return "$label ${clockTime(at, zone)}"
}

/** "12s", "1m 04s", "2m 00s". */
fun durationLabel(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return if (total < 60) "${total}s" else "${total / 60}m ${"%02d".format(total % 60)}s"
}
