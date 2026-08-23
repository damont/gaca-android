package dev.monts.gaca.data.repo

import com.google.firebase.messaging.FirebaseMessaging
import dev.monts.gaca.data.remote.GacaApi
import dev.monts.gaca.data.remote.PushTokenRegisterRequest
import dev.monts.gaca.data.remote.PushTokenUnregisterRequest
import kotlinx.coroutines.tasks.await

/**
 * Keeps this device's FCM token registered against the signed-in account.
 * Best-effort throughout — a failed registration just means push notifications
 * lag until the next successful call (app relaunch, or the next onNewToken);
 * it must never block or fail sign-in/sign-out.
 */
class PushRepository(private val api: GacaApi) {

    /** Call after every successful sign-in, and once at app start if already signed in. */
    suspend fun registerCurrentToken() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token)
        }
    }

    suspend fun registerToken(token: String) {
        runCatching { api.registerPushToken(PushTokenRegisterRequest(token)) }
    }

    /** Call on sign-out so a shared/reset device doesn't keep alerting for the old account. */
    suspend fun unregisterCurrentToken() {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            api.unregisterPushToken(PushTokenUnregisterRequest(token))
        }
    }
}
