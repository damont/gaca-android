package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.CloudAgentAccessRequest
import dev.monts.gaca.data.remote.CloudLinkPollDto
import dev.monts.gaca.data.remote.CloudLinkStartRequest
import dev.monts.gaca.data.remote.CloudLinkStartedDto
import dev.monts.gaca.data.remote.CloudLinkStatusDto
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.util.apiError
import dev.monts.gaca.util.infoInt
import dev.monts.gaca.util.parseInstant
import java.time.Instant

/** Where this server's link to the person's GACA Cloud account stands. The cloud's token never reaches the app. */
enum class CloudLinkKind { NotLinked, Pending, Linked, Invalid }

data class CloudLinkStatus(
    val kind: CloudLinkKind,
    /** The cloud account's display name. */
    val handle: String? = null,
    /** `manage` (can move, share, publish) or `read` (can only list and read). */
    val scope: String? = null,
    val linkedAt: Instant? = null,
    /** May the person's agents (Hermes) use the cloud? Their choice, off by default. */
    val agentAccess: Boolean = false,
    /** False: this server has no cloud address, so there is nothing to link to. */
    val configured: Boolean = true,
    /** While [kind] is Pending and the code is still valid: the code to show again. */
    val pending: LinkAttempt? = null,
) {
    val isLinked get() = kind == CloudLinkKind.Linked
}

fun CloudLinkStatusDto.toDomain() = CloudLinkStatus(
    kind = when (state) {
        "linked" -> CloudLinkKind.Linked
        "pending" -> CloudLinkKind.Pending
        "invalid" -> CloudLinkKind.Invalid
        else -> CloudLinkKind.NotLinked
    },
    handle = handle, scope = scope, linkedAt = parseInstant(linkedAt),
    agentAccess = agentAccess, configured = configured,
    pending = pendingAttempt(userCode, verificationUri, parseInstant(expiresAt), interval),
)

/** A link attempt in progress: the person types or checks [userCode] on the page at [verificationUri]. */
data class LinkAttempt(
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Instant,
    val intervalSeconds: Int,
)

/** The attempt the server still remembers, or null when any part is missing (an expired code comes back as nulls). */
internal fun pendingAttempt(code: String?, uri: String?, expiresAt: Instant?, interval: Int?): LinkAttempt? =
    if (code == null || uri == null || expiresAt == null) null else LinkAttempt(code, uri, expiresAt, interval ?: 5)

fun CloudLinkStartedDto.toAttempt(now: Instant) = LinkAttempt(
    userCode = userCode, verificationUri = verificationUri,
    expiresAt = now.plusSeconds(expiresIn.toLong()), intervalSeconds = interval,
)

/** What one poll answer means for the screen: keep waiting (and for how long), or it ended. */
sealed interface PollStep {
    data class Wait(val delayMillis: Long) : PollStep
    data class Done(val status: CloudLinkStatus) : PollStep
    data object Expired : PollStep
    data object Denied : PollStep
}

/** Seconds between polls the server will accept. `retry_in` (called too soon) wins over `interval`; never less than 1. */
fun CloudLinkPollDto.toStep(fallbackIntervalSeconds: Int): PollStep = when (status) {
    "linked" -> PollStep.Done(
        CloudLinkStatus(CloudLinkKind.Linked, handle = handle, scope = scope, linkedAt = parseInstant(linkedAt)),
    )
    "expired" -> PollStep.Expired
    "denied" -> PollStep.Denied
    else -> PollStep.Wait(maxOf(1, retryIn ?: interval ?: fallbackIntervalSeconds) * 1000L)
}

/** Why an unlink was held back: some pages live only in the cloud, and unlinking would cut this server off from them. */
data class UnlinkBlock(val owned: Int, val shared: Int, val inProgress: Int)

sealed interface UnlinkResult {
    /** [message] is the server's own words, e.g. that the cloud could not confirm revoking the link. */
    data class Done(val message: String) : UnlinkResult
    data class Blocked(val block: UnlinkBlock) : UnlinkResult
}

class CloudLinkRepository(private val api: GacaApi) {
    suspend fun status(): CloudLinkStatus = api.getCloudLink().toDomain()

    /** The owner's switch for their agents. The server answers with the whole status. */
    suspend fun setAgentAccess(enabled: Boolean): CloudLinkStatus =
        api.setCloudAgentAccess(CloudAgentAccessRequest(enabled)).toDomain()

    /** Starts linking with the `manage` scope (the app can move, share and publish on the person's behalf, each only after they confirm). */
    suspend fun start(now: Instant = Instant.now()): LinkAttempt =
        api.startCloudLink(CloudLinkStartRequest(scope = "manage")).toAttempt(now)

    suspend fun poll(fallbackIntervalSeconds: Int): PollStep = api.pollCloudLink().toStep(fallbackIntervalSeconds)

    /** Unlinks. Without [force] the server refuses (409 `cloud_artifacts_present`) while pages live in the cloud. */
    suspend fun unlink(force: Boolean): UnlinkResult = try {
        UnlinkResult.Done(api.unlinkCloud(force).message)
    } catch (e: retrofit2.HttpException) {
        val err = e.apiError()
        if (err?.code == "cloud_artifacts_present") {
            UnlinkResult.Blocked(UnlinkBlock(err.infoInt("owned") ?: 0, err.infoInt("shared") ?: 0, err.infoInt("in_progress") ?: 0))
        } else {
            throw e
        }
    }
}
