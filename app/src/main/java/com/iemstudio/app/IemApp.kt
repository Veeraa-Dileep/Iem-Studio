package com.iemstudio.app

import android.app.Application
import android.content.Context

/** Simple manual dependency container — no DI framework needed for a companion app. */
class AppContainer(context: Context) {
    val controller = IemController(context.applicationContext)
}

class IemApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
