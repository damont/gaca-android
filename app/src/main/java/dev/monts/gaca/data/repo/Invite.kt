package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.InvitePreviewDto
import dev.monts.gaca.data.remote.InviteTokenDto

/** What an invite link would give the person, shown before they accept ("Trust and add"). */
data class InvitePreview(
    val title: String,
    /** The owner's name. Null if the cloud did not say. */
    val invitedBy: String?,
    /** The word the cloud sent (viewer, editor, maintainer). */
    val roleWord: String,
    /** What the role allows, in the person's own terms. */
    val youCan: String,
    val alreadyMember: Boolean,
) {
    val roleLabel: String get() = ShareRole.fromWire(roleWord)?.label ?: roleWord.replaceFirstChar { it.uppercase() }
}

fun InvitePreviewDto.toDomain(): InvitePreview {
    val role = ShareRole.fromWire(role)
    return InvitePreview(
        title = gacabitTitle.ifBlank { "Untitled page" },
        invitedBy = invitedBy?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.handle.takeIf(String::isNotBlank) },
        roleWord = this.role,
        // An access level this version does not know is described as nothing, not guessed at.
        youCan = role?.youCan ?: "This app doesn't know this kind of access yet, so it can't tell you what it allows.",
        alreadyMember = alreadyMember,
    )
}

/** A page the person was just added to. */
data class JoinedPage(val id: String, val title: String)

class JoinRepository(private val api: GacaApi) {
    suspend fun preview(token: String): InvitePreview = api.previewInvite(InviteTokenDto(token)).toDomain()

    /** Accepts the link. Joining twice is harmless on the cloud's side (already a member is a no-op). */
    suspend fun join(token: String): JoinedPage = api.redeemInvite(InviteTokenDto(token)).let { JoinedPage(it.id, it.title) }
}

/** What went wrong with a link, as the cloud's stable codes tell it. */
enum class JoinProblem { NotLinked, LinkDead, Other }

/** Maps the server's error code. [Other] errors are shown with the server's own message. */
fun joinProblemFor(code: String?): JoinProblem = when (code) {
    "cloud_not_linked", "cloud_not_configured", "cloud_link_invalid" -> JoinProblem.NotLinked
    "invite_expired", "not_found" -> JoinProblem.LinkDead
    else -> JoinProblem.Other
}

const val LINK_DEAD_TEXT =
    "This link isn't valid any more. It may have expired, been cancelled, or been used up. Ask the person who shared it for a new one."
