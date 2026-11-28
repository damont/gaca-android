package dev.monts.gaca.ui.artifact

import android.net.Uri
import android.webkit.JavascriptInterface
import dev.monts.gaca.data.repo.ArtifactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

/**
 * What an artifact page can ask the app to do (`window.app.*`, from the SDK the API puts in the page, calls
 * [call]). The page is model-written and untrusted, so it only reaches ITS OWN artifact: data calls are
 * forwarded to POST /artifacts/{id}/call, which checks them, and a few UI actions are handled here. The path
 * is fixed (never built from page text) and the user's token never reaches the page.
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
    /** Shows the app's own Take photo / Choose photo(s) sheet; null if the person cancels. */
    private val pickPhotos: suspend (multiple: Boolean) -> List<Uri>?,
    /** Shrinks one picked photo to upload; null if it can't be read. */
    private val preparePhoto: suspend (Uri) -> ByteArray?,
) {
    private val picking = Mutex()

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

    private suspend fun dispatch(method: String, a: JSONObject): String = when (method) {
        // These act on this screen, so they stay in the app.
        "ask" -> { onAsk(a.getString("text").take(4000)); "null" }
        "openUrl" -> {
            val url = a.getString("url")
            if (!Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                throw BridgeError(400, "only http(s) addresses can be opened")
            }
            onOpenUrl(url); "null"
        }
        "toast" -> { onToast(a.getString("message").take(200)); "null" }
        // The page asks; the APP shows the sheet and uploads. The page only ever gets photo ids back.
        "photo.add" -> addPhotos(a.optBoolean("multiple", false))
        // Everything else is data: the API validates and runs it (same answers and errors as its own endpoints).
        else -> http("POST", "artifacts/$artifactId/call", JSONObject().put("method", method).put("args", a).toString())
    }

    private suspend fun addPhotos(multiple: Boolean): String {
        if (!picking.tryLock()) throw BridgeError(409, "already adding photos")
        try {
            val uris = pickPhotos(multiple).orEmpty()
            val added = JSONArray()
            var lastError: String? = null
            for (uri in uris) {
                val jpeg = preparePhoto(uri)
                if (jpeg == null) {
                    lastError = "that photo couldn't be read"
                    continue
                }
                val response = repo.uploadPhoto(artifactId, jpeg)
                if (response.status in 200..299) {
                    added.put(JSONObject(response.body))
                } else {
                    lastError = errorDetail(response.status, response.body)
                }
            }
            if (uris.isNotEmpty() && added.length() == 0) throw BridgeError(422, lastError ?: "the photo couldn't be added")
            return JSONObject().put("photos", added).toString()
        } finally {
            picking.unlock()
        }
    }

    private fun errorDetail(status: Int, body: String): String =
        runCatching { JSONObject(body).optString("detail") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "request failed ($status)"

    private suspend fun http(method: String, path: String, body: String? = null): String {
        val response = repo.raw(method, path, body)
        if (response.status !in 200..299) {
            throw BridgeError(response.status, errorDetail(response.status, response.body))
        }
        return response.body.ifBlank { "null" }
    }
}
