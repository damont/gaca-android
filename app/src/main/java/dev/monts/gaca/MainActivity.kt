package dev.monts.gaca

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.monts.gaca.data.repo.SetupRepository
import dev.monts.gaca.ui.setup.SetupScreen
import dev.monts.gaca.ui.setup.SetupViewModel
import dev.monts.gaca.util.SetupLink
import dev.monts.gaca.push.EXTRA_ALERT_ID
import dev.monts.gaca.push.EXTRA_SESSION_ID
import dev.monts.gaca.ui.nav.GacaNavHost
import dev.monts.gaca.ui.theme.GacaTheme

class MainActivity : ComponentActivity() {

    private val pendingAlertId = mutableStateOf<String?>(null)
    private val pendingSessionId = mutableStateOf<String?>(null)
    private val pendingArtifactId = mutableStateOf<String?>(null)
    private val pendingSetupLink = mutableStateOf<SetupLink?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* declining just means no future pushes show — nothing to react to here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAlertId.value = intent?.getStringExtra(EXTRA_ALERT_ID)
        pendingSessionId.value = intent?.getStringExtra(EXTRA_SESSION_ID)
        pendingArtifactId.value = artifactIdFrom(intent)
        pendingSetupLink.value = setupLinkFrom(intent)
        requestNotificationPermissionIfNeeded()

        val app = application as GacaApp
        setContent {
            GacaTheme {
                val container = app.container
                if (!container.configured) {
                    val setupViewModel = viewModel<SetupViewModel>(
                        key = "setup",
                        factory = viewModelFactory {
                            initializer {
                                SetupViewModel(
                                    setup = SetupRepository(),
                                    settingsStore = container.settingsStore,
                                    tokenStore = container.tokenStore,
                                    allowHttp = BuildConfig.DEBUG,
                                    onConfigured = app::reconfigure,
                                )
                            }
                        },
                    )
                    val link = pendingSetupLink.value
                    LaunchedEffect(link) {
                        if (link != null) {
                            setupViewModel.applyLink(link)
                            pendingSetupLink.value = null
                        }
                    }
                    SetupScreen(setupViewModel)
                    return@GacaTheme
                }
                // A new container (another server, signed out) starts the whole UI fresh.
                key(container) {
                GacaNavHost(
                    container = container,
                    pendingAlertId = pendingAlertId.value,
                    onPendingAlertConsumed = { pendingAlertId.value = null },
                    pendingSessionId = pendingSessionId.value,
                    onPendingSessionConsumed = { pendingSessionId.value = null },
                    pendingArtifactId = pendingArtifactId.value,
                    onPendingArtifactConsumed = { pendingArtifactId.value = null },
                )
                }
            }
        }
    }

    /** MainActivity is singleTask, so tapping a notification while already running lands here, not a fresh onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAlertId.value = intent.getStringExtra(EXTRA_ALERT_ID)
        pendingSessionId.value = intent.getStringExtra(EXTRA_SESSION_ID)
        pendingArtifactId.value = artifactIdFrom(intent)
        pendingSetupLink.value = setupLinkFrom(intent)
    }

    /** `gaca://setup?url=…&code=…` → what to prefill; only acted on while the app has no server yet. */
    private fun setupLinkFrom(intent: Intent?): SetupLink? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "gaca" || uri.host != "setup") return null
        return SetupLink(uri.getQueryParameter("url"), uri.getQueryParameter("code"))
    }

    /** `gaca://artifact/<id>` → the id (ids are 32 hex characters; anything else is ignored). */
    private fun artifactIdFrom(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "gaca" || uri.host != "artifact") return null
        return uri.pathSegments.firstOrNull()?.takeIf { Regex("^[0-9a-f]{32}$").matches(it) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
