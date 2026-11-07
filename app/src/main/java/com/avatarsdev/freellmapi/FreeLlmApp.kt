package com.avatarsdev.freellmapi

import android.app.Application

class FreeLlmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
