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
import dev.monts.gaca.push.EXTRA_ALERT_ID
import dev.monts.gaca.push.EXTRA_SESSION_ID
import dev.monts.gaca.ui.nav.GacaNavHost
import dev.monts.gaca.ui.theme.GacaTheme

class MainActivity : ComponentActivity() {

    private val pendingAlertId = mutableStateOf<String?>(null)
    private val pendingSessionId = mutableStateOf<String?>(null)
    private val pendingArtifactId = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* declining just means no future pushes show — nothing to react to here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAlertId.value = intent?.getStringExtra(EXTRA_ALERT_ID)
        pendingSessionId.value = intent?.getStringExtra(EXTRA_SESSION_ID)
        pendingArtifactId.value = artifactIdFrom(intent)
        requestNotificationPermissionIfNeeded()

        val container = (application as GacaApp).container
        setContent {
            GacaTheme {
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

    /** MainActivity is singleTask, so tapping a notification while already running lands here, not a fresh onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAlertId.value = intent.getStringExtra(EXTRA_ALERT_ID)
        pendingSessionId.value = intent.getStringExtra(EXTRA_SESSION_ID)
        pendingArtifactId.value = artifactIdFrom(intent)
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
