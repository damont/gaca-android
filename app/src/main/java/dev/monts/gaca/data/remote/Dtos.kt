package dev.monts.gaca.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Mirrors gaca-planning/alerts/api-contract.md exactly. That doc — not this
// file — is the source of truth; if the two disagree, fetch <base>/api/openapi.json
// and trust that over both.

@Serializable
data class SuggestedActionDto(
    val label: String,
    val prompt: String,
)

@Serializable
data class AlertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    val source: String,
    val crew: String? = null,
    val kind: String,
    val severity: String,
    val title: String,
    val body: String? = null,
    val data: Map<String, JsonElement> = emptyMap(),
    @SerialName("dedupe_key") val dedupeKey: String? = null,
    @SerialName("suggested_actions") val suggestedActions: List<SuggestedActionDto> = emptyList(),
    val status: String,
    @SerialName("effective_status") val effectiveStatus: String,
    @SerialName("status_changed_at") val statusChangedAt: String,
    @SerialName("snooze_until") val snoozeUntil: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("resolution_note") val resolutionNote: String? = null,
    @SerialName("parent_alert_id") val parentAlertId: String? = null,
)

@Serializable
data class AlertListDto(
    val alerts: List<AlertDto> = emptyList(),
)

/** Body for POST /alerts — normally a crew member's Hermes posts here, but nothing about
 * the endpoint requires that; a plain session token works too, which is what lets
 * the app raise its own alert to immediately delegate. */
@Serializable
data class AlertCreateRequest(
    val kind: String,
    val title: String,
    val severity: String = "info",
    val body: String? = null,
    val source: String = "gaca-android",
    val crew: String? = null,
)

@Serializable
data class AlertResolveRequest(
    val note: String? = null,
)

@Serializable
data class AlertSnoozeRequest(
    val until: String,
)

@Serializable
data class AlertDelegateRequest(
    val instruction: String? = null,
    @SerialName("suggested_action_index") val suggestedActionIndex: Int? = null,
    val crew: String? = null,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class UserLoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class GoogleLoginRequest(
    @SerialName("id_token") val idToken: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
)

/** Body for POST /crew. `color` is optional — omit it to let the server auto-assign
 * one from its fixed palette (first swatch not already in use). */
@Serializable
data class CrewMemberCreateRequest(
    val name: String,
    val port: Int,
    @SerialName("api_key") val apiKey: String,
    val color: String? = null,
)

/** Body for PUT /crew/{name} — both fields required together, no partial update. */
@Serializable
data class CrewMemberUpdateRequest(
    val port: Int,
    @SerialName("api_key") val apiKey: String,
)

/** Never carries the key back — same rule as a password. `color` is a hex string,
 * auto-assigned server-side from a fixed palette unless the caller supplies one —
 * render it as-is, the server is the one source of truth for it. */
@Serializable
data class CrewMemberDto(
    val name: String,
    val port: Int,
    val color: String,
    @SerialName("is_default") val isDefault: Boolean,
)

/** GET /crew and every mutating crew endpoint return this — the full picture. */
@Serializable
data class CrewDto(
    val host: String? = null,
    val crew: List<CrewMemberDto> = emptyList(),
)

/** A single turn in a session's transcript — GET /sessions/{id}. */
@Serializable
data class MessageDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)

/** A session summary — GET /sessions (the Chat tab's list) and the response of
 * POST /sessions. No `messages` field at all on this shape; fetch [SessionDetailDto]
 * (GET /sessions/{id}) for the transcript. */
@Serializable
data class SessionDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val title: String? = null,
    val status: String,
    val crew: String? = null,
    @SerialName("alert_id") val alertId: String? = null,
)

/** GET /sessions. */
@Serializable
data class SessionListDto(
    val sessions: List<SessionDto> = emptyList(),
)

/** Body for POST /sessions — both fields optional; omitting `crew` uses the account default. */
@Serializable
data class SessionCreateRequest(
    val title: String? = null,
    val crew: String? = null,
)

/** GET /sessions/{id} — a session plus every message in it, oldest first. */
@Serializable
data class SessionDetailDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val title: String? = null,
    val status: String,
    val crew: String? = null,
    @SerialName("alert_id") val alertId: String? = null,
    val messages: List<MessageDto> = emptyList(),
)

/** Body for POST /sessions/{id}/messages. */
@Serializable
data class MessageCreateRequest(
    val content: String,
)

/** 202 body — the message was stored; the reply streams in on GET /sessions/{id}/events. */
@Serializable
data class MessageAcceptedDto(
    @SerialName("turn_id") val turnId: String,
)

/** GET /crew/{name}/skills — read-only, live from that crew member's own Hermes, never
 * cached (api-contract.md §8). `category` may be null for an uncategorized skill. */
@Serializable
data class SkillDto(
    val name: String,
    val description: String,
    val category: String? = null,
)

@Serializable
data class PushTokenRegisterRequest(
    val token: String,
    val platform: String = "android",
)

@Serializable
data class PushTokenUnregisterRequest(
    val token: String,
)
