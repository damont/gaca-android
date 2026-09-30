package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.SessionDetailDto
import dev.monts.gaca.data.remote.SessionDto

/** A standing conversation with exactly one crew member, fixed for its whole
 * lifetime — api-contract.md §6. The Chat tab's list is these, without messages. */
data class Session(
    val id: String,
    val createdAt: String,
    val title: String?,
    val status: String,
    /** Which crew member this session talks to. */
    val crew: String?,
    /** Set only if this session was opened by delegating an alert — the back
     * pointer to [Alert.sessionId]'s forward one. `null` for a session started
     * directly from the Chat tab. */
    val alertId: String?,
    /** What the Chat list shows as the chat's headline — computed by the API, display only. */
    val summary: String?,
    /** One-line preview of the latest message — computed by the API, display only. */
    val preview: String?,
)

fun SessionDto.toDomain(): Session = Session(id, createdAt, title, status, crew, alertId, summary, preview)

/** [Session] plus its transcript, oldest first — GET /sessions/{id}. */
data class SessionDetail(
    val session: Session,
    val messages: List<ConversationMessage>,
)

fun SessionDetailDto.toDomain(): SessionDetail = SessionDetail(
    session = Session(id, createdAt, title, status, crew, alertId, summary, preview),
    messages = messages.map { it.toDomain() },
)
