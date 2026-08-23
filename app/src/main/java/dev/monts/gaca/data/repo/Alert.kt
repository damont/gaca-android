package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.AlertDto
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
    val severity: Severity,
    val createdAt: String,
    /** Filter and display on this, never `status` — api-contract.md §3. */
    val effectiveStatus: String,
    val snoozeUntil: String?,
    val sessionId: String?,
    val resolutionNote: String?,
    val suggestedActions: List<SuggestedAction>,
    val dataRows: List<Pair<String, String>>,
)

fun AlertDto.toDomain(): Alert = Alert(
    id = id,
    title = title,
    body = body,
    kind = kind,
    source = source,
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
)

/** `data` is arbitrary JSON from whatever posted the alert — render it, never assume its shape. */
private fun renderJsonValue(element: kotlinx.serialization.json.JsonElement): String = when (element) {
    is JsonPrimitive -> if (element.isString) element.content else element.toString()
    is JsonObject -> element.entries.joinToString(", ") { (k, v) -> "$k: ${renderJsonValue(v)}" }
    is JsonArray -> element.joinToString(", ") { renderJsonValue(it) }
    else -> element.toString()
}
