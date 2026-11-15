package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.BringHomeRequestDto
import dev.monts.gaca.data.remote.BringHomeResultDto
import dev.monts.gaca.data.remote.PublishCheckRequestDto
import dev.monts.gaca.data.remote.PublishRequestDto
import dev.monts.gaca.data.remote.ValidationReportDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun page(home: ArtifactHome = ArtifactHome.Cloud, role: ArtifactRole = ArtifactRole.Owner, members: Int = 1, moving: Boolean = false) =
        Artifact("a", "Garden", null, null, null, null, false, 1, "2026-11-10T12:00:00Z", null, home, role, null, members, moving, null, null)

    @Test fun `the check and the publish carry the same listing text, and the publish names the confirmation`() {
        val listing = PublishListing("  Garden  ", "   ", PublishVisibility.Unlisted)
        assertEquals("Garden", listing.cleanTitle)
        assertNull(listing.cleanDescription)
        assertEquals(
            """{"title":"Garden"}""",
            json.encodeToString(PublishCheckRequestDto(listing.cleanTitle, listing.cleanDescription)),
        )
        assertEquals(
            """{"title":"Garden","visibility":"unlisted","confirmed_preview":true,"preview_hash":"h1"}""",
            json.encodeToString(PublishRequestDto(listing.cleanTitle, listing.cleanDescription, listing.visibility.wire, true, "h1")),
        )
    }

    @Test fun `a listing needs a title and respects the cloud's limits`() {
        assertFalse(PublishListing("  ").isValid)
        assertTrue(PublishListing("A").isValid)
        assertFalse(PublishListing("x".repeat(121)).isValid)
        assertFalse(PublishListing("A", "y".repeat(501)).isValid)
        assertTrue(PublishListing("A", "y".repeat(500)).isValid)
    }

    @Test fun `the confirmation says it goes public, shows no data and that copies stay`() {
        val text = PublishListing("Garden").summary
        assertTrue(text.contains("“Garden” will be public"))
        assertTrue(text.contains("never its data"))
        assertTrue(text.contains("keep it"))
    }

    @Test fun `the preview is read from what the cloud sent`() {
        val dto = json.decodeFromString<ValidationReportDto>(
            """{"status":"warned","content_hash":"abc","findings":[{"severity":"warn","message":"Big"}],
               "preview":{"listing":{"title":"Garden","description":"Plants","tags":["home","plants"],"license":"cc-by-4.0"},
               "html":"<p>hi</p>","schemas":{"a":{},"b":{}},"data":{}}}""",
        )
        val review = dto.toPublishReview("fallback")
        assertEquals("abc", review.hash)
        assertTrue(review.canPublish)
        assertEquals("Garden", review.preview.title)
        assertEquals("Plants", review.preview.description)
        assertEquals(listOf("home", "plants"), review.preview.tags)
        assertEquals(2, review.preview.shapes)
        assertEquals("9 bytes", review.preview.pageSizeText)
        assertEquals(1, review.report.warnings)
    }

    @Test fun `without a hash or with a block nothing can be published`() {
        assertFalse(ValidationReportDto(status = "passed").toPublishReview("T").canPublish)
        assertFalse(ValidationReportDto(status = "blocked", contentHash = "h").toPublishReview("T").canPublish)
        assertTrue(ValidationReportDto(status = "passed", contentHash = "h").toPublishReview("T").canPublish)
        assertEquals("T", ValidationReportDto(contentHash = "h").toPublishReview("T").preview.title)
    }

    @Test fun `page sizes read in plain units`() {
        assertEquals("2 KB", PublishPreview("t", null, null, emptyList(), 2048, 0).pageSizeText)
        assertEquals("0 bytes", PublishPreview("t", null, null, emptyList(), 0, 0).pageSizeText)
    }

    @Test fun `a new template may still be checked, and a blocked one is not visible`() {
        assertEquals("Published.", PublishedPage("t", "listed").message)
        assertTrue(PublishedPage("t", "checking").message.contains("still checking"))
        assertTrue(PublishedPage("t", "blocked").message.contains("not visible"))
    }

    @Test fun `any idle page you own can be published, private or in the cloud, but only a cloud page comes home`() {
        assertTrue(page().canPublish)
        assertTrue(page(home = ArtifactHome.Local).canPublish)
        assertTrue(page().canBringHome)
        assertFalse(page(home = ArtifactHome.Local).canBringHome)
        assertFalse(page(moving = true).canPublish)
        assertFalse(page(moving = true).canBringHome)
        assertFalse(page(role = ArtifactRole.Editor).canPublish)
    }

    @Test fun `only a page somebody else shared can be left`() {
        assertTrue(page(role = ArtifactRole.Viewer).canLeave)
        assertFalse(page().canLeave)
        assertFalse(page(role = ArtifactRole.Viewer, home = ArtifactHome.Local).canLeave)
    }

    @Test fun `bringing home says who loses access, and only then needs the tick`() {
        assertFalse(page(members = 1).needsMemberAck)
        assertTrue(page(members = 3).needsMemberAck)
        assertTrue(bringHomeWarning(page(members = 3)).startsWith("2 other people will lose access to “Garden”."))
        assertTrue(bringHomeWarning(page(members = 2)).startsWith("1 other person will lose access"))
        assertFalse(bringHomeWarning(page(members = 1)).contains("lose access"))
    }

    @Test fun `the bring home request and answer`() {
        assertEquals("""{"acknowledge_members":true}""", json.encodeToString(BringHomeRequestDto(true)))
        val dto = json.decodeFromString<BringHomeResultDto>(
            """{"artifact":{"id":"a","title":"Garden","created_at":"2026-11-10T12:00:00Z","updated_at":"2026-11-10T12:00:00Z"},
               "confirmed":false,"undo_until":"2026-11-22T00:00:00Z","warning":"The cloud will be told later.","members_lost_access":2}""",
        )
        val home = dto.toDomain()
        assertEquals("2026-11-22T00:00:00Z", home.undoUntil)
        assertEquals(2, home.membersLostAccess)
        assertEquals("The cloud will be told later.", home.warning)
    }
}
