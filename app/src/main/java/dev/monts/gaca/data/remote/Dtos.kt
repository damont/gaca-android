package dev.monts.gaca.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Mirrors the gaca-api wire format. The server, not this file, is the source of truth:
// if the two disagree, fetch <base>/api/openapi.json and trust that.

@Serializable
data class SuggestedActionDto(
    val label: String,
    val prompt: String,
)

/** An email draft an agent wrote. The API built [mailto] and [copyText]; the app only opens or copies them. */
@Serializable
data class AlertDraftDto(
    val to: String? = null,
    val subject: String? = null,
    val body: String,
    @SerialName("copy_text") val copyText: String = body,
    val mailto: String = "",
)

@Serializable
data class ActionResultDto(
    val summary: String? = null,
    val links: List<AlertLinkDto> = emptyList(),
    val draft: AlertDraftDto? = null,
    val error: String? = null,
)

/** One tappable action and where it stands — `state` is idle / running / done / failed. The API decides
 * all of it (label now, whether it can be tapped, the result); the app shows it. */
@Serializable
data class AlertActionDto(
    val index: Int,
    val kind: String? = null,
    val label: String,
    val detail: String? = null,
    /** The word on the button: "Add", "Draft", "Do it". */
    val cta: String = "Do it",
    /** An "Add all"-style action: shown as the one big button, not as a row. */
    val prominent: Boolean = false,
    /** Where it stands while running ("Hermes is on it"). */
    val note: String? = null,
    val state: String = "idle",
    @SerialName("can_run") val canRun: Boolean = false,
    val result: ActionResultDto? = null,
)

@Serializable
data class CloseOptionDto(
    val reason: String,
    val label: String,
    val primary: Boolean = false,
)

@Serializable
data class ChatEntryDto(
    val hint: String = "",
    val prominent: Boolean = false,
)

/** The layout decisions for an alert's screen, made by the API so every app behaves the same. */
@Serializable
data class AlertWorkflowDto(
    /** open / working / waiting_on_you / closed */
    val phase: String = "open",
    @SerialName("phase_label") val phaseLabel: String? = null,
    @SerialName("actions_heading") val actionsHeading: String? = null,
    @SerialName("progress_label") val progressLabel: String? = null,
    val chat: ChatEntryDto = ChatEntryDto(),
    @SerialName("close_heading") val closeHeading: String? = null,
    @SerialName("close_options") val closeOptions: List<CloseOptionDto> = emptyList(),
    @SerialName("close_hint") val closeHint: String? = null,
    @SerialName("closed_note") val closedNote: String? = null,
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
    /** Web pages that go with this alert; the API always supplies a label. */
    val links: List<AlertLinkDto> = emptyList(),
    val status: String,
    @SerialName("effective_status") val effectiveStatus: String,
    @SerialName("status_changed_at") val statusChangedAt: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("resolution_note") val resolutionNote: String? = null,
    /** The actions with their state and results, and how the screen lays out — see [AlertWorkflowDto]. */
    val actions: List<AlertActionDto> = emptyList(),
    val workflow: AlertWorkflowDto = AlertWorkflowDto(),
    /** The latest email draft, if an agent wrote one. */
    val draft: AlertDraftDto? = null,
    @SerialName("parent_alert_id") val parentAlertId: String? = null,
    /** An HTML page (artifact) published for this alert. */
    @SerialName("artifact_id") val artifactId: String? = null,
    /** True when the crew member it names was removed; it is read-only history now. Absent on older servers. */
    @SerialName("crew_removed") val crewRemoved: Boolean = false,
)

@Serializable
data class AlertLinkDto(
    val url: String,
    val label: String? = null,
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

/** POST /alerts/{id}/close — `reason` is one the API offered in `workflow.close_options`. */
@Serializable
data class AlertCloseRequest(
    val reason: String,
    val note: String? = null,
)

@Serializable
data class AlertDelegateRequest(
    val instruction: String? = null,
    @SerialName("suggested_action_index") val suggestedActionIndex: Int? = null,
    val crew: String? = null,
)

/** POST /alerts/{id}/take-to-chat — opens the alert's chat; nothing is sent (the chat carries the alert itself). */
@Serializable
data class AlertTakeToChatRequest(
    @SerialName("suggested_action_index") val suggestedActionIndex: Int? = null,
    val crew: String? = null,
)

@Serializable
data class TakeToChatDto(
    val alert: AlertDto,
    @SerialName("session_id") val sessionId: String,
    val draft: String? = null,
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
    /** Give both to register a profile already set up by hand; omit both and gaca-api
     * provisions a brand new one on the box directly (it picks the port, generates the
     * key) — needs SSH access to the box, granted out of band and remembered server-side,
     * so the app never collects or sends it. */
    val port: Int? = null,
    @SerialName("api_key") val apiKey: String? = null,
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
    /** Whether gaca-api already has SSH access to the box, i.e. whether adding a
     * crew member can skip straight to a bare name instead of needing `access` too. */
    @SerialName("can_create") val canCreate: Boolean = false,
)

/** GET /agent — is this account connected to an agent yet, and where. A real,
 * dedicated record (not inferred from the crew list) — see api-contract.md §2. */
@Serializable
data class AgentStatusDto(
    val connected: Boolean,
    val host: String? = null,
)

/** A single turn in a session's transcript — GET /sessions/{id}. */
@Serializable
data class MessageDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    val attachments: List<AttachmentDto> = emptyList(),
    /** What to render — `content` minus artifact links, computed by the API (null on older servers). */
    @SerialName("display_content") val displayContent: String? = null,
    /** Pages linked from this message, resolved by the API. */
    val artifacts: List<MessageArtifactDto> = emptyList(),
    /** What the agent did to produce this reply; null when it used no tools. */
    val activity: ActivityDto? = null,
    /** An email draft the agent wrote in this reply; when set, `display_content` is only the words before it. */
    val draft: AlertDraftDto? = null,
)

@Serializable
data class MessageArtifactDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val crew: String? = null,
    val missing: Boolean = false,
)

@Serializable
data class ActivityDto(
    val tools: List<ActivityToolDto> = emptyList(),
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    val label: String = "",
)

@Serializable
data class ActivityToolDto(val name: String, val count: Int = 1, val errors: Int = 0)

/** A photo on a message. [url] is relative to the API base and needs the bearer token. */
@Serializable
data class AttachmentDto(
    val id: String,
    @SerialName("content_type") val contentType: String,
    val size: Long = 0,
    val url: String = "",
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
    /** Headline for the Chat list, computed by the API (title, else the opening message). */
    val summary: String? = null,
    /** One-line preview of the latest message, computed by the API. */
    val preview: String? = null,
    /** Latest message's time, else creation time — what the list is sorted and labelled by. */
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    /** True when [crew] was removed: the chat is read-only history. Absent on older servers. */
    @SerialName("crew_removed") val crewRemoved: Boolean = false,
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
    val summary: String? = null,
    val preview: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("crew_removed") val crewRemoved: Boolean = false,
    val messages: List<MessageDto> = emptyList(),
    /** A run for this session is in flight right now. */
    val running: Boolean = false,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    /** The Last-Event-ID to open the event stream with. */
    @SerialName("resume_seq") val resumeSeq: Long = 0,
)

/** Body for POST /sessions/{id}/messages. */
@Serializable
data class MessageCreateRequest(
    val content: String,
    /** Ids from POST /sessions/{id}/attachments; text is optional when there's at least one. */
    @SerialName("attachment_ids") val attachmentIds: List<String> = emptyList(),
)

/** 202 body — the message was stored; the reply streams in on GET /sessions/{id}/events. */
@Serializable
data class MessageAcceptedDto(
    @SerialName("turn_id") val turnId: String,
)

/** GET /crew/{name}/skills — read-only, live from that crew member's own Hermes, never
 * cached (api-contract.md §8). `category` may be null for an uncategorized skill; `use_count`
 * is 0 when the engine keeps no count. */
@Serializable
data class SkillDto(
    val name: String,
    val description: String,
    val category: String? = null,
    @SerialName("use_count") val useCount: Int = 0,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

/** One past run in an automation's row of coloured squares; `status` is ok / failed / running. */
@Serializable
data class RunMarkDto(
    val id: String? = null,
    val status: String,
    val at: String? = null,
)

/** GET /automations — what a crew member does on a schedule. `status` is one of ok, failed,
 * running, paused, done, never_run; `recent` is at most 10 runs, oldest first. */
@Serializable
data class AutomationDto(
    val id: String,
    val name: String,
    val crew: String = "",
    val schedule: String,
    @SerialName("cron_expr") val cronExpr: String? = null,
    val status: String,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_run") val lastRun: String? = null,
    @SerialName("next_run") val nextRun: String? = null,
    val kind: String = "agent",
    val recent: List<RunMarkDto> = emptyList(),
)

@Serializable
data class AutomationListDto(
    val automations: List<AutomationDto> = emptyList(),
    /** Crew members whose automations couldn't be read — say so rather than show an empty list as true. */
    val unavailable: List<String> = emptyList(),
)

/** GET /crew/{name}/automations/{id}/runs, newest first. */
@Serializable
data class AutomationRunDto(
    val id: String,
    val status: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val summary: String? = null,
    val error: String? = null,
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


/** One row in a briefing section. `time` is free
 * text the poster formatted; show it as-is. */
@Serializable
data class BriefingItemDto(
    val title: String,
    val body: String? = null,
    val time: String? = null,
    val location: String? = null,
    val link: String? = null,
    val priority: String = "medium",
)

@Serializable
data class BriefingSectionDto(
    val key: String,
    val title: String,
    val kind: String = "general",
    val headline: String? = null,
    val items: List<BriefingItemDto> = emptyList(),
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** GET /briefings?date= — sections already in display order; empty (not an error) if
 * nothing was posted for that day. */
@Serializable
data class BriefingDto(
    val date: String,
    val summary: String? = null,
    val sections: List<BriefingSectionDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)


/** GET /crew/{name}/profile — GACA's own picture of how a crew member is set up
 * (gaca-api schemas/crew_profile.py). Display only. */
@Serializable
data class CrewProfileDto(
    val name: String,
    val color: String,
    @SerialName("is_default") val isDefault: Boolean = false,
    val status: String = "offline",
    val engine: String? = null,
    val model: ProfileModelDto? = null,
    val channels: List<ProfileChannelDto> = emptyList(),
    @SerialName("skill_count") val skillCount: Int = 0,
    @SerialName("skill_categories") val skillCategories: List<SkillCategoryDto> = emptyList(),
    val toolsets: List<ProfileToolsetDto> = emptyList(),
    val jobs: List<ProfileJobDto> = emptyList(),
    /** What this crew member is dedicated to doing; null means it couldn't be read. */
    val purpose: ProfilePurposeDto? = null,
    val unavailable: List<String> = emptyList(),
)

@Serializable
data class ProfilePurposeDto(
    val summary: String? = null,
    @SerialName("summary_source") val summarySource: String? = null,
    val instructions: String? = null,
    @SerialName("is_generic") val isGeneric: Boolean = true,
)

@Serializable
data class ProfileModelDto(val provider: String? = null, val name: String? = null)

@Serializable
data class ProfileChannelDto(
    val name: String,
    val state: String = "disconnected",
    @SerialName("needs_attention") val needsAttention: Boolean = false,
)

@Serializable
data class SkillCategoryDto(val name: String, val count: Int)

@Serializable
data class ProfileToolsetDto(val name: String, val label: String, val description: String = "")

@Serializable
data class ProfileJobDto(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean = true,
    @SerialName("next_run") val nextRun: String? = null,
    @SerialName("last_run") val lastRun: String? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    val kind: String = "agent",
)

// ── Artifacts: HTML pages an agent writes, plus the data they keep ─────────────
// See the artifacts routes in <base>/api/openapi.json.

@Serializable
data class ArtifactDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val crew: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("alert_id") val alertId: String? = null,
    val archived: Boolean = false,
    val version: Int = 1,
    val rev: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
    // Cloud-aware fields (servers before Part 8 omit them: a local page you own).
    /** `local` (on your server) or `cloud`. */
    val home: String = "local",
    /** Your role on it: owner, maintainer, editor or viewer. */
    val role: String = "owner",
    /** The owner's handle when it is not you. */
    @SerialName("shared_by") val sharedBy: String? = null,
    @SerialName("member_count") val memberCount: Int = 1,
    /** True while it is being moved to the cloud (read-only until that ends). */
    val moving: Boolean = false,
    @SerialName("cloud_status") val cloudStatus: String? = null,
    /** Set once this page has been published to the gallery. */
    @SerialName("published_template_id") val publishedTemplateId: String? = null,
)

@Serializable
data class ArtifactOrderRequest(val ids: List<String>)

@Serializable
data class ArtifactListDto(
    val artifacts: List<ArtifactDto> = emptyList(),
    /** True when the cloud could not be asked and the cloud rows are from the server's last look. */
    val stale: Boolean = false,
)

// ── Gallery: public templates in the cloud (read through the server) ───────────

@Serializable
data class AccountRefDto(val id: String = "", val handle: String = "", @SerialName("display_name") val displayName: String? = null)

@Serializable
data class TemplateDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val author: AccountRefDto? = null,
    val status: String = "listed",
    val downloads: Int = 0,
)

@Serializable
data class TemplateListDto(
    val templates: List<TemplateDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class ArtifactPatchRequest(
    val archived: Boolean? = null,
    val title: String? = null,
)

/** GET/PUT /heartbeat/settings — how the agent's periodic check-in behaves. Hours are 0-23 in
 * the phone's zone (`timezone`); quiet hours run from `quiet_start_hour` to `quiet_end_hour`
 * and may cross midnight. `paused_until` is null when not paused. */
@Serializable
data class HeartbeatSettingsDto(
    val enabled: Boolean = true,
    @SerialName("paused_until") val pausedUntil: String? = null,
    val timezone: String? = null,
    @SerialName("quiet_start_hour") val quietStartHour: Int = 22,
    @SerialName("quiet_end_hour") val quietEndHour: Int = 7,
    @SerialName("max_alerts_per_day") val maxAlertsPerDay: Int = 3,
    @SerialName("min_minutes_between_alerts") val minMinutesBetweenAlerts: Int = 60,
    @SerialName("max_runs_per_day") val maxRunsPerDay: Int = 30,
)

// ── GACA Cloud link: this server's connection to the person's cloud account ────
// The app only ever talks to its own server (<base>/cloud/...); the server holds the cloud token.

/** GET /cloud/link. `state`: none | pending | linked | invalid (the cloud stopped accepting the link). */
@Serializable
data class CloudLinkStatusDto(
    val linked: Boolean = false,
    val state: String = "none",
    val handle: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("linked_at") val linkedAt: String? = null,
    val scope: String? = null,
    /** The owner's switch: may their agents (Hermes) use the cloud? Off until switched on here. */
    @SerialName("agent_access") val agentAccess: Boolean = false,
    /** False when the server has no cloud address at all, so linking cannot work. Older servers omit it (= true). */
    val configured: Boolean = true,
    /** A link in progress (paired app only, and only while the code is valid), so the same code can be shown again. */
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val interval: Int? = null,
)

/** PUT /cloud/agent-access. */
@Serializable
data class CloudAgentAccessRequest(val enabled: Boolean)

/** POST /cloud/link/start. `scope` has no default on purpose: the app's Json leaves defaults out, and we want it sent. */
@Serializable
data class CloudLinkStartRequest(val scope: String)

@Serializable
data class CloudLinkStartedDto(
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

/** POST /cloud/link/poll: pending | linked | expired | denied. */
@Serializable
data class CloudLinkPollDto(
    val status: String,
    val interval: Int? = null,
    @SerialName("retry_in") val retryIn: Int? = null,
    val handle: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    val scope: String? = null,
    @SerialName("linked_at") val linkedAt: String? = null,
)

/** DELETE /cloud/link. */
@Serializable
data class CloudUnlinkedDto(
    val unlinked: Boolean = false,
    @SerialName("cloud_revoked") val cloudRevoked: Boolean = false,
    val message: String = "",
)

// ── Sharing a page: move it to the cloud, check it, make an invite link ────────
// All through the person's own server (<base>/artifacts/{id}/... and <base>/cloud/gacabits/{id}/...).

@Serializable
data class NotCarriedDto(
    @SerialName("versions_dropped") val versionsDropped: Int = 0,
    @SerialName("handled_events_dropped") val handledEventsDropped: Int = 0,
    val crew: String? = null,
    @SerialName("chat_alert_links") val chatAlertLinks: JsonObject? = null,
    @SerialName("doc_versions_reset") val docVersionsReset: Int = 0,
    @SerialName("last_opened") val lastOpened: Boolean = false,
    val anything: Boolean = false,
)

@Serializable
data class MoveSizeDto(val bytes: Long = 0, @SerialName("limit_bytes") val limitBytes: Long = 0)

/** GET /artifacts/{id}/move-preview: what a move would do. Nothing is sent anywhere. */
@Serializable
data class MovePreviewDto(
    @SerialName("can_move") val canMove: Boolean = false,
    @SerialName("blocked_because") val blockedBecause: String? = null,
    val problems: List<JsonElement> = emptyList(),
    val size: MoveSizeDto = MoveSizeDto(),
    @SerialName("not_carried") val notCarried: NotCarriedDto = NotCarriedDto(),
    @SerialName("requires_acknowledgement") val requiresAcknowledgement: Boolean = false,
    @SerialName("backup_days") val backupDays: Int = 30,
)

@Serializable
data class MoveRequestDto(@SerialName("acknowledge_not_carried") val acknowledgeNotCarried: Boolean)

@Serializable
data class MoveResultDto(val artifact: ArtifactDto, val verified: Boolean = false)

/** `subject` has no default so it is always sent. */
@Serializable
data class ValidationRunRequestDto(val subject: String)

@Serializable
data class FindingDto(val rule: String = "", val severity: String = "info", val message: String = "", val location: String? = null)

@Serializable
data class ValidationReportDto(
    val status: String = "pending",
    val findings: List<FindingDto> = emptyList(),
    /** Publish checks only: the hash of exactly what would be published, and that content itself. */
    @SerialName("content_hash") val contentHash: String? = null,
    val preview: JsonObject? = null,
)

/** Check what publishing would put out. `subject` and `title` have no default so they are always sent. */
@Serializable
data class PublishCheckRequestDto(val subject: String, val title: String, val description: String? = null)

/** The same listing text as the check (the hash covers it), plus the person's confirmation of that preview. */
@Serializable
data class PublishRequestDto(
    val title: String,
    val description: String? = null,
    val visibility: String,
    @SerialName("confirmed_preview") val confirmedPreview: Boolean,
    @SerialName("preview_hash") val previewHash: String,
)

@Serializable
data class BringHomeRequestDto(@SerialName("acknowledge_members") val acknowledgeMembers: Boolean)

@Serializable
data class BringHomeResultDto(
    val artifact: ArtifactDto,
    val confirmed: Boolean = true,
    @SerialName("undo_until") val undoUntil: String? = null,
    val warning: String? = null,
    @SerialName("members_lost_access") val membersLostAccess: Int = 0,
)

@Serializable
data class UndoResultDto(val home: String = "local", val undone: Boolean = false)

@Serializable
data class InviteCreateRequestDto(
    val role: String,
    @SerialName("expires_in_days") val expiresInDays: Int,
    @SerialName("max_uses") val maxUses: Int,
)

/** The answer to creating an invite link. `url` is the secret link and comes back only this once. */
@Serializable
data class InviteLinkDto(
    val id: String = "",
    val role: String = "viewer",
    val url: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("max_uses") val maxUses: Int = 1,
)

// ── Joining a page someone shared: preview an invite link, then accept it ──────

@Serializable
data class InviteTokenDto(val token: String)

/** What an invite link would add. The cloud sends no page data here, and no id. */
@Serializable
data class InvitePreviewDto(
    @SerialName("gacabit_title") val gacabitTitle: String = "",
    val role: String = "viewer",
    @SerialName("invited_by") val invitedBy: AccountRefDto? = null,
    @SerialName("already_member") val alreadyMember: Boolean = false,
)

/** The page you were added to (only the bits the app needs; the server answers with a whole list row). */
@Serializable
data class RedeemedDto(val id: String = "", val title: String = "")
