package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertCreateRequest
import dev.monts.gaca.data.remote.AlertDelegateRequest
import dev.monts.gaca.data.remote.AlertResolveRequest
import dev.monts.gaca.data.remote.AlertSnoozeRequest
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.util.friendlyMessage
import retrofit2.HttpException

/**
 * api-contract.md §5: plain polling, no stream. `GET /alerts` on
 * app-foreground plus pull-to-refresh is the whole story — no local
 * derivation of expired snoozes or legal transitions; the server owns that
 * (§7: "the app does not compute state").
 */
class AlertsRepository(private val api: GacaApi) {

    suspend fun listAlerts(statuses: List<String>, agent: String? = null, limit: Int = 50): List<Alert> =
        api.listAlerts(statuses = statuses, agents = agent?.let { listOf(it) }, limit = limit)
            .alerts.map { it.toDomain() }

    suspend fun getAlert(id: String): Alert = api.getAlert(id).toDomain()

    /** POST /alerts with a normal session token — see [dev.monts.gaca.data.remote.AlertCreateRequest].
     * Lets the app raise its own alert (rather than waiting on Hermes to notice something) purely
     * so it has something to hand to [delegate] right after. `agent` is who this alert is
     * attributed to — set it to whoever you're about to delegate to, same as Hermes would set its
     * own name: `delegate()` never writes the resolved agent back onto the alert afterwards
     * (gaca-api services/alerts.py), so this is the only chance to make the agent filter and the
     * detail screen's picker match reality. */
    suspend fun create(kind: String, title: String, body: String?, severity: String = "info", agent: String? = null): Alert =
        api.createAlert(AlertCreateRequest(kind = kind, title = title, severity = severity, body = body, agent = agent)).toDomain()

    /** The session transcript behind a delegated alert — `null` if it was never delegated. */
    suspend fun getConversation(sessionId: String): List<ConversationMessage> =
        api.getSession(sessionId).messages.map { it.toDomain() }

    suspend fun act(id: String, note: String?): Alert =
        api.actOnAlert(id, AlertResolveRequest(note)).toDomain()

    suspend fun dismiss(id: String, note: String?): Alert =
        api.dismissAlert(id, AlertResolveRequest(note)).toDomain()

    suspend fun snooze(id: String, untilIso: String): Alert =
        api.snoozeAlert(id, AlertSnoozeRequest(untilIso)).toDomain()

    suspend fun delegate(
        id: String,
        instruction: String?,
        suggestedActionIndex: Int?,
        agent: String? = null,
    ): Alert = api.delegateAlert(id, AlertDelegateRequest(instruction, suggestedActionIndex, agent)).toDomain()
}

/** api-contract.md §6: a 409 means the alert moved underneath you — refetch and re-render, don't retry the action. */
fun Throwable.isStaleTransition(): Boolean = this is HttpException && code() == 409

/** api-contract.md §6: a 400 on /delegate means this account has no agent that resolves — no host,
 * no agents, or no default. Agents are managed in-app now (Settings), not the old web profile page. */
fun Throwable.delegateErrorMessage(): String =
    if (this is HttpException && code() == 400) {
        "Set up an agent in Settings first, then try delegating again."
    } else {
        friendlyMessage()
    }
