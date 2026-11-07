package com.avatarsdev.freellmapi.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY priority ASC, id ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    fun observe(id: Long): Flow<ProviderEntity?>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: Long): ProviderEntity?

    @Query("SELECT slug FROM providers")
    suspend fun slugs(): List<String>

    @Query("SELECT COALESCE(MAX(priority), -1) FROM providers")
    suspend fun maxPriority(): Int

    @Insert
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Update
    suspend fun updateAll(providers: List<ProviderEntity>)

    @Delete
    suspend fun delete(provider: ProviderEntity)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY providerId, id")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId ORDER BY id")
    fun observeForProvider(providerId: Long): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY providerId, id")
    suspend fun getAll(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun get(id: Long): ApiKeyEntity?

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId AND enabled = 1 ORDER BY id")
    suspend fun enabledForProvider(providerId: Long): List<ApiKeyEntity>

    @Insert
    suspend fun insert(key: ApiKeyEntity): Long

    @Query("UPDATE api_keys SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE api_keys SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("UPDATE api_keys SET cooldownUntil = :until, lastError = :error, failureCount = failureCount + 1 WHERE id = :id")
    suspend fun markFailure(id: Long, until: Long, error: String)

    @Query("UPDATE api_keys SET lastUsedAt = :at, lastError = NULL, cooldownUntil = 0, successCount = successCount + 1 WHERE id = :id")
    suspend fun markSuccess(id: Long, at: Long)

    @Query("UPDATE api_keys SET cooldownUntil = 0, lastError = NULL")
    suspend fun clearCooldowns()

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
abstract class ModelDao {
    @Query("SELECT * FROM models ORDER BY providerId, modelId")
    abstract fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY providerId, modelId")
    abstract suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY modelId")
    abstract fun observeForProvider(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId")
    abstract suspend fun forProvider(providerId: Long): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(models: List<ModelEntity>): List<Long>

    @Query("UPDATE models SET displayName = :displayName, contextWindow = :contextWindow, isFree = :isFree, source = :source, updatedAt = :at WHERE providerId = :providerId AND modelId = :modelId")
    abstract suspend fun refresh(providerId: Long, modelId: String, displayName: String, contextWindow: Int?, isFree: Boolean, source: String, at: Long)

    @Query("UPDATE models SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE models SET enabled = :enabled WHERE providerId = :providerId")
    abstract suspend fun setEnabledForProvider(providerId: Long, enabled: Boolean)

    @Query("DELETE FROM models WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Transaction
    open suspend fun upsertAll(models: List<ModelEntity>) {
        insertAll(models)
        for (m in models) refresh(m.providerId, m.modelId, m.displayName, m.contextWindow, m.isFree, m.source, m.updatedAt)
    }
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY alias")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes ORDER BY alias")
    suspend fun getAll(): List<RouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(route: RouteEntity): Long

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM request_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RequestLogEntity>>

    @Insert
    suspend fun insert(log: RequestLogEntity): Long

    @Query("DELETE FROM request_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM request_logs")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM request_logs")
    suspend fun count(): Int
}

@Dao
abstract class UsageDao {
    @Query("SELECT * FROM usage_daily WHERE day >= :fromDay ORDER BY day")
    abstract fun observeSince(fromDay: String): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily WHERE day >= :fromDay")
    abstract suspend fun since(fromDay: String): List<UsageDailyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(row: UsageDailyEntity)

    @Query(
        """UPDATE usage_daily SET requests = requests + 1, successes = successes + :success, failures = failures + :failure,
           promptTokens = promptTokens + :prompt, completionTokens = completionTokens + :completion,
           totalLatencyMs = totalLatencyMs + :latency, providerName = :providerName
           WHERE day = :day AND providerId = :providerId AND modelId = :modelId""",
    )
    abstract suspend fun increment(day: String, providerId: Long, modelId: String, providerName: String, success: Int, failure: Int, prompt: Long, completion: Long, latency: Long)

    /** SQLite on Android 10 has no UPSERT syntax, so insert-if-missing then increment inside one transaction. */
    @Transaction
    open suspend fun record(day: String, providerId: Long, providerName: String, modelId: String, success: Boolean, prompt: Long, completion: Long, latency: Long) {
        insertIgnore(UsageDailyEntity(day = day, providerId = providerId, providerName = providerName, modelId = modelId))
        increment(day, providerId, modelId, providerName, if (success) 1 else 0, if (success) 0 else 1, prompt, completion, latency)
    }

    @Query("DELETE FROM usage_daily")
    abstract suspend fun clear()
}
