package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertDto
import dev.monts.gaca.data.remote.SessionDetailDto
import dev.monts.gaca.data.remote.SessionDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewRemovedTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun session(extra: String = "") =
        """{"id":"s1","created_at":"2026-11-01T10:00:00Z","status":"idle","crew":"david"$extra}"""

    private fun alert(extra: String = "") =
        """{"id":"a1","user_id":"u","created_at":"2026-11-01T10:00:00Z","source":"hermes","crew":"david","kind":"k","severity":"info",
            "title":"t","status":"new","effective_status":"new","status_changed_at":"2026-11-01T10:00:00Z"$extra}"""

    @Test fun `an older server that never sends crew_removed means the crew member is still there`() {
        assertFalse(json.decodeFromString<SessionDto>(session()).toDomain().crewRemoved)
        assertFalse(json.decodeFromString<SessionDetailDto>(session()).toDomain().session.crewRemoved)
        assertFalse(json.decodeFromString<AlertDto>(alert()).toDomain().crewRemoved)
    }

    @Test fun `crew_removed true reaches the session list, the session detail and the alert`() {
        assertTrue(json.decodeFromString<SessionDto>(session(""","crew_removed":true""")).toDomain().crewRemoved)
        assertTrue(json.decodeFromString<SessionDetailDto>(session(""","crew_removed":true""")).toDomain().session.crewRemoved)
        assertTrue(json.decodeFromString<AlertDto>(alert(""","crew_removed":true""")).toDomain().crewRemoved)
    }

    @Test fun `crew_removed false stays false`() {
        assertFalse(json.decodeFromString<SessionDto>(session(""","crew_removed":false""")).toDomain().crewRemoved)
    }

    @Test fun `a removed crew member is marked wherever it is named, a current one is left alone`() {
        assertEquals("david (removed)", crewLabel("david", true))
        assertEquals("david", crewLabel("david", false))
    }

    @Test fun `when gaca-api can reach the box, the prompt says the profile will be deleted for good`() {
        val prompt = removeCrewPrompt("david", deletesProfile = true)
        assertEquals("Delete david?", prompt.title)
        assertEquals("Delete", prompt.confirm)
        assertTrue("permanently" in prompt.body && "memory" in prompt.body && "scheduled jobs" in prompt.body)
        assertTrue("read-only history" in prompt.body)
    }

    @Test fun `when it cannot, the prompt says nothing on the box is touched`() {
        val prompt = removeCrewPrompt("david", deletesProfile = false)
        assertEquals("Remove david from the app?", prompt.title)
        assertEquals("Remove", prompt.confirm)
        assertEquals("This does not delete anything on your Hermes box.", prompt.body)
    }
}
