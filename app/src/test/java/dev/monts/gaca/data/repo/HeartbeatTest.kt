package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.HeartbeatSettingsDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HeartbeatTest {
    private val base = HeartbeatSettingsDto(
        enabled = true, pausedUntil = null, timezone = "America/Chicago", quietStartHour = 22, quietEndHour = 7,
        maxAlertsPerDay = 3, minMinutesBetweenAlerts = 60, maxRunsPerDay = 30,
    ).toDomain()

    @Test fun `the API's settings map to the app's, and a missing field takes the server's default`() {
        val s = HeartbeatSettingsDto(pausedUntil = "2026-11-02T08:00:00Z", timezone = "America/Chicago", maxAlertsPerDay = 1).toDomain()
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), s.pausedUntil)
        assertEquals(1, s.maxAlertsPerDay)
        assertEquals(22, s.quietStartHour)   // defaults, as the server's own
        assertEquals(60, s.minMinutesBetweenAlerts)
        assertTrue(s.enabled)
    }

    @Test fun `the server's reply decodes even with fields this app does not know`() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val dto = json.decodeFromString<HeartbeatSettingsDto>(
            """{"enabled":false,"paused_until":null,"timezone":null,"quiet_start_hour":21,"quiet_end_hour":6,
               "max_alerts_per_day":2,"min_minutes_between_alerts":90,"max_runs_per_day":30,"something_new":1}""",
        )
        assertFalse(dto.enabled); assertNull(dto.pausedUntil); assertEquals(21, dto.quietStartHour); assertEquals(90, dto.minMinutesBetweenAlerts)
    }

    @Test fun `a save sends only what changed, plus this phone's time zone`() {
        val body = HeartbeatChange(maxAlertsPerDay = 1).toBody("America/Chicago")
        assertEquals(setOf("timezone", "max_alerts_per_day"), body.keys)
        assertEquals("America/Chicago", body["timezone"]!!.jsonPrimitive.content)
        assertEquals(1, body["max_alerts_per_day"]!!.jsonPrimitive.int)
        assertFalse(HeartbeatChange(enabled = false).toBody("UTC")["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test fun `clearing a pause sends an explicit null, and it survives the app's own Json settings`() {
        val body = HeartbeatChange(clearPause = true).toBody("UTC")
        assertEquals(JsonNull, body["paused_until"])
        // The app's Json drops null *properties* (explicitNulls = false); a null inside a JsonObject must stay.
        val appJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val sent = appJson.encodeToString(JsonObject.serializer(), body)
        assertTrue(sent, "\"paused_until\":null" in sent)
    }

    @Test fun `setting a pause sends it as a timestamp`() {
        val body = HeartbeatChange(pausedUntil = Instant.parse("2026-11-02T08:00:00Z")).toBody("UTC")
        assertEquals("2026-11-02T08:00:00Z", body["paused_until"]!!.jsonPrimitive.content)
    }

    @Test fun `a change shows at once on the settings it will be saved to`() {
        val at = Instant.parse("2026-11-02T08:00:00Z")
        val s = base.applying(HeartbeatChange(quietStartHour = 23, maxAlertsPerDay = 5, pausedUntil = at))
        assertEquals(23, s.quietStartHour); assertEquals(5, s.maxAlertsPerDay); assertEquals(at, s.pausedUntil)
        assertEquals(7, s.quietEndHour)    // untouched
        assertNull(s.applying(HeartbeatChange(clearPause = true)).pausedUntil)
        assertFalse(base.applying(HeartbeatChange(enabled = false)).enabled)
    }

    @Test fun `paused means a pause that has not ended yet`() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        assertTrue(base.copy(pausedUntil = now.plusSeconds(60)).isPaused(now))
        assertFalse(base.copy(pausedUntil = now.minusSeconds(60)).isPaused(now))
        assertFalse(base.isPaused(now))
    }

    @Test fun `pausing for hours counts from now`() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        assertEquals(Instant.parse("2026-11-02T13:00:00Z"), pauseForHours(now, 1))
    }

    @Test fun `until morning is the next time the clock reads the quiet hours' end`() {
        val chicago = ZoneId.of("America/Chicago")
        // 9:00 PM Monday in Chicago (CST, UTC-6) -> 7:00 AM Tuesday
        assertEquals(Instant.parse("2026-11-03T13:00:00Z"), nextMorning(Instant.parse("2026-11-03T03:00:00Z"), chicago, 7))
        // 2:00 AM Monday -> 7:00 AM the same morning, not a day later
        assertEquals(Instant.parse("2026-11-02T13:00:00Z"), nextMorning(Instant.parse("2026-11-02T08:00:00Z"), chicago, 7))
        // exactly 7:00 AM -> the next one, tomorrow
        assertEquals(Instant.parse("2026-11-03T13:00:00Z"), nextMorning(Instant.parse("2026-11-02T13:00:00Z"), chicago, 7))
    }

    @Test fun `hours read as a person says them`() {
        assertEquals("12 AM", hourLabel(0)); assertEquals("7 AM", hourLabel(7)); assertEquals("12 PM", hourLabel(12))
        assertEquals("1 PM", hourLabel(13)); assertEquals("10 PM", hourLabel(22)); assertEquals("11 PM", hourLabel(23))
    }

    @Test fun `gaps and daily limits read as a person says them`() {
        assertEquals("No minimum", gapLabel(0)); assertEquals("30 min", gapLabel(30)); assertEquals("1 hour", gapLabel(60))
        assertEquals("90 min", gapLabel(90)); assertEquals("2 hours", gapLabel(120))
        assertEquals("None (urgent only)", alertsPerDayLabel(0)); assertEquals("1 per day", alertsPerDayLabel(1)); assertEquals("3 per day", alertsPerDayLabel(3))
    }

    @Test fun `a gap set somewhere else still appears among the choices, in order`() {
        assertEquals(GAP_PRESETS, gapChoices(60))
        val choices = gapChoices(45)
        assertTrue(45 in choices); assertEquals(choices.sorted(), choices)
    }
}
