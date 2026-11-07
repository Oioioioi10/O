package com.avatarsdev.freellmapi.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "providers", indices = [Index(value = ["slug"], unique = true)])
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val slug: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val defaultModel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** An API key. Only the AES-GCM ciphertext (Android Keystore) and a masked preview are stored. */
@Entity(
    tableName = "api_keys",
    foreignKeys = [ForeignKey(entity = ProviderEntity::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("providerId")],
)
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val label: String,
    val encryptedKey: String,
    val maskedKey: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val cooldownUntil: Long = 0,
    val lastError: String? = null,
    val successCount: Long = 0,
    val failureCount: Long = 0,
)

@Entity(
    tableName = "models",
    foreignKeys = [ForeignKey(entity = ProviderEntity::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["providerId", "modelId"], unique = true)],
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val isFree: Boolean = false,
    val enabled: Boolean = true,
    /** builtin | remote | manual */
    val source: String = "builtin",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A named route (alias) with an ordered fallback chain of `providerId|modelId` targets, one per line. */
@Entity(tableName = "routes", indices = [Index(value = ["alias"], unique = true)])
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val description: String = "",
    val targets: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One gateway request. Prompt/response text and keys are never stored. */
@Entity(tableName = "request_logs", indices = [Index("timestamp")])
data class RequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val source: String,
    val endpoint: String,
    val requestedModel: String,
    val providerName: String?,
    val providerType: String?,
    val model: String?,
    val statusCode: Int,
    val success: Boolean,
    val latencyMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val attempts: Int,
    val fallbackPath: String,
    val errorMessage: String?,
    val stream: Boolean,
)

/** Daily usage counters per provider/model; survives log clean-up. */
@Entity(tableName = "usage_daily", primaryKeys = ["day", "providerId", "modelId"])
data class UsageDailyEntity(
    val day: String,
    val providerId: Long,
    val providerName: String,
    val modelId: String,
    val requests: Long = 0,
    val successes: Long = 0,
    val failures: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalLatencyMs: Long = 0,
)
