package dev.monts.gaca.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * The structured part of an error from gaca-api: `{"code": "...", "message": "...", "detail": "...", "info": {...}}`.
 * Cloud-related routes (and answers passed through from the cloud) carry a stable machine `code`
 * (e.g. `cloud_not_configured`, `cloud_artifacts_present`) and optional `info` with numbers or reasons.
 * Older routes only have `detail`, so every field here may be null.
 */
data class ApiError(val status: Int, val code: String?, val message: String?, val info: JsonObject?)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Reads an error body. Never throws: anything unreadable gives an [ApiError] with only [status]. */
fun parseApiError(status: Int, body: String?): ApiError {
    val obj = runCatching { errorJson.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull()
        ?: return ApiError(status, null, null, null)
    fun text(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    return ApiError(status, text("code"), text("message") ?: text("detail"), obj["info"] as? JsonObject)
}

/** The structured error behind this exception, or null when it was not an HTTP error answer. */
fun Throwable.apiError(): ApiError? =
    (this as? HttpException)?.let { parseApiError(it.code(), runCatching { it.response()?.errorBody()?.string() }.getOrNull()) }

/** A whole-number field of [ApiError.info], or null. */
fun ApiError.infoInt(key: String): Int? = info?.get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull()
