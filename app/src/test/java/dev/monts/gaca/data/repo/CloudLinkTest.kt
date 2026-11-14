package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.CloudLinkPollDto
import dev.monts.gaca.data.remote.CloudLinkStartedDto
import dev.monts.gaca.data.remote.CloudLinkStatusDto
import dev.monts.gaca.util.infoInt
import dev.monts.gaca.util.parseApiError
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudLinkTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `server link status maps to the four states, and an unknown state reads as not linked`() {
        fun kind(state: String) = CloudLinkStatusDto(state = state).toDomain().kind
        assertEquals(CloudLinkKind.NotLinked, kind("none"))
        assertEquals(CloudLinkKind.Pending, kind("pending"))
        assertEquals(CloudLinkKind.Linked, kind("linked"))
        assertEquals(CloudLinkKind.Invalid, kind("invalid"))
        assertEquals(CloudLinkKind.NotLinked, kind("something-new"))
    }

    @Test fun `a linked status carries the account name, scope and time`() {
        val dto = json.decodeFromString<CloudLinkStatusDto>(
            """{"linked":true,"state":"linked","handle":"ada","account_id":"a1","linked_at":"2026-11-10T12:00:00Z","scope":"manage","extra":1}""",
        )
        val status = dto.toDomain()
        assertTrue(status.isLinked)
        assertEquals("ada", status.handle)
        assertEquals("manage", status.scope)
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), status.linkedAt)
    }

    @Test fun `start answer becomes an attempt that expires expires_in seconds from now`() {
        val now = Instant.parse("2026-11-10T12:00:00Z")
        val attempt = CloudLinkStartedDto("ABCD-1234", "https://cloud.example/link", 600, 5).toAttempt(now)
        assertEquals("ABCD-1234", attempt.userCode)
        assertEquals(Instant.parse("2026-11-10T12:10:00Z"), attempt.expiresAt)
        assertEquals(5, attempt.intervalSeconds)
    }

    @Test fun `pending poll waits the interval, or the server's retry_in when it was asked too soon`() {
        assertEquals(PollStep.Wait(7000), CloudLinkPollDto("pending", interval = 7).toStep(5))
        assertEquals(PollStep.Wait(3000), CloudLinkPollDto("pending", interval = 5, retryIn = 3).toStep(5))
        assertEquals(PollStep.Wait(5000), CloudLinkPollDto("pending").toStep(5))   // falls back to the attempt's interval
        assertEquals(PollStep.Wait(1000), CloudLinkPollDto("pending", interval = 0).toStep(5)) // never a busy loop
    }

    @Test fun `poll outcomes end the attempt`() {
        assertEquals(PollStep.Expired, CloudLinkPollDto("expired").toStep(5))
        assertEquals(PollStep.Denied, CloudLinkPollDto("denied").toStep(5))
        val done = CloudLinkPollDto("linked", handle = "ada", scope = "manage", linkedAt = "2026-11-10T12:00:00Z").toStep(5)
        assertTrue(done is PollStep.Done)
        assertEquals("ada", (done as PollStep.Done).status.handle)
        assertTrue(done.status.isLinked)
    }

    @Test fun `an error body gives its stable code, message and info numbers`() {
        val err = parseApiError(
            409,
            """{"code":"cloud_artifacts_present","message":"Some pages live in the cloud.","detail":"Some pages live in the cloud.",
               "info":{"owned":2,"shared":1,"total":3,"in_progress":0,"hint":"x"}}""",
        )
        assertEquals(409, err.status)
        assertEquals("cloud_artifacts_present", err.code)
        assertEquals("Some pages live in the cloud.", err.message)
        assertEquals(2, err.infoInt("owned"))
        assertEquals(0, err.infoInt("in_progress"))
        assertNull(err.infoInt("missing"))
    }

    @Test fun `an older plain detail error still parses, and garbage does not throw`() {
        val plain = parseApiError(404, """{"detail":"Not found"}""")
        assertNull(plain.code)
        assertEquals("Not found", plain.message)
        val list = parseApiError(422, """{"detail":[{"loc":["body"],"msg":"bad","type":"x"}]}""")
        assertNull(list.message)  // a list detail is not a message; friendlyMessage() handles that shape
        val junk = parseApiError(502, "<html>bad gateway</html>")
        assertNull(junk.code); assertNull(junk.message)
        assertFalse(parseApiError(500, null).info != null)
    }

    @Test fun `older servers that omit the new fields read as configured, agent access off, nothing pending`() {
        val status = json.decodeFromString<CloudLinkStatusDto>("""{"linked":false,"state":"none"}""").toDomain()
        assertTrue(status.configured)
        assertFalse(status.agentAccess)
        assertNull(status.pending)
    }

    @Test fun `a server with no cloud address says so, and agent access comes through`() {
        assertFalse(CloudLinkStatusDto(configured = false).toDomain().configured)
        assertTrue(CloudLinkStatusDto(linked = true, state = "linked", agentAccess = true).toDomain().agentAccess)
    }

    @Test fun `a link in progress gives back its code to show again, an expired one gives nothing`() {
        val live = json.decodeFromString<CloudLinkStatusDto>(
            """{"linked":false,"state":"pending","user_code":"ABCD-1234","verification_uri":"https://cloud.example/link",
               "expires_at":"2026-11-10T12:10:00Z","expires_in":300,"interval":5}""",
        ).toDomain()
        assertEquals(CloudLinkKind.Pending, live.kind)
        assertEquals("ABCD-1234", live.pending?.userCode)
        assertEquals(Instant.parse("2026-11-10T12:10:00Z"), live.pending?.expiresAt)
        assertEquals(5, live.pending?.intervalSeconds)
        val expired = json.decodeFromString<CloudLinkStatusDto>(
            """{"linked":false,"state":"pending","user_code":null,"verification_uri":null,"expires_at":null}""",
        ).toDomain()
        assertNull(expired.pending)
    }
}
