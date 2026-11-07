package com.avatarsdev.freellmapi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderEntity::class,
        ApiKeyEntity::class,
        ModelEntity::class,
        RouteEntity::class,
        RequestLogEntity::class,
        UsageDailyEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providers(): ProviderDao
    abstract fun keys(): ApiKeyDao
    abstract fun models(): ModelDao
    abstract fun routes(): RouteDao
    abstract fun logs(): LogDao
    abstract fun usage(): UsageDao

    companion object {
        fun create(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "freellmapi.db")
            }
            return builder.fallbackToDestructiveMigration().build()
        }
    }
}
