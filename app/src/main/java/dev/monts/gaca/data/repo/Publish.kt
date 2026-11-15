package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.PublishCheckRequestDto
import dev.monts.gaca.data.remote.PublishRequestDto
import dev.monts.gaca.data.remote.TemplateDto
import dev.monts.gaca.data.remote.ValidationReportDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where a published page shows up. Both kinds are public to whoever can reach them. */
enum class PublishVisibility(val wire: String, val label: String, val explain: String) {
    Listed("listed", "In the gallery", "Anyone can find it in the gallery and add their own copy."),
    Unlisted("unlisted", "Not listed", "Kept out of the gallery list, but anyone who is given its address can see it and add a copy."),
}

/** What the owner typed. Blank text is sent as nothing, in the check and the publish alike (the hash covers it). */
data class PublishListing(
    val title: String,
    val description: String = "",
    val visibility: PublishVisibility = PublishVisibility.Listed,
) {
    val cleanTitle: String get() = title.trim()
    val cleanDescription: String? get() = description.trim().ifBlank { null }
    val isValid: Boolean get() = cleanTitle.isNotEmpty() && cleanTitle.length <= 120 && (cleanDescription?.length ?: 0) <= 500

    /** The sentence the owner confirms. */
    val summary: String
        get() = "“$cleanTitle” will be public. ${visibility.explain} Only the page and its shapes are published, never " +
            "its data. People who add a copy keep it."
}

/** What would go out, in plain words. Read from the cloud's own preview, never from the page here. */
data class PublishPreview(
    val title: String,
    val description: String?,
    val license: String?,
    val tags: List<String>,
    val pageBytes: Int,
    val shapes: Int,
) {
    val pageSizeText: String get() = if (pageBytes < 1024) "$pageBytes bytes" else "${(pageBytes + 512) / 1024} KB"
}

/** The cloud's publish check: findings, what would go out, and the hash that ties a publish to this exact preview. */
data class PublishReview(val report: ShareReport, val hash: String?, val preview: PublishPreview) {
    /** No hash means the cloud did not give us something to confirm, so nothing can be published. */
    val canPublish: Boolean get() = !report.blocked && !hash.isNullOrBlank()
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

fun ValidationReportDto.toPublishReview(fallbackTitle: String): PublishReview {
    val listing = preview?.get("listing") as? JsonObject
    val tags = (listing?.get("tags") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
    return PublishReview(
        report = toDomain(),
        hash = contentHash?.takeIf { it.isNotBlank() },
        preview = PublishPreview(
            title = listing?.text("title") ?: fallbackTitle,
            description = listing?.text("description"),
            license = listing?.text("license"),
            tags = tags,
            pageBytes = preview?.text("html")?.length ?: 0,
            shapes = (preview?.get("schemas") as? JsonObject)?.size ?: 0,
        ),
    )
}

/** What the cloud made of the publish. A new template is checked first, so it may not be visible yet. */
data class PublishedPage(val title: String, val status: String) {
    val message: String
        get() = when (status) {
            "checking" -> "Published. The cloud is still checking it, so it may take a moment to appear."
            "blocked" -> "The cloud's check blocked it, so it is not visible to anyone."
            else -> "Published."
        }
}

fun TemplateDto.toPublished() = PublishedPage(title, status)

class PublishRepository(private val api: GacaApi) {
    suspend fun check(id: String, listing: PublishListing): PublishReview =
        api.runPublishCheck(id, PublishCheckRequestDto("publish", listing.cleanTitle, listing.cleanDescription))
            .toPublishReview(listing.cleanTitle)

    /** Only after the person confirmed the preview whose [hash] this is. The cloud refuses if anything changed since. */
    suspend fun publish(id: String, listing: PublishListing, hash: String): PublishedPage =
        api.publish(
            id,
            PublishRequestDto(listing.cleanTitle, listing.cleanDescription, listing.visibility.wire, confirmedPreview = true, previewHash = hash),
        ).toPublished()
}
