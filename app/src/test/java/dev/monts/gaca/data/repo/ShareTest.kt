package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.InviteCreateRequestDto
import dev.monts.gaca.data.remote.InviteLinkDto
import dev.monts.gaca.data.remote.MovePreviewDto
import dev.monts.gaca.data.remote.ValidationReportDto
import dev.monts.gaca.data.remote.ValidationRunRequestDto
import dev.monts.gaca.ui.artifact.ShareUiState
import dev.monts.gaca.ui.artifact.SharePhase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ShareTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun page(home: ArtifactHome = ArtifactHome.Local, moving: Boolean = false, status: String? = null, archived: Boolean = false, role: ArtifactRole = ArtifactRole.Owner) =
        Artifact("a", "T", null, null, null, null, archived, 1, "2026-11-10T12:00:00Z", null, home, role, null, 1, moving, status, null)

    @Test fun `the safest link is the default and its summary says exactly what will happen`() {
        val o = ShareOptions()
        assertEquals(ShareRole.Viewer, o.role)
        assertEquals("Anyone who has the link can join as viewer. It works for 7 days and for up to 1 person.", o.summary)
        assertEquals(
            "Anyone who has the link can join as maintainer. It works for 1 day and for up to 25 people.",
            ShareOptions(ShareRole.Maintainer, 1, 25).summary,
        )
    }

    @Test fun `the choices stay inside what the cloud accepts`() {
        assertTrue(EXPIRY_CHOICES_DAYS.all { it in 1..30 })
        assertTrue(USE_CHOICES.all { it in 1..25 })
        assertEquals(listOf("viewer", "editor", "maintainer"), ShareRole.entries.map { it.wire })
    }

    @Test fun `the invite request carries role, expiry and uses, and the check request always names its subject`() {
        assertEquals(
            """{"role":"editor","expires_in_days":7,"max_uses":5}""",
            json.encodeToString(InviteCreateRequestDto("editor", 7, 5)),
        )
        assertEquals("""{"subject":"share"}""", json.encodeToString(ValidationRunRequestDto("share")))
    }

    @Test fun `a move preview becomes plain-words lines about what is left behind`() {
        val dto = json.decodeFromString<MovePreviewDto>(
            """{"artifact_id":"a","can_move":true,"blocked_because":null,"problems":[],"size":{"bytes":10,"limit_bytes":99},
               "not_carried":{"versions_dropped":3,"handled_events_dropped":1,"crew":"scout","chat_alert_links":{"session_id":"s"},
               "doc_versions_reset":2,"last_opened":true,"anything":true},"requires_acknowledgement":true,"member_impact":"none","backup_days":30}""",
        )
        val p = dto.toDomain()
        assertTrue(p.canMove); assertTrue(p.requiresAcknowledgement); assertEquals(30, p.backupDays)
        assertEquals(6, p.notCarried.size)
        assertTrue(p.notCarried[0].startsWith("3 older versions of the page"))
        assertTrue(p.notCarried[1].startsWith("1 event already handled"))
        assertTrue(p.notCarried.any { it.contains("2 documents") })
    }

    @Test fun `nothing left behind means nothing to tick`() {
        val p = json.decodeFromString<MovePreviewDto>("""{"can_move":true,"not_carried":{"anything":false}}""").toDomain()
        assertTrue(p.notCarried.isEmpty()); assertFalse(p.requiresAcknowledgement)
    }

    @Test fun `a page that cannot move says why in plain words`() {
        val archived = json.decodeFromString<MovePreviewDto>("""{"can_move":false,"blocked_because":"artifact_archived"}""").toDomain()
        assertFalse(archived.canMove)
        assertEquals("Archived pages can't be moved. Unarchive it first.", archived.blockedText)
        val big = json.decodeFromString<MovePreviewDto>(
            """{"can_move":false,"blocked_because":"gacabit_size_exceeded","problems":[{"item":"too big","paths":[]},"x"]}""",
        ).toDomain()
        assertEquals("This page can't be moved to the cloud yet. too big; x.", big.blockedText)
    }

    @Test fun `settling after a dropped connection reads the page the way the list does`() {
        assertEquals(MoveSettled.StillMoving, settle(page(moving = true)))
        assertEquals(MoveSettled.StillMoving, settle(page(status = "moving")))
        assertEquals(MoveSettled.Done, settle(page(home = ArtifactHome.Cloud)))
        assertEquals(MoveSettled.Failed, settle(page(status = "move_failed")))
        assertEquals(MoveSettled.Failed, settle(page())) // never started: still local
    }

    @Test fun `waiting out a dropped move keeps asking through offline lookups until the page is in the cloud`() = runBlocking {
        var calls = 0
        val result = awaitMove(0, 10) {
            calls++
            when (calls) {
                1 -> throw IOException("offline")
                2 -> page(moving = true)
                else -> page(home = ArtifactHome.Cloud)
            }
        }
        assertEquals(ArtifactHome.Cloud, result.home)
        assertEquals(3, calls)
    }

    @Test fun `a failed move is reported and so is giving up, both telling the person it is safe to retry`() = runBlocking {
        try { awaitMove(0, 5) { page(status = "move_failed") }; fail() } catch (e: MoveNotFinished) {
            assertTrue(e.message!!.contains("unchanged"))
        }
        try { awaitMove(0, 3) { page(moving = true) }; fail() } catch (e: MoveNotFinished) {
            assertTrue(e.message!!.contains("safe to retry"))
        }
    }

    @Test fun `a check report orders problems first and a block stops sharing`() {
        val report = json.decodeFromString<ValidationReportDto>(
            """{"status":"warned","findings":[{"rule":"pii.email","severity":"warn","message":"An email address","location":"description"},
               {"rule":"html.external_script","severity":"block","message":"Loads a script from elsewhere"},{"rule":"x","severity":"info","message":"fyi"}]}""",
        ).toDomain()
        assertEquals(FindingSeverity.Block, report.findings.first().severity)
        assertTrue(report.blocked)
        assertEquals(1, report.warnings)
        val ok = json.decodeFromString<ValidationReportDto>("""{"status":"passed","findings":[]}""").toDomain()
        assertFalse(ok.blocked)
        assertTrue(json.decodeFromString<ValidationReportDto>("""{"status":"blocked","findings":[]}""").toDomain().blocked)
    }

    @Test fun `the invite link comes only from the url the cloud returned once`() {
        val made = InviteLinkDto(id = "i", role = "editor", url = "https://cloud.example/join/abc", expiresAt = "2026-11-17T00:00:00Z", maxUses = 5).toCreated()
        assertEquals("https://cloud.example/join/abc", made?.url)
        assertEquals(5, made?.maxUses)
        assertNull(InviteLinkDto(id = "i").toCreated())
        assertNull(InviteLinkDto(id = "i", url = " ").toCreated())
    }

    @Test fun `only an owner's idle page can be shared`() {
        assertTrue(page().canShare)
        assertTrue(page(home = ArtifactHome.Cloud).canShare)
        assertFalse(page(archived = true).canShare)
        assertFalse(page(moving = true).canShare)
        assertFalse(page(home = ArtifactHome.Cloud, status = "brought_home").canShare)
        assertFalse(page(home = ArtifactHome.Cloud, role = ArtifactRole.Maintainer).canShare)
    }

    @Test fun `continuing needs the tick only when something is left behind, and a blocked page never continues`() {
        val ok = MovePreview(true, null, emptyList(), requiresAcknowledgement = false, backupDays = 30)
        val tick = ok.copy(notCarried = listOf("x"), requiresAcknowledgement = true)
        val base = ShareUiState(title = "T", phase = SharePhase.Setup)
        assertTrue(base.copy(preview = ok).canContinue)
        assertFalse(base.copy(preview = tick).canContinue)
        assertTrue(base.copy(preview = tick, acknowledged = true).canContinue)
        assertFalse(base.copy(preview = ok.copy(canMove = false)).canContinue)
        assertTrue(base.copy(inCloud = true).canContinue)          // already in the cloud: no move to confirm
        assertFalse(base.copy(phase = SharePhase.Moving, preview = ok).canContinue)
    }
}
