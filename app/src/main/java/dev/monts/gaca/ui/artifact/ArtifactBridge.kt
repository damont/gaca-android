package dev.monts.gaca.ui.artifact

import android.net.Uri
import android.webkit.JavascriptInterface
import dev.monts.gaca.data.repo.ArtifactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * What an artifact page can ask the app to do (`window.app.*` in assets/artifact-sdk.js calls
 * [call]). It is deliberately narrow: the page is model-written and untrusted, so it can only
 * read/write ITS OWN artifact's data, plus a few UI actions. Every API path is built here from
 * validated pieces — never from page-supplied text — and the user's token never reaches the page.
 *
 * Methods arrive on a binder thread; each runs on [scope] and answers through [reply]
 * (which the screen wires to `window.__gaca.resolve(...)` on the WebView).
 */
class ArtifactBridge(
    private val artifactId: String,
    private val repo: ArtifactRepository,
    private val scope: CoroutineScope,
    private val reply: (callId: String, ok: Boolean, json: String) -> Unit,
    private val onAsk: (String) -> Unit,
    private val onOpenUrl: (String) -> Unit,
    private val onToast: (String) -> Unit,
) {
    @JavascriptInterface
    fun call(callId: String, method: String, argsJson: String) {
        scope.launch {
            try {
                val args = JSONObject(argsJson)
                reply(callId, true, dispatch(method, args))
            } catch (e: BridgeError) {
                reply(callId, false, JSONObject().put("status", e.status).put("message", e.message).toString())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reply(callId, false, JSONObject().put("status", 0).put("message", e.message ?: "request failed").toString())
            }
        }
    }

    private class BridgeError(val status: Int, message: String) : Exception(message)

    private suspend fun dispatch(method: String, a: JSONObject): String {
        val base = "artifacts/$artifactId"
        return when (method) {
            "data" -> {
                val since = if (a.has("since_rev") && !a.isNull("since_rev")) "?since_rev=${a.getInt("since_rev")}" else ""
                http("GET", "$base/data$since")
            }
            "state.get" -> http("GET", "$base/state")
            "state.set" -> http("PUT", "$base/state", only(a, "state", "expected_version"))
            "state.patch" -> http("PATCH", "$base/state", only(a, "patch", "expected_version"))
            "doc.list" -> http("GET", "$base/collections/${name(a)}")
            "doc.add" -> http("POST", "$base/collections/${name(a)}", only(a, "data", "id"))
            "doc.set" -> http("PUT", "$base/collections/${name(a)}/${docId(a)}", only(a, "data", "expected_version"))
            "doc.patch" -> http("PATCH", "$base/collections/${name(a)}/${docId(a)}", only(a, "patch", "expected_version"))
            "doc.remove" -> http("DELETE", "$base/collections/${name(a)}/${docId(a)}")
            "doc.clear" -> http("DELETE", "$base/collections/${name(a)}")
            "batch" -> http("POST", "$base/batch", only(a, "ops"))
            "emit" -> http("POST", "$base/events", only(a, "name", "payload"))
            "ask" -> { onAsk(a.getString("text").take(4000)); "null" }
            "openUrl" -> {
                val url = a.getString("url")
                if (!Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                    throw BridgeError(400, "only http(s) addresses can be opened")
                }
                onOpenUrl(url); "null"
            }
            "toast" -> { onToast(a.getString("message").take(200)); "null" }
            else -> throw BridgeError(400, "unknown method $method")
        }
    }

    private suspend fun http(method: String, path: String, body: String? = null): String {
        val response = repo.raw(method, path, body)
        if (response.status !in 200..299) {
            val detail = runCatching { JSONObject(response.body).optString("detail") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "request failed (${response.status})"
            throw BridgeError(response.status, detail)
        }
        return response.body.ifBlank { "null" }
    }

    /** Collection names and document ids go into a URL path, so they're checked against the same
     * patterns the API enforces (and encoded) rather than trusted. */
    private fun name(a: JSONObject): String {
        val v = a.getString("collection")
        if (!NAME.matches(v)) throw BridgeError(422, "bad collection name")
        return Uri.encode(v)
    }

    private fun docId(a: JSONObject): String {
        val v = a.getString("id")
        if (!DOC_ID.matches(v)) throw BridgeError(422, "bad document id")
        return Uri.encode(v)
    }

    /** A request body made only of the named fields (others the page sent are dropped), omitting nulls. */
    private fun only(a: JSONObject, vararg keys: String): String {
        val out = JSONObject()
        keys.forEach { if (a.has(it) && !a.isNull(it)) out.put(it, a.get(it)) }
        return out.toString()
    }

    private companion object {
        val NAME = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        val DOC_ID = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
    }
}
