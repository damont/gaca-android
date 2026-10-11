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

/** `gaca://setup?url=…&code=…` — what `gaca-api pair` prints and its QR code carries. */
data class SetupLink(val url: String?, val code: String?)

/** The link in a scanned QR code or a tapped `gaca://setup` link, or null if it is anything else. */
fun parseSetupLink(text: String?): SetupLink? {
    val uri = runCatching { java.net.URI(text?.trim() ?: return null) }.getOrNull() ?: return null
    if (uri.scheme != "gaca" || uri.host != "setup") return null
    val params = (uri.rawQuery ?: "").split("&").filter { it.isNotEmpty() }.associate {
        val (key, value) = (it.split("=", limit = 2) + "").take(2)
        java.net.URLDecoder.decode(key, "UTF-8") to java.net.URLDecoder.decode(value, "UTF-8")
    }
    return SetupLink(params["url"], params["code"])
}
