package com.avatarsdev.tiktokupgrade

import android.app.Application

class TikTokUpgradeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
