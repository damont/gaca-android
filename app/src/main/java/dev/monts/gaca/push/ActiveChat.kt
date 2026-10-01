package dev.monts.gaca.push

/**
 * The chat thread currently on screen, or null. A push for a reply in the thread you're
 * already reading is noise — GacaFirebaseMessagingService checks this before posting.
 * Set only while the thread screen is RESUMED (see ChatThreadScreen), so a backgrounded
 * app never suppresses its own notifications.
 */
object ActiveChat {
    @Volatile
    var sessionId: String? = null
}
