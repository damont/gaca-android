package dev.monts.gaca.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

private val json = Json { ignoreUnknownKeys = true }

/**
 * gaca-api error bodies are `{"detail": ...}` (FastAPI's default shape) — but
 * `detail` takes two different shapes depending on the failure:
 *  - a plain string for the app's own domain errors (401/403/404/409/400)
 *  - a list of `{loc, msg, type}` objects for pydantic's own 422 validation
 *    errors (e.g. submitting a blank email/password)
 * Handle both rather than showing Retrofit's generic "HTTP 422 ...".
 */
fun Throwable.friendlyMessage(): String {
    if (this is HttpException) {
        val detail = runCatching {
            val body = response()?.errorBody()?.string() ?: return@runCatching null
            val element = json.parseToJsonElement(body).jsonObject["detail"] ?: return@runCatching null
            when (element) {
                is JsonPrimitive -> element.content
                is JsonArray -> element.mapNotNull { item ->
                    val obj = item.jsonObject
                    val field = (obj["loc"] as? JsonArray)?.lastOrNull()?.jsonPrimitive?.content
                    val msg = obj["msg"]?.jsonPrimitive?.content
                    when {
                        field != null && msg != null -> "$field: $msg"
                        msg != null -> msg
                        else -> null
                    }
                }.joinToString("; ").ifBlank { null }
                else -> null
            }
        }.getOrNull()
        if (!detail.isNullOrBlank()) return detail
    }
    return message ?: "Something went wrong"
}
