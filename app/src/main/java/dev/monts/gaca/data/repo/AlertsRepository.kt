package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertCreateRequest
import dev.monts.gaca.data.remote.AlertDelegateRequest
import dev.monts.gaca.data.remote.AlertCloseRequest
import dev.monts.gaca.data.remote.AlertTakeToChatRequest
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.util.friendlyMessage
import retrofit2.HttpException

/**
 * Plain polling, no stream. `GET /alerts` on app-foreground plus pull-to-refresh, and a short poll
 * of one alert while an action runs. The app computes no state: what each action reads as, whether
 * it can be tapped and which buttons close the alert all come from the API (`actions`, `workflow`).
 */
class AlertsRepository(private val api: GacaApi) {

    suspend fun listAlerts(statuses: List<String>, crew: String? = null, limit: Int = 50): List<Alert> =
        api.listAlerts(statuses = statuses, crew = crew?.let { listOf(it) }, limit = limit)
            .alerts.map { it.toDomain() }

    suspend fun getAlert(id: String): Alert = api.getAlert(id).toDomain()

    /** POST /alerts with a normal session token — see [dev.monts.gaca.data.remote.AlertCreateRequest].
     * Lets the app raise its own alert (rather than waiting on Hermes to notice something) purely
     * so it has something to hand to [delegate] right after. `crew` is who this alert is
     * attributed to — set it to whoever you're about to delegate to, same as Hermes would set its
     * own name: `delegate()` never writes the resolved crew member back onto the alert afterwards
     * (gaca-api services/alerts.py), so this is the only chance to make the crew filter and the
     * detail screen's picker match reality. */
    suspend fun create(kind: String, title: String, body: String?, severity: String = "info", crew: String? = null): Alert =
        api.createAlert(AlertCreateRequest(kind = kind, title = title, severity = severity, body = body, crew = crew)).toDomain()

    /** The session transcript behind a delegated alert — `null` if it was never delegated. */
    suspend fun getConversation(sessionId: String): List<ConversationMessage> =
        api.getSession(sessionId).messages.map { it.toDomain() }

    /** Close an open alert with one of the reasons the API offered in [Workflow.closeOptions]. */
    suspend fun close(id: String, reason: String): Alert =
        api.closeAlert(id, AlertCloseRequest(reason)).toDomain()

    /** Tap an action. Returns the alert with the run in flight; read it again to see it settle. */
    suspend fun runAction(id: String, index: Int): Alert = api.runAlertAction(id, index).toDomain()

    /** Opens the chat for an alert WITHOUT sending anything to the crew member: the API creates
     * the session (which always carries the alert as context) and closes the alert as handled by
     * Hermes. [TakeToChat.draft] is only ever a picked action's one-line prompt — null here. */
    suspend fun takeToChat(id: String, suggestedActionIndex: Int?, crew: String? = null): TakeToChat =
        api.takeAlertToChat(id, AlertTakeToChatRequest(suggestedActionIndex, crew)).let {
            TakeToChat(it.alert.toDomain(), it.sessionId, it.draft)
        }

    suspend fun delegate(
        id: String,
        instruction: String?,
        suggestedActionIndex: Int?,
        crew: String? = null,
    ): Alert = api.delegateAlert(id, AlertDelegateRequest(instruction, suggestedActionIndex, crew)).toDomain()
}

/** What taking an alert to chat returns: the (now handled) alert, the chat to open, and a draft to pre-fill. */
data class TakeToChat(val alert: Alert, val sessionId: String, val draft: String?)

/** api-contract.md §6: a 409 means the alert moved underneath you — refetch and re-render, don't retry the action. */
fun Throwable.isStaleTransition(): Boolean = this is HttpException && code() == 409

/** api-contract.md §10: a 400 (`CrewNotConfigured`) on /delegate means this account has no crew
 * member that resolves — no host, no crew, or no default. Crew is managed in-app now (Settings),
 * not the old web profile page. */
fun Throwable.delegateErrorMessage(): String =
    if (this is HttpException && code() == 400) {
        "Set up a crew member in Settings first, then try delegating again."
    } else {
        friendlyMessage()
    }
