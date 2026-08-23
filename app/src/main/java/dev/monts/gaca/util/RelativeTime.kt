package dev.monts.gaca.util

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** Formats an ISO-8601 UTC timestamp (`created_at`) as "2m" / "3h" / "5d", matching the design's inbox rows. */
fun relativeTime(isoTimestamp: String, now: Instant = Instant.now()): String {
    val then = try {
        Instant.parse(isoTimestamp)
    } catch (e: DateTimeParseException) {
        return ""
    }
    val minutes = Duration.between(then, now).toMinutes()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}
