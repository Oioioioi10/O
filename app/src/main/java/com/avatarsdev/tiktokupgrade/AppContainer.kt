package com.avatarsdev.tiktokupgrade

import android.content.Context
import com.avatarsdev.tiktokupgrade.data.LikeRepository
import com.avatarsdev.tiktokupgrade.data.SettingsRepository
import com.avatarsdev.tiktokupgrade.data.db.AppDatabase
import com.avatarsdev.tiktokupgrade.domain.feed.LlmPromptClient

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val likes = LikeRepository(db)
    val llm = LlmPromptClient()
}
