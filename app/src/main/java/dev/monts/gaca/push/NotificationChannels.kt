package dev.monts.gaca.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.monts.gaca.R

/**
 * Two channels, matching the design's "urgent gets a full card and sound;
 * everything else stays quiet" split (see the Notification mockup in the
 * design canvas): urgent interrupts (HIGH — heads-up popup + sound),
 * standard just sits in the shade (DEFAULT — no heads-up, no sound).
 *
 * Ids live in strings.xml (notification_channel_*_id) as the single source
 * of truth — AndroidManifest's default-channel meta-data references the same
 * resource, and GacaFirebaseMessagingService reads it the same way when
 * picking a channel per message, so there's exactly one place these strings
 * are spelled out.
 */
fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    manager.createNotificationChannel(
        NotificationChannel(
            context.getString(R.string.notification_channel_urgent_id),
            context.getString(R.string.notification_channel_urgent_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_urgent_description)
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            context.getString(R.string.notification_channel_standard_id),
            context.getString(R.string.notification_channel_standard_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_standard_description)
        },
    )
}
