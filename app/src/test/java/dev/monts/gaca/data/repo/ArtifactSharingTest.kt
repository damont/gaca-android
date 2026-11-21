package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.ArtifactListDto
import dev.monts.gaca.data.remote.TemplateListDto
import dev.monts.gaca.ui.artifact.ArtifactsUiState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactSharingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun page(
        id: String = "a", home: ArtifactHome = ArtifactHome.Local, role: ArtifactRole = ArtifactRole.Owner,
        sharedBy: String? = null, members: Int = 1, published: String? = null, cloudStatus: String? = null, moving: Boolean = false,
    ) = Artifact(
        id = id, title = "T$id", description = null, crew = null, sessionId = null, alertId = null, archived = false,
        version = 1, updatedAt = "2026-11-10T12:00:00Z", lastOpenedAt = null, home = home, role = role, sharedBy = sharedBy,
        memberCount = members, moving = moving, cloudStatus = cloudStatus, publishedTemplateId = published,
    )

    @Test fun `an old server's list, with none of the cloud fields, reads as private pages you own`() {
        val list = json.decodeFromString<ArtifactListDto>(
            """{"artifacts":[{"id":"a","title":"Trip","created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-02T00:00:00Z"}]}""",
        )
        val a = list.artifacts.single().toDomain()
        assertEquals(ArtifactHome.Local, a.home)
        assertEquals(ArtifactRole.Owner, a.role)
        assertEquals(listOf(ArtifactBadge.Private), a.badges)
        assertFalse(list.stale)
        assertTrue(a.matches(ArtifactFilter.Pages))
    }

    @Test fun `the cloud-aware list decodes home, role, owner, members, published and stale`() {
        val list = json.decodeFromString<ArtifactListDto>(
            """{"stale":true,"artifacts":[{"id":"b","title":"Budget","created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-02T00:00:00Z",
               "home":"cloud","role":"editor","shared_by":"ada","member_count":3,"moving":false,"cloud_status":"active","published_template_id":null,"new_field":1}]}""",
        )
        assertTrue(list.stale)
        val a = list.artifacts.single().toDomain()
        assertEquals(ArtifactHome.Cloud, a.home)
        assertEquals(ArtifactRole.Editor, a.role)
        assertEquals("ada", a.sharedBy)
        assertEquals(3, a.memberCount)
        assertNull(a.publishedTemplateId)
    }

    @Test fun `My pages shows everything you can use, and each card says whose it is and where it lives`() {
        val own = page("1")
        val ownInCloud = page("2", ArtifactHome.Cloud)
        val theirs = page("3", ArtifactHome.Cloud, ArtifactRole.Viewer, sharedBy = "ada")
        assertEquals(listOf("1", "2", "3"), listOf(own, ownInCloud, theirs).filter { it.matches(ArtifactFilter.Pages) }.map { it.id })
        assertEquals(listOf("Mine", "Mine", "Shared with me"), listOf(own, ownInCloud, theirs).map { it.ownershipLabel })
        assertEquals(listOf("This server", "Cloud", "Cloud"), listOf(own, ownInCloud, theirs).map { it.locationLabel })
        assertFalse(own.matches(ArtifactFilter.Gallery))
    }

    @Test fun `badges are private by default, shared when others are in it or it is theirs, published when in the gallery`() {
        assertEquals(listOf(ArtifactBadge.Private), page().badges)
        assertEquals(listOf(ArtifactBadge.Private), page(home = ArtifactHome.Cloud, members = 1).badges) // in the cloud, nobody else yet
        assertEquals(listOf(ArtifactBadge.Shared), page(home = ArtifactHome.Cloud, members = 3).badges)
        assertEquals(listOf(ArtifactBadge.Shared), page(home = ArtifactHome.Cloud, role = ArtifactRole.Viewer, sharedBy = "ada", members = 2).badges)
        assertEquals(listOf(ArtifactBadge.Shared, ArtifactBadge.Published), page(home = ArtifactHome.Cloud, members = 2, published = "t1").badges)
        assertEquals(listOf(ArtifactBadge.Published), page(home = ArtifactHome.Cloud, published = "t1").badges)
    }

    @Test fun `somebody else's page names its owner and shows your role, your own shared page counts people`() {
        val theirs = page(home = ArtifactHome.Cloud, role = ArtifactRole.Maintainer, sharedBy = "ada")
        assertEquals("Shared by ada", theirs.sharedByLine)
        assertEquals("Maintainer", theirs.role.label)
        assertNull(theirs.peopleLine)
        assertEquals("Shared by someone", page(home = ArtifactHome.Cloud, role = ArtifactRole.Viewer).sharedByLine)
        val mine = page(home = ArtifactHome.Cloud, members = 4)
        assertNull(mine.sharedByLine)
        assertEquals("4 people", mine.peopleLine)
        assertNull(page().peopleLine)
    }

    @Test fun `only the owner may archive or delete`() {
        assertTrue(page().canManage)
        assertFalse(page(home = ArtifactHome.Cloud, role = ArtifactRole.Maintainer).canManage)
    }

    @Test fun `an unknown role is treated as viewer, never as owner`() {
        assertEquals(ArtifactRole.Viewer, ArtifactRole.parse("something-new"))
        assertEquals(ArtifactRole.Owner, ArtifactRole.parse("owner"))
        assertEquals(ArtifactRole.Maintainer, ArtifactRole.parse("MAINTAINER"))
    }

    @Test fun `transfer lines for pages in the middle of a move`() {
        assertEquals("Moving to the cloud…", page(moving = true).transferLine)
        assertEquals("Moving to the cloud…", page(cloudStatus = "moving").transferLine)
        assertEquals("Last move did not finish", page(cloudStatus = "move_failed").transferLine)
        assertNull(page(cloudStatus = "active").transferLine)
        assertNull(page().transferLine)
    }

    @Test fun `the screen's visible list follows the view and the search together`() {
        val all = listOf(page("1"), page("2", ArtifactHome.Cloud, ArtifactRole.Editor, sharedBy = "ada"), page("3"))
        val state = ArtifactsUiState(artifacts = all)
        assertEquals(listOf("1", "2", "3"), state.visible.map { it.id })
        assertEquals(listOf("3"), state.copy(query = "t3").visible.map { it.id })
        assertTrue(state.copy(filter = ArtifactFilter.Gallery).visible.isEmpty())
    }

    @Test fun `gallery templates map from the cloud's shape and search by title, text and tag`() {
        val dto = json.decodeFromString<TemplateListDto>(
            """{"templates":[{"id":"t1","title":"Trip planner","description":"Plan a trip","tags":["travel","list"],"category":"life",
               "author":{"id":"u1","handle":"ada","display_name":"Ada L"},"status":"listed","downloads":12,"preview_url":"x"},
               {"id":"t2","title":"Mine, unlisted","author":{"id":"u2","handle":"me"},"status":"unlisted"}],"next_cursor":"c2"}""",
        )
        assertEquals("c2", dto.nextCursor)
        val t = dto.templates.first().toDomain()
        assertEquals("Ada L", t.author)
        assertEquals(12, t.downloads)
        assertTrue(t.matches("trip")); assertTrue(t.matches("TRAVEL")); assertTrue(t.matches("plan a")); assertTrue(t.matches(""))
        assertFalse(t.matches("budget"))
        assertEquals("me", dto.templates[1].toDomain().author) // no display name: falls back to the handle
    }
}
