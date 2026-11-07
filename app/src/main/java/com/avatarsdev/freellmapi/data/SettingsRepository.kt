package com.avatarsdev.freellmapi.data

import android.content.Context
import android.content.SharedPreferences
import com.avatarsdev.freellmapi.core.gateway.GatewayConfig
import com.avatarsdev.freellmapi.core.gateway.RoutingStrategy
import com.avatarsdev.freellmapi.security.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import android.util.Base64

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val serverPort: Int = 8787,
    val allowLan: Boolean = false,
    val strategy: RoutingStrategy = RoutingStrategy.PRIORITY,
    val fallbackEnabled: Boolean = true,
    val crossModelFallback: Boolean = true,
    val maxAttempts: Int = 4,
    val requestTimeoutSec: Int = 60,
    val logRetentionDays: Int = 7,
) {
    val gatewayConfig: GatewayConfig
        get() = GatewayConfig(strategy, fallbackEnabled, crossModelFallback, maxAttempts)
}

/** Non-secret preferences in SharedPreferences; the gateway key is stored encrypted with the Keystore cipher. */
class SettingsRepository(context: Context, private val cipher: SecretCipher) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    private fun load() = AppSettings(
        themeMode = enumOr(prefs.getString("themeMode", null), ThemeMode.DARK),
        serverPort = prefs.getInt("serverPort", 8787),
        allowLan = prefs.getBoolean("allowLan", false),
        strategy = enumOr(prefs.getString("strategy", null), RoutingStrategy.PRIORITY),
        fallbackEnabled = prefs.getBoolean("fallbackEnabled", true),
        crossModelFallback = prefs.getBoolean("crossModelFallback", true),
        maxAttempts = prefs.getInt("maxAttempts", 4),
        requestTimeoutSec = prefs.getInt("requestTimeoutSec", 60),
        logRetentionDays = prefs.getInt("logRetentionDays", 7),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString("themeMode", next.themeMode.name)
            .putInt("serverPort", next.serverPort)
            .putBoolean("allowLan", next.allowLan)
            .putString("strategy", next.strategy.name)
            .putBoolean("fallbackEnabled", next.fallbackEnabled)
            .putBoolean("crossModelFallback", next.crossModelFallback)
            .putInt("maxAttempts", next.maxAttempts)
            .putInt("requestTimeoutSec", next.requestTimeoutSec)
            .putInt("logRetentionDays", next.logRetentionDays)
            .apply()
        _settings.value = next
    }

    @Synchronized
    fun gatewayKey(): String {
        prefs.getString(GATEWAY_KEY, null)?.let { enc ->
            try {
                return cipher.decrypt(enc)
            } catch (_: Exception) {
                // Keystore key lost (e.g. restored on another device): issue a new one.
            }
        }
        return regenerateGatewayKey()
    }

    @Synchronized
    fun regenerateGatewayKey(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key = "fllm_" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs.edit().putString(GATEWAY_KEY, cipher.encrypt(key)).apply()
        return key
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    companion object {
        private const val GATEWAY_KEY = "gateway_key_enc"
    }
}
