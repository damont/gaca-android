package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AutomationDto
import dev.monts.gaca.data.remote.RunMarkDto
import dev.monts.gaca.util.dayAndTime
import dev.monts.gaca.util.durationLabel
import dev.monts.gaca.util.parseInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AutomationTest {
    @Test fun `the API's words map to statuses and an unknown one reads as ok`() {
        fun status(word: String) = AutomationDto(id = "a", name = "n", schedule = "s", status = word).toDomain().status
        assertEquals(AutomationStatus.FAILED, status("failed"))
        assertEquals(AutomationStatus.PAUSED, status("paused"))
        assertEquals(AutomationStatus.NEVER_RUN, status("never_run"))
        assertEquals(AutomationStatus.OK, status("something_new"))
    }

    @Test fun `a job maps with its recent runs and times`() {
        val a = AutomationDto(
            id = "j1", name = "Daily briefing", crew = "Hermes", schedule = "Every day at 7:00 AM", cronExpr = "0 7 * * *",
            status = "failed", lastStatus = "failed", lastRun = "2026-10-18T11:00:00+00:00", nextRun = "2026-10-19T11:00:00Z",
            recent = listOf(RunMarkDto("r1", "ok", "2026-10-17T11:00:00Z"), RunMarkDto("r2", "failed", null)),
        ).toDomain()
        assertTrue(a.needsAttention)
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), a.lastRun)
        assertEquals(Instant.parse("2026-10-19T11:00:00Z"), a.nextRun)
        assertEquals(listOf(RunStatus.OK, RunStatus.FAILED), a.recent.map { it.status })
        assertNull(a.recent[1].at)
    }

    @Test fun `timestamps parse with Z or an offset and tolerate junk`() {
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), parseInstant("2026-10-18T07:00:00-04:00"))
        assertNull(parseInstant(null))
        assertNull(parseInstant("not a time"))
    }

    @Test fun `durations read as seconds or minutes and seconds`() {
        assertEquals("12s", durationLabel(12.4))
        assertEquals("1m 04s", durationLabel(64.0))
        assertEquals("2m 00s", durationLabel(120.0))
    }

    @Test fun `a time is labelled by its day`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 18)
        val at = Instant.parse("2026-10-18T07:30:00Z")
        assertTrue(dayAndTime(at, today, zone).startsWith("Today"))
        assertTrue(dayAndTime(at.plusSeconds(86_400), today, zone).startsWith("Tomorrow"))
        assertTrue(dayAndTime(at.minusSeconds(86_400), today, zone).startsWith("Yesterday"))
    }
}
