package com.avatarsdev.tiktokupgrade.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes WHERE source = :source ORDER BY likedAt DESC")
    fun observeBySource(source: String): Flow<List<LikeEntity>>

    @Query("SELECT COUNT(*) FROM likes WHERE source = :source")
    fun observeCount(source: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM likes WHERE source = :source")
    suspend fun count(source: String): Int

    /** Returns row ids; -1 for rows skipped as duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<LikeEntity>): List<Long>

    @Query("DELETE FROM likes WHERE source = :source")
    suspend fun deleteBySource(source: String)
}

@Dao
interface FeedProfileDao {
    @Query("SELECT * FROM feed_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FeedProfileEntity>>

    @Insert
    suspend fun insert(profile: FeedProfileEntity): Long

    @Query("DELETE FROM feed_profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_clips ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<InboxClipEntity>>

    @Insert
    suspend fun insert(clip: InboxClipEntity): Long

    @Query("DELETE FROM inbox_clips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM inbox_clips")
    suspend fun clear()
}
