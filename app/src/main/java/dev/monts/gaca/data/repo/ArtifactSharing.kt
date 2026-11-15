package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.TemplateDto

/** Where a page lives: on the person's own server, or in the GACA Cloud (the server fetches it from there). */
enum class ArtifactHome { Local, Cloud }

/** What the person may do with a page. Owner > Maintainer > Editor > Viewer. */
enum class ArtifactRole(val label: String) {
    Owner("Owner"), Maintainer("Maintainer"), Editor("Editor"), Viewer("Viewer");

    companion object {
        /** An unknown word is treated as the least a person can have, so nothing is offered that the cloud may refuse. */
        fun parse(value: String): ArtifactRole = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Viewer
    }
}

/** The chips on the Artifacts tab. Mine and Shared with me split the list; Gallery is a different list altogether. */
enum class ArtifactFilter(val label: String) { Mine("Mine"), Shared("Shared with me"), Gallery("Gallery") }

/** The badge on a row. A page can carry more than one (shared and published). */
enum class ArtifactBadge(val label: String) { Private("Private"), Shared("Shared"), Published("Published") }

val Artifact.isMine: Boolean get() = role == ArtifactRole.Owner

/** Somebody else's page that you were let into. */
val Artifact.isSharedWithMe: Boolean get() = !isMine

/** Archive and delete belong to the owner: the cloud refuses them to everybody else. */
val Artifact.canManage: Boolean get() = isMine

/** Only the owner shares, and not while it is archived or in the middle of a move. */
val Artifact.canShare: Boolean get() = isMine && !archived && !moving && cloudStatus !in setOf("moving", "read_only", "brought_home")

/** Publishing is about the page's code, not people: any page you own, private or in the cloud, that isn't busy. */
val Artifact.canPublish: Boolean get() = isMine && !archived && !moving && cloudStatus != "moving"

/** Bringing home only makes sense for a cloud page you own. */
val Artifact.canBringHome: Boolean get() = canShare && home == ArtifactHome.Cloud

/** A page someone shared with you, which you can walk away from. */
val Artifact.canLeave: Boolean get() = isSharedWithMe && home == ArtifactHome.Cloud && !moving

fun Artifact.matches(filter: ArtifactFilter): Boolean = when (filter) {
    ArtifactFilter.Mine -> isMine
    ArtifactFilter.Shared -> isSharedWithMe
    ArtifactFilter.Gallery -> false
}

/**
 * Private (only you), Shared (someone else is in it, or it is theirs), Published (in the gallery). A page in the
 * cloud with nobody else in it is still private.
 */
val Artifact.badges: List<ArtifactBadge>
    get() = buildList {
        val shared = home == ArtifactHome.Cloud && (memberCount > 1 || !isMine)
        if (shared) add(ArtifactBadge.Shared)
        if (publishedTemplateId != null) add(ArtifactBadge.Published)
        if (isEmpty()) add(ArtifactBadge.Private)
    }

/** "Shared by ada", for somebody else's page. */
val Artifact.sharedByLine: String? get() = if (isSharedWithMe) "Shared by ${sharedBy ?: "someone"}" else null

/** For your own shared page: how many people are in it, you included. */
val Artifact.peopleLine: String? get() = if (isMine && home == ArtifactHome.Cloud && memberCount > 1) "$memberCount people" else null

/** A short status for pages in the middle of a transfer, else null. */
val Artifact.transferLine: String?
    get() = when {
        moving || cloudStatus == "moving" -> "Moving to the cloud…"
        cloudStatus == "move_failed" -> "Last move did not finish"
        cloudStatus == "bring_home_unconfirmed" -> "Brought home, syncing with the cloud"
        cloudStatus == "read_only" -> "Read-only"
        cloudStatus == "brought_home" -> "Brought home"
        else -> null
    }

// ── Gallery ───────────────────────────────────────────────────────

/** A public template other people published. */
data class Template(
    val id: String,
    val title: String,
    val description: String?,
    val author: String?,
    val tags: List<String>,
    val category: String?,
    val downloads: Int,
)

fun TemplateDto.toDomain() = Template(
    id = id, title = title, description = description?.takeIf { it.isNotBlank() },
    author = author?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.handle.takeIf(String::isNotBlank) },
    tags = tags, category = category?.takeIf { it.isNotBlank() }, downloads = downloads,
)

fun Template.matches(query: String): Boolean = query.isBlank() ||
    title.contains(query, ignoreCase = true) || description.orEmpty().contains(query, ignoreCase = true) ||
    tags.any { it.contains(query, ignoreCase = true) }

data class TemplatePage(val templates: List<Template>, val nextCursor: String?)

class TemplatesRepository(private val api: GacaApi) {
    suspend fun page(cursor: String? = null): TemplatePage {
        val dto = api.listTemplates(limit = PAGE_SIZE, cursor = cursor)
        // Only what is listed: the cloud also returns your own unlisted or blocked ones.
        return TemplatePage(dto.templates.filter { it.status == "listed" }.map { it.toDomain() }, dto.nextCursor)
    }

    /** Makes a private copy on the person's server. Returns the new page's id. */
    suspend fun install(templateId: String): String = api.installTemplate(templateId).id

    private companion object { const val PAGE_SIZE = 30 }
}
