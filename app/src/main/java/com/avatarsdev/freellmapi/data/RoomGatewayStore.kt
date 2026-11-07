package com.avatarsdev.freellmapi.data

import com.avatarsdev.freellmapi.core.gateway.GatewaySnapshot
import com.avatarsdev.freellmapi.core.gateway.GatewayStore
import com.avatarsdev.freellmapi.core.gateway.KeySnapshot
import com.avatarsdev.freellmapi.core.gateway.ProviderSnapshot
import com.avatarsdev.freellmapi.core.gateway.RequestOutcome
import com.avatarsdev.freellmapi.core.gateway.RouteSnapshot
import com.avatarsdev.freellmapi.core.gateway.RouteTarget
import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.core.security.Redactor
import com.avatarsdev.freellmapi.data.db.AppDatabase
import com.avatarsdev.freellmapi.data.db.RequestLogEntity
import com.avatarsdev.freellmapi.security.SecretCipher
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** Gateway storage backed by Room. Keys are decrypted only on demand, per request, and never cached. */
class RoomGatewayStore(
    private val db: AppDatabase,
    private val cipher: SecretCipher,
    private val settings: SettingsRepository,
) : GatewayStore {

    private val writes = AtomicInteger(0)

    override suspend fun snapshot(): GatewaySnapshot {
        val providers = db.providers().getAll()
        val keys = db.keys().getAll().groupBy { it.providerId }
        val models = db.models().getAll().filter { it.enabled }.groupBy { it.providerId }
        val routes = db.routes().getAll().map { RouteSnapshot(it.alias, parseTargets(it.targets)) }
        val usage = db.usage().since(LocalDate.now().minusDays(7).toString())
        val latency = usage.filter { it.successes > 0 }
            .groupBy { "${it.providerId}:${it.modelId}" }
            .mapValues { (_, rows) -> rows.sumOf { it.totalLatencyMs } / rows.sumOf { it.successes }.coerceAtLeast(1) }
        return GatewaySnapshot(
            providers = providers.map { p ->
                ProviderSnapshot(
                    id = p.id,
                    type = ProviderType.fromId(p.type),
                    name = p.name,
                    slug = p.slug,
                    baseUrl = p.baseUrl,
                    enabled = p.enabled,
                    priority = p.priority,
                    defaultModel = p.defaultModel,
                    keys = keys[p.id].orEmpty().map { KeySnapshot(it.id, it.label, it.enabled, it.cooldownUntil) },
                    models = models[p.id].orEmpty().map { it.modelId },
                )
            },
            routes = routes,
            config = settings.current.gatewayConfig,
            latency = latency,
        )
    }

    override suspend fun decryptKey(keyId: Long): String? {
        val entity = db.keys().get(keyId) ?: return null
        return try {
            cipher.decrypt(entity.encryptedKey)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun record(outcome: RequestOutcome) {
        db.logs().insert(
            RequestLogEntity(
                timestamp = outcome.timestamp,
                source = outcome.source,
                endpoint = outcome.endpoint,
                requestedModel = outcome.requestedModel,
                providerName = outcome.providerName,
                providerType = outcome.providerType,
                model = outcome.model,
                statusCode = outcome.statusCode,
                success = outcome.success,
                latencyMs = outcome.latencyMs,
                promptTokens = outcome.usage.promptTokens,
                completionTokens = outcome.usage.completionTokens,
                attempts = outcome.attempts.size,
                fallbackPath = Redactor.redact(outcome.fallbackPath).take(1000),
                errorMessage = outcome.error?.let { Redactor.redact(it).take(1000) },
                stream = outcome.stream,
            ),
        )
        db.usage().record(
            day = LocalDate.now().toString(),
            providerId = outcome.providerId ?: FAILED_BUCKET,
            providerName = outcome.providerName ?: "Unrouted",
            modelId = outcome.model ?: outcome.requestedModel,
            success = outcome.success,
            prompt = outcome.usage.promptTokens.toLong(),
            completion = outcome.usage.completionTokens.toLong(),
            latency = if (outcome.success) outcome.latencyMs else 0L,
        )
        if (writes.incrementAndGet() % 25 == 1) {
            val days = settings.current.logRetentionDays.coerceAtLeast(1)
            db.logs().deleteOlderThan(System.currentTimeMillis() - days * 24L * 3600_000L)
        }
    }

    override suspend fun markKeyFailure(keyId: Long, kind: FailureKind, message: String, cooldownUntil: Long) {
        db.keys().markFailure(keyId, cooldownUntil, Redactor.redact(message).take(300))
    }

    override suspend fun markKeySuccess(keyId: Long, at: Long) {
        db.keys().markSuccess(keyId, at)
    }

    companion object {
        const val FAILED_BUCKET = -1L

        fun parseTargets(raw: String): List<RouteTarget> = raw.lines().mapNotNull { line ->
            val i = line.indexOf('|')
            if (i <= 0) return@mapNotNull null
            val pid = line.substring(0, i).toLongOrNull() ?: return@mapNotNull null
            val model = line.substring(i + 1).trim()
            if (model.isEmpty()) null else RouteTarget(pid, model)
        }

        fun encodeTargets(targets: List<RouteTarget>): String = targets.joinToString("\n") { "${it.providerId}|${it.modelId}" }
    }
}
