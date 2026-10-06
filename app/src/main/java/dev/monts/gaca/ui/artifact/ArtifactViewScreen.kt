package dev.monts.gaca.ui.artifact

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.monts.gaca.data.repo.ArtifactRepository
import dev.monts.gaca.ui.theme.HeaderAccent
import dev.monts.gaca.ui.theme.PageBackground
import dev.monts.gaca.ui.theme.SeverityUrgentDot
import dev.monts.gaca.ui.theme.TextSecondary
import org.json.JSONObject

private const val PAGE_ORIGIN = "https://artifact.invalid/"

/**
 * Shows one artifact — an HTML page an agent wrote — in a locked-down WebView. The page is
 * untrusted: no network, no file/content access, no navigation away, and the only way to anything
 * outside it is [ArtifactBridge]'s narrow `window.app` API. Its data lives in the GACA API, so
 * the page can be rewritten (or the agent can change the data) without anything being lost.
 */
@Composable
fun ArtifactViewScreen(
    viewModel: ArtifactViewModel,
    repo: ArtifactRepository,
    onBack: () -> Unit,
    onOpenChat: (sessionId: String, prefill: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(state.chatSessionId) {
        val sessionId = state.chatSessionId
        if (sessionId != null) {
            onOpenChat(sessionId, state.chatPrefill.orEmpty())
            viewModel.consumeChat()
        }
    }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    // Tell the page to refresh when the screen comes back (the agent may have changed things).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webView?.onResume()
                    webView?.evaluateJavascript("window.__gaca&&window.__gaca.resume()", null)
                }
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(webView) {
        val view = webView
        onDispose {
            view?.apply {
                removeJavascriptInterface("GacaBridge")
                stopLoading()
                destroy()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            // Edge-to-edge doesn't resize the window for the keyboard, so the page would sit
            // under it; shrinking the page's area lets the WebView scroll the focused field into view.
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text(
                state.artifact?.title ?: "Page",
                style = MaterialTheme.typography.titleMedium,
                color = HeaderAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Reload") }, onClick = { menuOpen = false; viewModel.load() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menuOpen = false; viewModel.archive() })
                    DropdownMenuItem(text = { Text("Delete…") }, onClick = { menuOpen = false; confirmDelete = true })
                }
            }
        }

        val errorText = state.error
        if (errorText != null) {
            Text(
                errorText,
                style = MaterialTheme.typography.bodySmall,
                color = SeverityUrgentDot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val document = state.document
            val artifact = state.artifact
            if (document != null && artifact != null) {
                key(state.loadCount) {
                    ArtifactWebView(
                        artifactId = artifact.id,
                        document = document,
                        repo = repo,
                        onAsk = viewModel::ask,
                        onToast = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
                        onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        onCreated = { webView = it },
                    )
                }
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HeaderAccent)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this page?") },
            text = { Text("The page and everything it stored will be gone for good. Archive it instead to just tidy it away.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArtifactWebView(
    artifactId: String,
    document: String,
    repo: ArtifactRepository,
    onAsk: (String) -> Unit,
    onToast: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCreated: (WebView) -> Unit,
) {
    val scope = rememberCoroutineScope()
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(PageBackground.toArgb())
                settings.apply {
                    javaScriptEnabled = true
                    // The page's data belongs in the API (window.app), not in browser storage the agent can't see.
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                val view = this
                val bridge = ArtifactBridge(
                    artifactId = artifactId,
                    repo = repo,
                    scope = scope,
                    reply = { callId, ok, json ->
                        view.post {
                            view.evaluateJavascript(
                                "window.__gaca&&window.__gaca.resolve(${JSONObject.quote(callId)},$ok,${JSONObject.quote(json)})",
                                null,
                            )
                        }
                    },
                    onAsk = onAsk,
                    onOpenUrl = onOpenUrl,
                    onToast = onToast,
                )
                addJavascriptInterface(bridge, "GacaBridge")
                webViewClient = object : WebViewClient() {
                    // The page never navigates. A link the user taps opens in their browser instead.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (request.hasGesture() && (url.startsWith("https://") || url.startsWith("http://"))) onOpenUrl(url)
                        return true
                    }

                    // Belt and braces with the page's CSP: nothing leaves for the network.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url.scheme
                        return if (scheme == "http" || scheme == "https") {
                            WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), "".byteInputStream())
                        } else {
                            null
                        }
                    }
                }
                onCreated(this)
                loadDataWithBaseURL(PAGE_ORIGIN, document, "text/html", "utf-8", null)
            }
        },
    )
}
