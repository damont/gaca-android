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
    val agent: String? = null,
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

/** Body for POST /alerts — normally an agent posts here, but nothing about the
 * endpoint requires that; a plain session token works too, which is what lets
 * the app raise its own alert to immediately delegate. */
@Serializable
data class AlertCreateRequest(
    val kind: String,
    val title: String,
    val severity: String = "info",
    val body: String? = null,
    val source: String = "gaca-android",
    val agent: String? = null,
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
    val agent: String? = null,
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

/** Body for PUT /auth/agent-host — the scheme+host shared by every agent below. */
@Serializable
data class AgentHostUpdateRequest(
    val host: String,
)

/** Body for POST /auth/agents. */
@Serializable
data class AgentCreateRequest(
    val name: String,
    val port: Int,
    @SerialName("api_key") val apiKey: String,
)

/** Body for PUT /auth/agents/{name} — both fields required together, no partial update. */
@Serializable
data class AgentUpdateRequest(
    val port: Int,
    @SerialName("api_key") val apiKey: String,
)

/** Never carries the key back — same rule as a password. */
@Serializable
data class AgentDto(
    val name: String,
    val port: Int,
    @SerialName("is_default") val isDefault: Boolean,
)

/** GET /auth/agents and every mutating agent endpoint return this — the full picture. */
@Serializable
data class AgentsDto(
    val host: String? = null,
    val agents: List<AgentDto> = emptyList(),
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

/** GET /sessions/{id} — a session plus every message in it, oldest first. */
@Serializable
data class SessionDetailDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val title: String? = null,
    val status: String,
    val messages: List<MessageDto> = emptyList(),
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
