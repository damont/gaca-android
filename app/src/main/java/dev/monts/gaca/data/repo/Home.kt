package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.BringHomeRequestDto
import dev.monts.gaca.data.remote.BringHomeResultDto
import dev.monts.gaca.data.remote.GacaApi
import java.io.IOException

/** A page that came back to this server. The cloud keeps its copy for a while so the person can change their mind. */
data class BroughtHome(val artifact: Artifact, val undoUntil: String?, val warning: String?, val membersLostAccess: Int)

fun BringHomeResultDto.toDomain() = BroughtHome(artifact.toDomain(), undoUntil?.takeIf { it.isNotBlank() }, warning?.takeIf { it.isNotBlank() }, membersLostAccess)

/** What the person is told before a bring home. Other members lose access, so that is said first. */
fun bringHomeWarning(page: Artifact): String {
    val others = (page.memberCount - 1).coerceAtLeast(0)
    val base = "The page and its data move back to your server and leave the cloud (the cloud keeps its copy for a few days so you can undo)."
    return if (others > 0) {
        "${if (others == 1) "1 other person" else "$others other people"} will lose access to “${page.title}”. $base"
    } else {
        base
    }
}

/** Other people share the page, so bringing it home needs the person's tick about them. */
val Artifact.needsMemberAck: Boolean get() = memberCount > 1

class HomeRepository(private val api: GacaApi, private val longApi: GacaApi) {
    /** Bring a page you own back here. Safe to retry; a dropped connection says so instead of guessing. */
    suspend fun bringHome(page: Artifact): BroughtHome =
        try {
            longApi.bringHome(page.id, BringHomeRequestDto(acknowledgeMembers = page.needsMemberAck)).toDomain()
        } catch (e: IOException) {
            throw MoveNotFinished(
                "Lost the connection while bringing it home. Check the list in a moment: if the page is still in the cloud, " +
                    "it is safe to try again.",
            )
        }

    /** Cancel a bring home while the cloud still has its copy. */
    suspend fun undoBringHome(id: String): Boolean = api.undoBringHome(id).undone

    suspend fun leave(id: String) = api.leaveGacabit(id)
}
