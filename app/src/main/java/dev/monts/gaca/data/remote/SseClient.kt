package dev.monts.gaca.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** One parsed Server-Sent Event: `id`/`event`/`data` per the SSE wire format
 * (`data:`, `event:`, `id:` lines, a blank line dispatches what's been
 * accumulated). `data` is the raw payload — each event type's own JSON shape
 * ("assistant.delta", "tool.started", …) is for whoever consumes the stream
 * to interpret, not this client. */
data class SseEvent(val id: String?, val type: String, val data: String)

/**
 * A minimal hand-rolled SSE client over the [OkHttpClient] this app already
 * builds (di/AppContainer.kt) — same auth interceptor, same logging, same
 * timeouts, nothing new to configure. Retrofit doesn't support a genuinely
 * streaming response body, so this reads one line at a time instead.
 *
 * Reconnects automatically on any I/O failure, resuming from the last event
 * id it saw via the `Last-Event-ID` header — api-contract.md §6: events are
 * "durable and sequenced, not fire-and-forget," specifically so a dropped
 * connection doesn't lose anything the server already has.
 */
class SseClient(private val client: OkHttpClient, private val baseUrl: String) {

    fun stream(path: String, initialLastEventId: String? = null): Flow<SseEvent> = callbackFlow {
        var lastEventId = initialLastEventId
        var currentCall: Call? = null

        val job = launch(Dispatchers.IO) {
            while (isActive) {
                val request = Request.Builder()
                    .url(baseUrl + path)
                    .header("Accept", "text/event-stream")
                    .apply { lastEventId?.let { header("Last-Event-ID", it) } }
                    .build()
                val call = client.newCall(request)
                currentCall = call
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("SSE $path failed: ${response.code}")
                        val source = response.body?.source() ?: throw IOException("SSE $path: empty body")

                        var eventType = "message"
                        var data = StringBuilder()
                        var eventId: String? = null

                        while (isActive) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) {
                                        eventId?.let { lastEventId = it }
                                        trySend(SseEvent(eventId, eventType, data.toString().removeSuffix("\n")))
                                    }
                                    eventType = "message"
                                    data = StringBuilder()
                                    eventId = null
                                }
                                line.startsWith("data:") -> data.append(line.removePrefix("data:").trimStart()).append("\n")
                                line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                                line.startsWith("id:") -> eventId = line.removePrefix("id:").trim()
                                // a line starting with ":" is a comment/heartbeat — nothing to do
                            }
                        }
                    }
                    // The server closed the stream cleanly — there's no "done for good"
                    // signal in this contract, so reconnecting is still the right call.
                } catch (_: IOException) {
                    // Dropped mid-stream, or never connected — fall through to reconnect below.
                }
                if (!isActive) break
                delay(2000) // a simple fixed backoff; good enough until this needs to be smarter
            }
        }

        awaitClose {
            currentCall?.cancel()
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
