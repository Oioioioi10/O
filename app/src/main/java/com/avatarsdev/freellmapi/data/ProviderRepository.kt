package com.avatarsdev.freellmapi.data

import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.provider.AdapterRegistry
import com.avatarsdev.freellmapi.core.provider.ProviderEndpoint
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.core.security.Redactor
import com.avatarsdev.freellmapi.data.db.ApiKeyEntity
import com.avatarsdev.freellmapi.data.db.AppDatabase
import com.avatarsdev.freellmapi.data.db.ModelEntity
import com.avatarsdev.freellmapi.data.db.ProviderEntity
import com.avatarsdev.freellmapi.security.SecretCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

data class ConnectionTest(val ok: Boolean, val message: String, val latencyMs: Long, val modelCount: Int = 0)

class ProviderRepository(
    private val db: AppDatabase,
    private val cipher: SecretCipher,
    private val adapters: AdapterRegistry,
) {
    val providers: Flow<List<ProviderEntity>> = db.providers().observeAll()
    val keys: Flow<List<ApiKeyEntity>> = db.keys().observeAll()
    val models: Flow<List<ModelEntity>> = db.models().observeAll()

    fun provider(id: Long): Flow<ProviderEntity?> = db.providers().observe(id)
    fun keysFor(id: Long): Flow<List<ApiKeyEntity>> = db.keys().observeForProvider(id)
    fun modelsFor(id: Long): Flow<List<ModelEntity>> = db.models().observeForProvider(id)

    suspend fun addProvider(type: ProviderType, name: String, baseUrl: String, apiKey: String?): Long {
        val slugs = db.providers().slugs().toSet()
        var slug = type.id
        var n = 2
        while (slug in slugs) slug = "${type.id}-${n++}"
        val id = db.providers().insert(
            ProviderEntity(
                type = type.id,
                name = name.trim().ifEmpty { type.displayName },
                slug = slug,
                baseUrl = normalizeUrl(baseUrl.ifBlank { type.defaultBaseUrl }),
                priority = db.providers().maxPriority() + 1,
                defaultModel = type.builtinModels.firstOrNull(),
            ),
        )
        if (type.builtinModels.isNotEmpty()) {
            db.models().upsertAll(type.builtinModels.map { ModelEntity(providerId = id, modelId = it, displayName = it, source = "builtin", isFree = it == "openrouter/free") })
        }
        if (!apiKey.isNullOrBlank()) addKey(id, "Key 1", apiKey)
        return id
    }

    suspend fun updateProvider(provider: ProviderEntity) = db.providers().update(provider.copy(baseUrl = normalizeUrl(provider.baseUrl)))

    suspend fun deleteProvider(provider: ProviderEntity) = db.providers().delete(provider)

    /** Moves a provider one step up or down in the priority order. */
    suspend fun move(providerId: Long, up: Boolean) {
        val list = db.providers().getAll().toMutableList()
        val i = list.indexOfFirst { it.id == providerId }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        db.providers().updateAll(list.mapIndexed { idx, p -> p.copy(priority = idx) })
    }

    suspend fun addKey(providerId: Long, label: String, plainKey: String): Long {
        val key = plainKey.trim()
        require(key.isNotEmpty()) { "Key is empty" }
        val existing = db.keys().getAll().count { it.providerId == providerId }
        return db.keys().insert(
            ApiKeyEntity(
                providerId = providerId,
                label = label.trim().ifEmpty { "Key ${existing + 1}" },
                encryptedKey = cipher.encrypt(key),
                maskedKey = Redactor.mask(key),
            ),
        )
    }

    suspend fun deleteKey(id: Long) = db.keys().delete(id)
    suspend fun setKeyEnabled(id: Long, enabled: Boolean) = db.keys().setEnabled(id, enabled)
    suspend fun clearCooldowns() = db.keys().clearCooldowns()

    suspend fun setModelEnabled(id: Long, enabled: Boolean) = db.models().setEnabled(id, enabled)
    suspend fun deleteModel(id: Long) = db.models().delete(id)

    suspend fun addManualModel(providerId: Long, modelId: String) {
        val id = modelId.trim()
        require(id.isNotEmpty()) { "Model id is empty" }
        db.models().upsertAll(listOf(ModelEntity(providerId = providerId, modelId = id, displayName = id, source = "manual")))
    }

    suspend fun setDefaultModel(providerId: Long, modelId: String?) {
        val p = db.providers().get(providerId) ?: return
        db.providers().update(p.copy(defaultModel = modelId))
    }

    /** Lists the provider's live models with its first working key and stores them. Returns the number of models. */
    suspend fun syncModels(providerId: Long): ConnectionTest {
        val p = db.providers().get(providerId) ?: return ConnectionTest(false, "Provider not found", 0)
        val type = ProviderType.fromId(p.type)
        val keys = db.keys().enabledForProvider(providerId)
        if (keys.isEmpty() && type.requiresKey) return ConnectionTest(false, "Add an API key first", 0)
        var lastError = "No usable key"
        val candidates: List<ApiKeyEntity?> = if (keys.isEmpty()) listOf(null) else keys
        for (k in candidates) {
            val plain = k?.let { runCatching { cipher.decrypt(it.encryptedKey) }.getOrNull() } ?: if (k == null) "" else continue
            val started = System.currentTimeMillis()
            try {
                val remote = adapters.forType(type).listModels(ProviderEndpoint(type, p.baseUrl, plain))
                val now = System.currentTimeMillis()
                if (remote.isNotEmpty()) {
                    db.models().upsertAll(
                        remote.map {
                            ModelEntity(
                                providerId = providerId,
                                modelId = it.id,
                                displayName = it.displayName,
                                contextWindow = it.contextWindow,
                                isFree = it.isFree,
                                // OpenRouter lists hundreds of paid models; only free ones start enabled.
                                enabled = type != ProviderType.OPENROUTER || it.isFree,
                                source = "remote",
                                updatedAt = now,
                            )
                        },
                    )
                    val remoteIds = remote.map { it.id }.toSet()
                    // Built-in suggestions the provider does not actually serve are switched off.
                    db.models().forProvider(providerId)
                        .filter { it.source == "builtin" && it.modelId !in remoteIds }
                        .forEach { db.models().setEnabled(it.id, false) }
                    if (p.defaultModel == null || p.defaultModel !in remoteIds) {
                        val preferred = type.builtinModels.firstOrNull { it in remoteIds }
                            ?: remote.firstOrNull { it.isFree }?.id
                            ?: remote.first().id
                        db.providers().update(p.copy(defaultModel = preferred))
                    }
                }
                k?.let { db.keys().markSuccess(it.id, now) }
                return ConnectionTest(true, "Found ${remote.size} models", now - started, remote.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                lastError = e.message ?: "Error"
                k?.let { db.keys().markFailure(it.id, 0, Redactor.redact(lastError).take(300)) }
            } catch (e: Exception) {
                lastError = Redactor.redact(e.message ?: e.javaClass.simpleName)
            }
        }
        return ConnectionTest(false, lastError, 0)
    }

    companion object {
        fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
    }
}
