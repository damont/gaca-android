package dev.monts.gaca.util

import java.net.URI

private val TOKEN = Regex("^[A-Za-z0-9_-]{8,512}$")

/**
 * Pulls the secret token out of whatever the person pasted or tapped: an invite link
 * (`https://<site>/join/<token>`), the app's own `gaca://join/<token>`, or the bare token. Returns null for anything else.
 *
 * The app never contacts the link's address: the token goes only to the person's own server, which asks the cloud
 * it is linked to. So the host in the link does not matter and is not checked.
 */
fun parseInviteToken(text: String?): String? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
    if (!raw.contains("://")) return raw.takeIf { TOKEN.matches(it) }
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val segments = when (uri.scheme?.lowercase()) {
        "http", "https" -> uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        // gaca://join/<token>: "join" is the host here
        "gaca" -> if (uri.host == "join") uri.path.orEmpty().split('/').filter { it.isNotEmpty() }.let { listOf("join") + it } else return null
        else -> return null
    }
    val at = segments.indexOf("join")
    return segments.getOrNull(at + 1)?.takeIf { at >= 0 && TOKEN.matches(it) }
}
