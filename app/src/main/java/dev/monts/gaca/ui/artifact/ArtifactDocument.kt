package dev.monts.gaca.ui.artifact

import org.json.JSONObject

/**
 * Wraps an artifact's HTML for display: a Content-Security-Policy that cuts the page off from the
 * network (it can only talk to the app through `window.app`), the app's colors as CSS variables,
 * the base style, and the SDK — all placed before the page's own markup so its scripts find
 * `window.app` ready and its CSS can still override the base style.
 */
object ArtifactDocument {

    // No connect-src / frame-src / form-action, so fetch, XHR, WebSocket, iframes and form posts
    // all fall back to default-src 'none'. Images and fonts must be inline (data:).
    private const val CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
        "img-src data: blob:; font-src data:; media-src data: blob:; base-uri 'none'; form-action 'none'"

    private val THEME_CSS = """
        :root{--bg:#F0F4F9;--surface:#FFFFFF;--text:#282F35;--muted:#747B83;--accent:#2D3C4C;
        --on-accent:#F4F9FE;--border:#C8CED5;--chip:#D8DFE6;--danger:#C55C43;--ok:#4C8C6A;
        --radius:12px;--font:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;color-scheme:light}
        html{-webkit-text-size-adjust:100%}
        body{margin:0;padding:16px;background:var(--bg);color:var(--text);font:16px/1.45 var(--font);
        -webkit-tap-highlight-color:transparent}
        button,input,select,textarea{font:inherit}
    """.trimIndent().replace("\n", "")

    fun build(html: String, sdk: String, artifactId: String, title: String): String {
        val init = JSONObject()
            .put("artifact", JSONObject().put("id", artifactId).put("title", title))
            .put("theme", JSONObject().put("dark", false).put("accent", "#2D3C4C"))
            .toString()
            .replace("</", "<\\/") // can't close the script tag early
        val prelude = buildString {
            append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(CSP).append("\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>").append(THEME_CSS).append("</style>")
            append("<script>window.__GACA_INIT=").append(init).append(";</script>")
            append("<script>").append(sdk.replace("</script", "<\\/script")).append("</script>")
        }
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (head != null) return html.substring(0, head.range.last + 1) + prelude + html.substring(head.range.last + 1)
        val root = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (root != null) return html.substring(0, root.range.last + 1) + "<head>" + prelude + "</head>" + html.substring(root.range.last + 1)
        return "<!doctype html><html><head><meta charset=\"utf-8\">$prelude</head><body>$html</body></html>"
    }
}
