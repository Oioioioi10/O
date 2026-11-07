package com.avatarsdev.freellmapi.core.gateway

import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.provider.ProviderType

enum class RoutingStrategy(val label: String, val description: String) {
    PRIORITY("Priority", "Try providers in your priority order"),
    ROUND_ROBIN("Round-robin", "Spread requests evenly across matching providers"),
    FASTEST("Fastest", "Prefer the lowest average latency measured so far"),
}

data class KeySnapshot(val id: Long, val label: String, val enabled: Boolean, val cooldownUntil: Long = 0)

data class ProviderSnapshot(
    val id: Long,
    val type: ProviderType,
    val name: String,
    val slug: String,
    val baseUrl: String,
    val enabled: Boolean,
    val priority: Int,
    val defaultModel: String?,
    val keys: List<KeySnapshot>,
    /** Enabled model ids for this provider. */
    val models: List<String>,
) {
    val usableKeys: List<KeySnapshot> get() = keys.filter { it.enabled }
    val isUsable: Boolean get() = enabled && (usableKeys.isNotEmpty() || !type.requiresKey)
}

data class RouteTarget(val providerId: Long, val modelId: String)

data class RouteSnapshot(val alias: String, val targets: List<RouteTarget>)

data class GatewayConfig(
    val strategy: RoutingStrategy = RoutingStrategy.PRIORITY,
    val fallbackEnabled: Boolean = true,
    val crossModelFallback: Boolean = true,
    val maxAttempts: Int = 4,
)

data class GatewaySnapshot(
    val providers: List<ProviderSnapshot>,
    val routes: List<RouteSnapshot>,
    val config: GatewayConfig,
    /** Average successful latency in ms keyed by "providerId:modelId". */
    val latency: Map<String, Long> = emptyMap(),
)

data class Candidate(val provider: ProviderSnapshot, val model: String) {
    val key: String get() = "${provider.id}:$model"
    val publicId: String get() = "${provider.slug}/$model"
}

data class Attempt(
    val providerName: String,
    val model: String,
    val keyLabel: String?,
    val statusCode: Int?,
    val error: String?,
    val latencyMs: Long,
) {
    val ok: Boolean get() = error == null
}

/** Everything the gateway records about one request. Contains no prompt text and no key material. */
data class RequestOutcome(
    val timestamp: Long,
    val source: String,
    val endpoint: String,
    val requestedModel: String,
    val providerId: Long?,
    val providerName: String?,
    val providerType: String?,
    val model: String?,
    val keyId: Long?,
    val statusCode: Int,
    val success: Boolean,
    val latencyMs: Long,
    val usage: TokenUsage,
    val attempts: List<Attempt>,
    val error: String?,
    val stream: Boolean,
) {
    val fallbackPath: String
        get() = attempts.joinToString(" → ") { a ->
            if (a.ok) "${a.providerName}/${a.model} ✓" else "${a.providerName}/${a.model} ✗${a.statusCode?.let { " $it" } ?: ""}"
        }
}

/** Storage boundary for the gateway; implemented with Room + Android Keystore in the app. */
interface GatewayStore {
    suspend fun snapshot(): GatewaySnapshot
    /** Returns the decrypted key, or null if it no longer exists. Never logged. */
    suspend fun decryptKey(keyId: Long): String?
    suspend fun record(outcome: RequestOutcome)
    suspend fun markKeyFailure(keyId: Long, kind: FailureKind, message: String, cooldownUntil: Long)
    suspend fun markKeySuccess(keyId: Long, at: Long)
}
