package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertDelegateRequest
import dev.monts.gaca.data.remote.AlertResolveRequest
import dev.monts.gaca.data.remote.AlertSnoozeRequest
import dev.monts.gaca.data.remote.GacaApi
import retrofit2.HttpException

/**
 * api-contract.md §5: plain polling, no stream. `GET /alerts` on
 * app-foreground plus pull-to-refresh is the whole story — no local
 * derivation of expired snoozes or legal transitions; the server owns that
 * (§7: "the app does not compute state").
 */
class AlertsRepository(private val api: GacaApi) {

    suspend fun listAlerts(status: String, limit: Int = 50): List<Alert> =
        api.listAlerts(statuses = listOf(status), limit = limit).alerts.map { it.toDomain() }

    suspend fun getAlert(id: String): Alert = api.getAlert(id).toDomain()

    suspend fun act(id: String, note: String?): Alert =
        api.actOnAlert(id, AlertResolveRequest(note)).toDomain()

    suspend fun dismiss(id: String, note: String?): Alert =
        api.dismissAlert(id, AlertResolveRequest(note)).toDomain()

    suspend fun snooze(id: String, untilIso: String): Alert =
        api.snoozeAlert(id, AlertSnoozeRequest(untilIso)).toDomain()

    suspend fun delegate(id: String, instruction: String?, suggestedActionIndex: Int?): Alert =
        api.delegateAlert(id, AlertDelegateRequest(instruction, suggestedActionIndex)).toDomain()
}

/** api-contract.md §6: a 409 means the alert moved underneath you — refetch and re-render, don't retry the action. */
fun Throwable.isStaleTransition(): Boolean = this is HttpException && code() == 409
