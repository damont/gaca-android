package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.HeartbeatSettingsDto
import dev.monts.gaca.util.parseInstant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** How the agent's periodic check-in behaves. The user owns these; the agent only reads them. */
data class HeartbeatSettings(
    val enabled: Boolean,
    val pausedUntil: Instant?,
    val timezone: String?,
    val quietStartHour: Int,
    val quietEndHour: Int,
    val maxAlertsPerDay: Int,
    val minMinutesBetweenAlerts: Int,
    val maxRunsPerDay: Int,
) {
    fun isPaused(now: Instant): Boolean = pausedUntil != null && pausedUntil.isAfter(now)

    /** The settings as they will stand once [change] is saved: shown at once, then confirmed (or undone). */
    fun applying(change: HeartbeatChange): HeartbeatSettings = copy(
        enabled = change.enabled ?: enabled,
        pausedUntil = when {
            change.clearPause -> null
            change.pausedUntil != null -> change.pausedUntil
            else -> pausedUntil
        },
        quietStartHour = change.quietStartHour ?: quietStartHour,
        quietEndHour = change.quietEndHour ?: quietEndHour,
        maxAlertsPerDay = change.maxAlertsPerDay ?: maxAlertsPerDay,
        minMinutesBetweenAlerts = change.minMinutesBetweenAlerts ?: minMinutesBetweenAlerts,
    )
}

fun HeartbeatSettingsDto.toDomain() = HeartbeatSettings(
    enabled = enabled,
    pausedUntil = parseInstant(pausedUntil),
    timezone = timezone,
    quietStartHour = quietStartHour,
    quietEndHour = quietEndHour,
    maxAlertsPerDay = maxAlertsPerDay,
    minMinutesBetweenAlerts = minMinutesBetweenAlerts,
    maxRunsPerDay = maxRunsPerDay,
)

/** One edit, however small: only what is set is sent. */
data class HeartbeatChange(
    val enabled: Boolean? = null,
    val pausedUntil: Instant? = null,
    val clearPause: Boolean = false,
    val quietStartHour: Int? = null,
    val quietEndHour: Int? = null,
    val maxAlertsPerDay: Int? = null,
    val minMinutesBetweenAlerts: Int? = null,
) {
    /** The PUT body. The phone's zone goes with every save so quiet hours mean the phone's local
     * hours; a cleared pause is an explicit null (omitting the field would leave the pause in place). */
    fun toBody(zoneId: String): JsonObject = JsonObject(buildMap {
        put("timezone", JsonPrimitive(zoneId))
        enabled?.let { put("enabled", JsonPrimitive(it)) }
        if (clearPause) put("paused_until", JsonNull) else pausedUntil?.let { put("paused_until", JsonPrimitive(it.toString())) }
        quietStartHour?.let { put("quiet_start_hour", JsonPrimitive(it)) }
        quietEndHour?.let { put("quiet_end_hour", JsonPrimitive(it)) }
        maxAlertsPerDay?.let { put("max_alerts_per_day", JsonPrimitive(it)) }
        minMinutesBetweenAlerts?.let { put("min_minutes_between_alerts", JsonPrimitive(it)) }
    })
}

/** Never cached: each call asks gaca-api, so what shows is what the heartbeat will obey. */
class HeartbeatRepository(private val api: GacaApi) {
    suspend fun get(): HeartbeatSettings = api.getHeartbeatSettings().toDomain()

    suspend fun update(change: HeartbeatChange, zone: ZoneId = ZoneId.systemDefault()): HeartbeatSettings =
        api.updateHeartbeatSettings(change.toBody(zone.id)).toDomain()
}

// ── pausing ──────────────────────────────────────────────────────────

fun pauseForHours(now: Instant, hours: Long): Instant = now.plus(Duration.ofHours(hours))

/** The next time the clock reads [hour]:00 in [zone]: this morning if it is still earlier than that,
 * otherwise tomorrow. "Until morning" means the quiet hours' own end, so it lines up with them. */
fun nextMorning(now: Instant, zone: ZoneId, hour: Int): Instant {
    val local = now.atZone(zone)
    var at = local.toLocalDate().atTime(hour, 0).atZone(zone)
    if (!at.toInstant().isAfter(now)) at = at.plusDays(1)
    return at.toInstant()
}

// ── wording ──────────────────────────────────────────────────────────

/** 0 -> "12 AM", 7 -> "7 AM", 12 -> "12 PM", 22 -> "10 PM". */
fun hourLabel(hour: Int): String {
    val h = ((hour % 24) + 24) % 24
    val twelve = if (h % 12 == 0) 12 else h % 12
    return "$twelve ${if (h < 12) "AM" else "PM"}"
}

/** 0 -> "No minimum", 30 -> "30 min", 60 -> "1 hour", 120 -> "2 hours", 90 -> "90 min". */
fun gapLabel(minutes: Int): String = when {
    minutes <= 0 -> "No minimum"
    minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes == 60) "" else "s"}"
    else -> "$minutes min"
}

val GAP_PRESETS = listOf(0, 15, 30, 60, 90, 120, 180, 240, 480, 720)

/** The presets plus the saved value, so a value set elsewhere (another client) still shows as itself. */
fun gapChoices(current: Int): List<Int> = (GAP_PRESETS + current).distinct().sorted()

const val MAX_ALERTS_PER_DAY_LIMIT = 20

fun alertsPerDayLabel(n: Int): String = when (n) {
    0 -> "None (urgent only)"
    1 -> "1 per day"
    else -> "$n per day"
}
