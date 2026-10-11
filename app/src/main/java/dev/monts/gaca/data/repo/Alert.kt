package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertDto
import dev.monts.gaca.data.remote.MessageDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** severity "drives presentation only... three values, stable, safe to switch on" — api-contract.md §2. */
enum class Severity { INFO, WARN, URGENT }

data class SuggestedAction(val label: String, val prompt: String)

/**
 * The app's own shape for an alert — [AlertDto] mapped into something the UI
 * can render directly, per api-contract.md §2's three client rules: render
 * unknown `kind`s generically, switch freely on `severity`, and treat
 * `suggested_actions` as the primary interaction when present.
 */
data class Alert(
    val id: String,
    val title: String,
    val body: String?,
    val kind: String,
    val source: String,
    /** Which crew member posted this, if the poster said — advisory, not a
     * guarantee it matches a currently configured crew member's name. */
    val crew: String?,
    val severity: Severity,
    val createdAt: String,
    /** Filter and display on this, never `status` — api-contract.md §3. */
    val effectiveStatus: String,
    val snoozeUntil: String?,
    val sessionId: String?,
    val resolutionNote: String?,
    val suggestedActions: List<SuggestedAction>,
    val dataRows: List<Pair<String, String>>,
    /** Pages worth opening to act on this alert (the calendar week, the source email...). */
    val links: List<AlertLink> = emptyList(),
    /** An HTML page (artifact) made for this alert; the detail screen offers to open it. */
    val artifactId: String? = null,
)

/** A web page that goes with an alert. [label] is what to show; the API always provides one. */
data class AlertLink(val url: String, val label: String)

fun AlertDto.toDomain(): Alert = Alert(
    id = id,
    title = title,
    body = body,
    kind = kind,
    source = source,
    crew = crew,
    severity = when (severity) {
        "urgent" -> Severity.URGENT
        "warn" -> Severity.WARN
        else -> Severity.INFO
    },
    createdAt = createdAt,
    effectiveStatus = effectiveStatus,
    snoozeUntil = snoozeUntil,
    sessionId = sessionId,
    resolutionNote = resolutionNote,
    suggestedActions = suggestedActions.map { SuggestedAction(it.label, it.prompt) },
    dataRows = data.entries.map { (key, value) -> key to renderJsonValue(value) },
    // Only web addresses ever become tappable, whatever the server says.
    links = links
        .filter { it.url.startsWith("https://", ignoreCase = true) || it.url.startsWith("http://", ignoreCase = true) }
        .map { AlertLink(it.url, it.label ?: it.url) },
    artifactId = artifactId,
)

/** One turn in a delegated alert's session transcript — the "back and forth" with the crew member. */
data class ConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
    /** Photos on this message, as the server stored them. */
    val attachments: List<MessageAttachment> = emptyList(),
    /** Thumbnails for a message just sent from this device, shown until the server's copy loads. */
    val localPreviews: List<android.graphics.Bitmap> = emptyList(),
    /** What to render (artifact links already removed by the API); falls back to [content]. */
    val displayContent: String = content,
    val artifacts: List<MessageArtifact> = emptyList(),
    val activity: MessageActivity? = null,
    /** True for a message just sent from this device that the server hasn't echoed back yet. */
    val pending: Boolean = false,
)

data class MessageArtifact(val id: String, val title: String, val description: String?, val missing: Boolean)

data class MessageActivity(val label: String, val tools: List<ActivityTool>)
data class ActivityTool(val name: String, val count: Int, val errors: Int)

/** A photo on a message; [url] is relative to the API base and must be fetched with auth. */
data class MessageAttachment(val id: String, val url: String)

fun MessageDto.toDomain(): ConversationMessage = ConversationMessage(
    id, role, content, createdAt,
    attachments = attachments.map { MessageAttachment(it.id, it.url) },
    displayContent = displayContent ?: content,
    artifacts = artifacts.map { MessageArtifact(it.id, it.title, it.description, it.missing) },
    activity = activity?.let { a -> MessageActivity(a.label, a.tools.map { ActivityTool(it.name, it.count, it.errors) }) },
)

/** `data` is arbitrary JSON from whatever posted the alert — render it, never assume its shape. */
private fun renderJsonValue(element: kotlinx.serialization.json.JsonElement): String = when (element) {
    is JsonPrimitive -> if (element.isString) element.content else element.toString()
    is JsonObject -> element.entries.joinToString(", ") { (k, v) -> "$k: ${renderJsonValue(v)}" }
    is JsonArray -> element.joinToString(", ") { renderJsonValue(it) }
    else -> element.toString()
}
