package com.avatarsdev.tiktokupgrade.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LikeEntity::class, FeedProfileEntity::class, InboxClipEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likeDao(): LikeDao
    abstract fun feedProfileDao(): FeedProfileDao
    abstract fun inboxDao(): InboxDao

    companion object {
        fun build(context: Context, name: String = "tiktok_upgrade.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    }
}
