package dev.monts.gaca

import android.app.Application
import dev.monts.gaca.di.AppContainer
import dev.monts.gaca.push.createNotificationChannels

class GacaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels(this)
    }
}
