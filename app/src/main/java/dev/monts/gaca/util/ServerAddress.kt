package dev.monts.gaca.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What someone typed (or a setup link carried) → the API base URL the app talks to, always
 * ending in `/api/`; null if it can't be a server address. A bare `host.example.com` means
 * https. Plain http is only accepted when [allowHttp] (debug builds, a dev box on the LAN).
 */
fun normalizeServerUrl(input: String, allowHttp: Boolean): String? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val withScheme = if ("://" in text) text else "https://$text"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" && !(allowHttp && url.scheme == "http")) return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
    val path = url.encodedPath.trimEnd('/').removeSuffix("/api")
    return url.newBuilder().encodedPath("$path/api/").build().toString()
}

/** `gaca://setup?url=…&code=…` — what `gaca-api setup-code --url` prints and the QR code carries. */
data class SetupLink(val url: String?, val code: String?)
