package com.avatarsdev.freellmapi

import android.content.Context
import com.avatarsdev.freellmapi.core.gateway.GatewayEngine
import com.avatarsdev.freellmapi.core.net.HttpEngine
import com.avatarsdev.freellmapi.core.provider.AdapterRegistry
import com.avatarsdev.freellmapi.data.ActivityRepository
import com.avatarsdev.freellmapi.data.ProviderRepository
import com.avatarsdev.freellmapi.data.RoomGatewayStore
import com.avatarsdev.freellmapi.data.SettingsRepository
import com.avatarsdev.freellmapi.data.db.AppDatabase
import com.avatarsdev.freellmapi.security.KeystoreCipher
import com.avatarsdev.freellmapi.security.SecretCipher
import com.avatarsdev.freellmapi.service.ServerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container, created once in [FreeLlmApp]. */
class AppContainer(context: Context, inMemoryDb: Boolean = false) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cipher: SecretCipher = KeystoreCipher()
    val db: AppDatabase = AppDatabase.create(appContext, inMemoryDb)
    val settings = SettingsRepository(appContext, cipher)

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var cachedTimeout = -1
    private var cachedClient: OkHttpClient = baseClient

    @Synchronized
    private fun client(): OkHttpClient {
        val t = settings.current.requestTimeoutSec
        if (t != cachedTimeout) {
            cachedClient = baseClient.newBuilder()
                .readTimeout(t.toLong(), TimeUnit.SECONDS)
                .writeTimeout(t.toLong(), TimeUnit.SECONDS)
                .build()
            cachedTimeout = t
        }
        return cachedClient
    }

    val http = HttpEngine { client() }
    val adapters = AdapterRegistry(http)
    val store = RoomGatewayStore(db, cipher, settings)
    val engine = GatewayEngine(store, adapters::forType)
    val providers = ProviderRepository(db, cipher, adapters)
    val activity = ActivityRepository(db)
    val server = ServerController(appContext, engine, store, settings)
}
