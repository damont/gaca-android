package dev.monts.gaca.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.monts.gaca.GacaApp
import dev.monts.gaca.MainActivity
import dev.monts.gaca.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Intent extra carrying the tapped alert's id — read by MainActivity to deep-link into its detail screen. */
const val EXTRA_ALERT_ID = "alert_id"

/**
 * Handles two things FCM hands the app: a fresh registration token (register
 * it with the backend) and an incoming message while the app is in the
 * foreground (build the system notification ourselves — FCM only
 * auto-displays a notification+data message when the app is backgrounded;
 * see services/push.py on the backend for the other half of this split).
 */
class GacaFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val container = (application as GacaApp).container
        scope.launch { container.pushRepository.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: ""
        val alertId = message.data["alert_id"]
        val severity = message.data["severity"]
        val channelId = if (severity == "urgent") {
            getString(R.string.notification_channel_urgent_id)
        } else {
            getString(R.string.notification_channel_standard_id)
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (alertId != null) putExtra(EXTRA_ALERT_ID, alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId?.hashCode() ?: 0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // On API 33+ this crashes at the OS level without the runtime grant.
        // No fallback UI here if it's missing — MainActivity requests it on
        // launch (see LaunchedEffect there); this message is simply not shown.
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canPost) {
            NotificationManagerCompat.from(this).notify(alertId?.hashCode() ?: title.hashCode(), notification)
        }
    }
}
