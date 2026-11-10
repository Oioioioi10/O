package com.avatarsdev.tiktokupgrade.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a like row came from. Sample rows are never mixed with real ones in any screen. */
object DataSource {
    const val REAL = "REAL"
    const val SAMPLE = "SAMPLE"
}

@Entity(
    tableName = "likes",
    indices = [
        Index(value = ["videoUrl", "likedAt", "source"], unique = true),
        Index(value = ["source", "likedAt"]),
    ],
)
data class LikeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoUrl: String,
    val videoId: String?,
    /** Epoch millis (UTC instant) of when the video was liked. */
    val likedAt: Long,
    val source: String,
    val importedAt: Long,
)

@Entity(tableName = "feed_profiles")
data class FeedProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prompt: String,
    /** FeedRules serialized as JSON. */
    val rulesJson: String,
    val engine: String,
    val createdAt: Long,
)

/** A clip the user shared into the app (or pasted) so it can be scored against the active feed rules. */
@Entity(tableName = "inbox_clips")
data class InboxClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val url: String?,
    val addedAt: Long,
)
