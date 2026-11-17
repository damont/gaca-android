package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.ActionResultDto
import dev.monts.gaca.data.remote.AlertActionDto
import dev.monts.gaca.data.remote.AlertDraftDto
import dev.monts.gaca.data.remote.AlertDto
import dev.monts.gaca.data.remote.AlertWorkflowDto
import dev.monts.gaca.data.remote.MessageDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** severity "drives presentation only... three values, stable, safe to switch on" — api-contract.md §2. */
enum class Severity { INFO, WARN, URGENT }

/** An email draft: show [to], [subject] and [body]; "Open in mail" opens [mailto], "Copy" copies [copyText]. */
data class Draft(val to: String?, val subject: String?, val body: String, val copyText: String, val mailto: String)

/** What an action did once it ran: the agent's short answer, links it gave, or a draft it wrote. */
data class ActionResult(val summary: String?, val links: List<AlertLink>, val draft: Draft?, val error: String?)

/** One thing to tap. Every field is the API's decision (see AlertWorkflow); the app shows it and posts the tap. */
data class AlertAction(
    val index: Int,
    val kind: String?,
    val label: String,
    val detail: String?,
    val cta: String,
    val prominent: Boolean,
    val note: String?,
    /** idle / running / done / failed */
    val state: String,
    val canRun: Boolean,
    val result: ActionResult?,
)

data class CloseOption(val reason: String, val label: String, val primary: Boolean)

/** How the alert's screen lays out, decided by the API. */
data class Workflow(
    /** open / working / waiting_on_you / closed */
    val phase: String,
    val phaseLabel: String?,
    val actionsHeading: String?,
    val progressLabel: String?,
    val chatHint: String,
    val chatProminent: Boolean,
    val closeHeading: String?,
    val closeOptions: List<CloseOption>,
    val closeHint: String?,
    val closedNote: String?,
)

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
    /** `new` while open; otherwise how it ended (done_by_gaca / done_by_me / noted / not_useful). */
    val status: String,
    /** When [status] last changed — the time to show next to "Done by Hermes". */
    val statusChangedAt: String,
    val sessionId: String?,
    val resolutionNote: String?,
    val actions: List<AlertAction>,
    val workflow: Workflow,
    val draft: Draft?,
    val dataRows: List<Pair<String, String>>,
    /** Pages worth opening to act on this alert (the calendar week, the source email...). */
    val links: List<AlertLink> = emptyList(),
    /** An HTML page (artifact) made for this alert; the detail screen offers to open it. */
    val artifactId: String? = null,
    /** The crew member it names was removed; the alert is history now. */
    val crewRemoved: Boolean = false,
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
    status = status,
    statusChangedAt = statusChangedAt,
    sessionId = sessionId,
    resolutionNote = resolutionNote,
    actions = actions.map { it.toDomain() },
    workflow = workflow.toDomain(),
    draft = draft?.toDomain(),
    dataRows = data.entries.map { (key, value) -> key to renderJsonValue(value) },
    // Only web addresses ever become tappable, whatever the server says.
    links = webLinks(links),
    artifactId = artifactId,
    crewRemoved = crewRemoved,
)

fun AlertDraftDto.toDomain() = Draft(to, subject, body, copyText, mailto)

private fun webLinks(links: List<dev.monts.gaca.data.remote.AlertLinkDto>) = links
    .filter { it.url.startsWith("https://", ignoreCase = true) || it.url.startsWith("http://", ignoreCase = true) }
    .map { AlertLink(it.url, it.label ?: it.url) }

private fun ActionResultDto.toDomain() = ActionResult(summary, webLinks(links), draft?.toDomain(), error)

private fun AlertActionDto.toDomain() = AlertAction(index, kind, label, detail, cta, prominent, note, state, canRun, result?.toDomain())

private fun AlertWorkflowDto.toDomain() = Workflow(
    phase = phase, phaseLabel = phaseLabel, actionsHeading = actionsHeading, progressLabel = progressLabel,
    chatHint = chat.hint, chatProminent = chat.prominent, closeHeading = closeHeading,
    closeOptions = closeOptions.map { CloseOption(it.reason, it.label, it.primary) },
    closeHint = closeHint, closedNote = closedNote,
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
    /** An email draft the agent wrote in this reply; [displayContent] is then only the words before it. */
    val draft: Draft? = null,
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
    draft = draft?.toDomain(),
)

/** `data` is arbitrary JSON from whatever posted the alert — render it, never assume its shape. */
private fun renderJsonValue(element: kotlinx.serialization.json.JsonElement): String = when (element) {
    is JsonPrimitive -> if (element.isString) element.content else element.toString()
    is JsonObject -> element.entries.joinToString(", ") { (k, v) -> "$k: ${renderJsonValue(v)}" }
    is JsonArray -> element.joinToString(", ") { renderJsonValue(it) }
    else -> element.toString()
}
