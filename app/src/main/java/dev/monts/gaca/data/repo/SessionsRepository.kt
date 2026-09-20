package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.MessageCreateRequest
import dev.monts.gaca.data.remote.SessionCreateRequest
import dev.monts.gaca.data.remote.SseClient
import dev.monts.gaca.data.remote.SseEvent
import kotlinx.coroutines.flow.Flow

/**
 * Chat sessions — api-contract.md §6. Follows the same shape as
 * [AlertsRepository]: a `Dto` at the network edge, a plain domain class
 * everywhere else. The live event stream is the one exception to plain
 * request/response, handled by [SseClient] rather than Retrofit.
 */
class SessionsRepository(
    private val api: GacaApi,
    private val sseClient: SseClient,
) {

    /** The Chat tab's list, newest first — no messages, no filtering yet (api-contract.md §6). */
    suspend fun listSessions(): List<Session> = api.listSessions().sessions.map { it.toDomain() }

    /** `crew` names which crew member this chat talks to; omit it for the account default. */
    suspend fun createSession(title: String? = null, crew: String? = null): Session =
        api.createSession(SessionCreateRequest(title, crew)).toDomain()

    suspend fun getSession(id: String): SessionDetail = api.getSession(id).toDomain()

    /** `202`s immediately with a turn id — this does not wait for a reply; the reply
     * streams in on [events] and lands in the transcript when the run completes. */
    suspend fun sendMessage(sessionId: String, content: String): String =
        api.postMessage(sessionId, MessageCreateRequest(content)).turnId

    /** `assistant.delta` / `tool.started` / `run.completed` / `run.failed`, in order,
     * reconnecting on its own if the connection drops. */
    fun events(sessionId: String): Flow<SseEvent> = sseClient.stream("sessions/$sessionId/events")
}
