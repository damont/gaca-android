package dev.monts.gaca.data.repo

import dev.monts.gaca.data.remote.ArtifactDto
import dev.monts.gaca.data.remote.ArtifactOrderRequest
import dev.monts.gaca.data.remote.ArtifactPatchRequest
import dev.monts.gaca.data.remote.GacaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** An HTML page an agent wrote, plus the data it keeps (which lives in the API, not the page). */
data class Artifact(
    val id: String,
    val title: String,
    val description: String?,
    val crew: String?,
    val sessionId: String?,
    val alertId: String?,
    val archived: Boolean,
    val version: Int,
    val updatedAt: String,
    val lastOpenedAt: String?,
)

fun ArtifactDto.toDomain() = Artifact(
    id = id, title = title, description = description, crew = crew, sessionId = sessionId,
    alertId = alertId, archived = archived, version = version, updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
)

/** The answer to a raw API call made on behalf of a page: HTTP status plus the JSON text. */
class RawResponse(val status: Int, val body: String)

/** The in-app address of an artifact, as an agent writes it in a chat message. */
private val ARTIFACT_LINK = Regex(
    """\[([^\]\n]{1,120})]\(gaca://artifact/([0-9a-f]{32})\)|gaca://artifact/([0-9a-f]{32})""",
)

/** One artifact link found in message text. */
data class ArtifactRef(val id: String, val label: String?)

/** Pulls the artifact links out of [text]; [display] is the text with them removed (or replaced
 * by their label) so the bubble doesn't show a raw `gaca://` address. */
fun extractArtifactRefs(text: String): Pair<String, List<ArtifactRef>> {
    val refs = mutableListOf<ArtifactRef>()
    val display = ARTIFACT_LINK.replace(text) { match ->
        // Either `[label](gaca://artifact/id)` (groups 1-2) or a bare address (group 3).
        val ref = ArtifactRef(
            match.groupValues[2].ifEmpty { match.groupValues[3] },
            match.groupValues[1].ifBlank { null },
        )
        if (refs.none { it.id == ref.id }) refs += ref
        ""
    }
    return display.replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim() to refs
}

class ArtifactRepository(
    private val api: GacaApi,
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun list(includeArchived: Boolean = false): List<Artifact> =
        api.listArtifacts(includeArchived).artifacts.map { it.toDomain() }.also { all -> all.forEach { titles[it.id] = it.title } }

    suspend fun get(id: String): Artifact = api.getArtifact(id).toDomain().also { titles[it.id] = it.title }

    /** A page's title for a chat chip; null when it can't be loaded (deleted, offline). Cached. */
    suspend fun title(id: String): String? =
        titles[id] ?: runCatching { get(id).title }.getOrNull()

    suspend fun setArchived(id: String, archived: Boolean) {
        api.patchArtifact(id, ArtifactPatchRequest(archived = archived))
    }

    suspend fun delete(id: String) = api.deleteArtifact(id)

    /** Saves the order the owner dragged the list into (the API keeps it per account). */
    suspend fun reorder(ids: List<String>) {
        api.reorderArtifacts(ArtifactOrderRequest(ids))
    }

    suspend fun markOpened(id: String) {
        runCatching { api.markArtifactOpened(id) }
    }

    /** The page ready to show: the API wraps the agent's HTML with the network-blocking policy, the theme, the base
     * style and the `window.app` SDK, so every client (this one, iPhone, web) shows the same thing. Fetched with the
     * user's token; the page itself never sees it. */
    suspend fun page(id: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("${baseUrl}artifacts/$id/page").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("page ${response.code}")
            response.body?.string() ?: throw IOException("empty page")
        }
    }

    /** One call to the API for the page bridge. [path] is relative to the API base. */
    suspend fun raw(method: String, path: String, json: String?): RawResponse = withContext(Dispatchers.IO) {
        val body = if (method == "GET" || method == "DELETE") null else (json ?: "{}").toRequestBody(JSON)
        val request = Request.Builder().url(baseUrl + path).method(method, body).build()
        client.newCall(request).execute().use { response ->
            RawResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
