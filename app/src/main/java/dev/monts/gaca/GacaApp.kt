package dev.monts.gaca

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.monts.gaca.di.AppContainer
import dev.monts.gaca.push.createNotificationChannels

class GacaApp : Application() {
    /** Observed by MainActivity: a new container (new server, signed out) swaps the whole UI over. */
    private var current by mutableStateOf<AppContainer?>(null)
    val container: AppContainer get() = checkNotNull(current) { "GacaApp.onCreate has not run" }

    fun reconfigure() {
        current = AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        reconfigure()
        createNotificationChannels(this)
    }
}
