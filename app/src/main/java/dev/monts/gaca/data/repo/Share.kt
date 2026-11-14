package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.FindingDto
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.InviteCreateRequestDto
import dev.monts.gaca.data.remote.InviteLinkDto
import dev.monts.gaca.data.remote.MovePreviewDto
import dev.monts.gaca.data.remote.MoveRequestDto
import dev.monts.gaca.data.remote.ValidationReportDto
import dev.monts.gaca.data.remote.ValidationRunRequestDto
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

// ── What a person can be given ───────────────────────────────────────────────

/** The three modes of an invite link. The role is fixed when the link is made; the owner can't be granted. */
enum class ShareRole(val wire: String, val label: String, val canDo: String, val youCan: String) {
    Viewer(
        "viewer", "Viewer", "Can open the page and read its data. Cannot change anything.",
        "You can open the page and read its data. You can't change anything.",
    ),
    Editor(
        "editor", "Editor", "Can read and change the page's data (checked against its shapes). Cannot change the page itself.",
        "You can read and change the page's data. You can't change the page itself.",
    ),
    Maintainer(
        "maintainer", "Maintainer", "Can change the data and edit the page itself. Every edit is versioned and checked, and you can undo it.",
        "You can change the data and edit the page itself. Every edit is versioned and checked, and the owner can undo it.",
    ),
    ;

    companion object {
        fun fromWire(value: String): ShareRole? = entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

val EXPIRY_CHOICES_DAYS = listOf(1, 7, 30)
val USE_CHOICES = listOf(1, 5, 10, 25)

/** What the owner picked for the link. Defaults are the safest: a viewer, for a week, for one person. */
data class ShareOptions(val role: ShareRole = ShareRole.Viewer, val expiryDays: Int = 7, val maxUses: Int = 1) {
    /** The sentence the owner confirms. A link is a secret: whoever holds it can join. */
    val summary: String
        get() = "Anyone who has the link can join as ${role.label.lowercase()}. It works for " +
            "${plural(expiryDays, "day")} and for up to ${plural(maxUses, "person", "people")}."
}

private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

// ── Moving a private page to the cloud (sharing needs it there) ───────────────────

data class MovePreview(
    val canMove: Boolean,
    /** Plain words for why not, when [canMove] is false. */
    val blockedText: String?,
    /** What the cloud will not keep, in plain words. Empty when nothing is lost. */
    val notCarried: List<String>,
    val requiresAcknowledgement: Boolean,
    val backupDays: Int,
)

fun MovePreviewDto.toDomain() = MovePreview(
    canMove = canMove,
    blockedText = if (canMove) null else blockedText(blockedBecause, problems.mapNotNull { problemText(it) }),
    notCarried = notCarriedLines(notCarried),
    requiresAcknowledgement = requiresAcknowledgement,
    backupDays = backupDays,
)

internal fun notCarriedLines(n: dev.monts.gaca.data.remote.NotCarriedDto): List<String> = buildList {
    if (n.versionsDropped > 0) add("${plural(n.versionsDropped, "older version")} of the page (the cloud keeps the latest 5)")
    if (n.handledEventsDropped > 0) add("${plural(n.handledEventsDropped, "event")} already handled")
    if (n.crew != null) add("Which crew member made it")
    if (n.chatAlertLinks != null) add("Its link to the chat or alert it came from")
    if (n.docVersionsReset > 0) add("The edit history of ${plural(n.docVersionsReset, "document")} (they start again at version 1)")
    if (n.lastOpened) add("When you last opened it")
}

private fun problemText(element: kotlinx.serialization.json.JsonElement): String? = when (element) {
    is JsonObject -> (element["item"] as? JsonPrimitive)?.content
    is JsonPrimitive -> element.content
    else -> null
}

internal fun blockedText(code: String?, problems: List<String>): String {
    val base = when (code) {
        "artifact_archived" -> "Archived pages can't be moved. Unarchive it first."
        "id_not_portable" -> "This page's id can't be kept in the cloud, so it can't be moved."
        null -> "This page can't be moved to the cloud."
        else -> "This page can't be moved to the cloud yet."
    }
    return if (problems.isEmpty()) base else base + " " + problems.distinct().joinToString("; ") + "."
}

/** Where a page stands after the connection to the server dropped during a move. */
sealed interface MoveSettled {
    data object Done : MoveSettled
    data object StillMoving : MoveSettled
    /** The move failed or never started; the page here is as it was and it is safe to try again. */
    data object Failed : MoveSettled
}

/** Reads the page the way the list does. A move is safe to retry, so the only question is whether it ended. */
fun settle(page: Artifact): MoveSettled = when {
    page.moving || page.cloudStatus == "moving" -> MoveSettled.StillMoving
    page.home == ArtifactHome.Cloud -> MoveSettled.Done
    else -> MoveSettled.Failed
}

/**
 * After the connection dropped mid-move: ask the server about the page every [everyMillis] until it says the move
 * ended. A lookup that itself fails (still offline) is just tried again. Gives up after [polls] looks.
 */
internal suspend fun awaitMove(everyMillis: Long, polls: Int, fetch: suspend () -> Artifact): Artifact {
    repeat(polls) {
        delay(everyMillis)
        val page = try { fetch() } catch (e: IOException) { return@repeat }
        when (settle(page)) {
            MoveSettled.Done -> return page
            MoveSettled.Failed -> throw MoveNotFinished("The move did not finish. Your page here is unchanged; you can try again.")
            MoveSettled.StillMoving -> Unit
        }
    }
    throw MoveNotFinished(
        "Lost the connection while moving. Check the list in a moment: if the page still shows as moving, wait; " +
            "otherwise try again (it is safe to retry).",
    )
}

/** The move did not (or may not) have finished. [message] says what to do. */
class MoveNotFinished(message: String) : Exception(message)

// ── Checks and the link ───────────────────────────────────────────────

enum class FindingSeverity { Block, Warn, Info }

data class Finding(val severity: FindingSeverity, val message: String, val location: String?)

/** The cloud's check of a page before it is shared. A block means it cannot be shared as it is. */
data class ShareReport(val findings: List<Finding>, val blocked: Boolean) {
    val warnings get() = findings.count { it.severity == FindingSeverity.Warn }
}

private fun FindingDto.toDomain() = Finding(
    severity = when (severity) { "block" -> FindingSeverity.Block; "warn" -> FindingSeverity.Warn; else -> FindingSeverity.Info },
    message = message, location = location?.takeIf { it.isNotBlank() },
)

fun ValidationReportDto.toDomain(): ShareReport {
    val found = findings.map { it.toDomain() }.sortedBy { it.severity.ordinal }
    return ShareReport(found, blocked = status == "blocked" || found.any { it.severity == FindingSeverity.Block })
}

/** The invite link, shown once. Nothing here is saved anywhere. */
data class CreatedInvite(val url: String, val role: String, val expiresAt: String?, val maxUses: Int)

fun InviteLinkDto.toCreated(): CreatedInvite? =
    url?.takeIf { it.isNotBlank() }?.let { CreatedInvite(it, role, expiresAt, maxUses) }

class ShareRepository(private val api: GacaApi, private val longApi: GacaApi) {
    suspend fun preview(id: String): MovePreview = api.movePreview(id).toDomain()

    /**
     * Moves the page (and its data) to the cloud and returns it as a cloud page. The server blocks until it is
     * done. If the phone's connection drops while waiting, the server may still be working, so we ask the server how
     * the page stands (it is safe to retry a move) instead of giving up at once.
     */
    suspend fun moveToCloud(id: String, acknowledged: Boolean, pollEveryMillis: Long = 3_000, polls: Int = 40): Artifact =
        try {
            longApi.moveToCloud(id, MoveRequestDto(acknowledged)).artifact.toDomain()
        } catch (e: IOException) {
            recover(id, pollEveryMillis, polls)
        }

    private suspend fun recover(id: String, everyMillis: Long, polls: Int): Artifact =
        awaitMove(everyMillis, polls) { api.getArtifact(id).toDomain() }

    suspend fun check(id: String): ShareReport = api.runValidation(id, ValidationRunRequestDto("share")).toDomain()

    suspend fun createInvite(id: String, options: ShareOptions): CreatedInvite =
        api.createInvite(id, InviteCreateRequestDto(options.role.wire, options.expiryDays, options.maxUses)).toCreated()
            ?: throw IllegalStateException("The cloud did not return a link. Nothing was shared; try again.")
}
