package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.InvitePreviewDto
import dev.monts.gaca.data.remote.InviteTokenDto
import dev.monts.gaca.data.remote.RedeemedDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `the preview shows the title, the owner, the role and what it allows`() {
        val preview = json.decodeFromString<InvitePreviewDto>(
            """{"gacabit_title":"Trip budget","role":"editor","invited_by":{"id":"u1","handle":"ada","display_name":"Ada L"},"already_member":false,"x":1}""",
        ).toDomain()
        assertEquals("Trip budget", preview.title)
        assertEquals("Ada L", preview.invitedBy)
        assertEquals("Editor", preview.roleLabel)
        assertEquals("You can read and change the page's data. You can't change the page itself.", preview.youCan)
        assertFalse(preview.alreadyMember)
    }

    @Test fun `each role says what the person could do, in the second person`() {
        assertTrue(ShareRole.Viewer.youCan.contains("can't change anything"))
        assertTrue(ShareRole.Maintainer.youCan.contains("edit the page itself"))
        assertEquals(ShareRole.Editor, ShareRole.fromWire("EDITOR"))
        assertNull(ShareRole.fromWire("owner")) // an invite can never grant ownership
    }

    @Test fun `without a display name the handle is used, and a missing owner is just unnamed`() {
        val byHandle = InvitePreviewDto("T", "viewer", dev.monts.gaca.data.remote.AccountRefDto("u", "ada", null)).toDomain()
        assertEquals("ada", byHandle.invitedBy)
        assertNull(InvitePreviewDto("T", "viewer", null).toDomain().invitedBy)
    }

    @Test fun `a role this app does not know is shown as sent but is not described`() {
        val p = InvitePreviewDto("T", "moderator").toDomain()
        assertEquals("Moderator", p.roleLabel)
        assertTrue(p.youCan.contains("doesn't know this kind of access"))
    }

    @Test fun `an untitled page and an already-a-member preview still read well`() {
        val p = InvitePreviewDto(gacabitTitle = "", alreadyMember = true).toDomain()
        assertEquals("Untitled page", p.title)
        assertTrue(p.alreadyMember)
    }

    @Test fun `the token is the only thing sent, and the join answer is read leniently`() {
        assertEquals("""{"token":"abc12345"}""", json.encodeToString(InviteTokenDto("abc12345")))
        val joined = json.decodeFromString<RedeemedDto>("""{"id":"p1","title":"Budget","home":"cloud","role":"editor","member_count":2,"anything":{"a":1}}""")
        assertEquals("p1", joined.id)
        assertEquals("", json.decodeFromString<RedeemedDto>("{}").id)
    }

    @Test fun `the server's codes decide what the person is told`() {
        assertEquals(JoinProblem.NotLinked, joinProblemFor("cloud_not_linked"))
        assertEquals(JoinProblem.NotLinked, joinProblemFor("cloud_not_configured"))
        assertEquals(JoinProblem.NotLinked, joinProblemFor("cloud_link_invalid"))
        assertEquals(JoinProblem.LinkDead, joinProblemFor("invite_expired"))
        assertEquals(JoinProblem.LinkDead, joinProblemFor("not_found"))
        assertEquals(JoinProblem.Other, joinProblemFor("account_limit"))
        assertEquals(JoinProblem.Other, joinProblemFor(null))
    }
}
