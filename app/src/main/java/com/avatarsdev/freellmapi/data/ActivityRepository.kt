package com.avatarsdev.freellmapi.data

import com.avatarsdev.freellmapi.core.gateway.RouteTarget
import com.avatarsdev.freellmapi.data.db.AppDatabase
import com.avatarsdev.freellmapi.data.db.RequestLogEntity
import com.avatarsdev.freellmapi.data.db.RouteEntity
import com.avatarsdev.freellmapi.data.db.UsageDailyEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Request logs, usage counters and route aliases. */
class ActivityRepository(private val db: AppDatabase) {

    fun recentLogs(limit: Int = 300): Flow<List<RequestLogEntity>> = db.logs().observeRecent(limit)

    fun usageSince(daysBack: Long): Flow<List<UsageDailyEntity>> =
        db.usage().observeSince(LocalDate.now().minusDays(daysBack).toString())

    suspend fun clearLogs() = db.logs().clear()
    suspend fun clearUsage() = db.usage().clear()

    val routes: Flow<List<RouteEntity>> = db.routes().observeAll()

    suspend fun saveRoute(alias: String, description: String, targets: List<RouteTarget>, existingId: Long = 0) {
        val clean = alias.trim()
        require(ALIAS_REGEX.matches(clean)) { "Alias may only contain letters, digits, '.', '_' and '-'" }
        require(targets.isNotEmpty()) { "Pick at least one model" }
        db.routes().upsert(
            RouteEntity(
                id = existingId,
                alias = clean,
                description = description.trim(),
                targets = RoomGatewayStore.encodeTargets(targets),
            ),
        )
    }

    suspend fun deleteRoute(id: Long) = db.routes().delete(id)

    companion object {
        val ALIAS_REGEX = Regex("^[A-Za-z0-9._-]{1,48}$")
    }
}
