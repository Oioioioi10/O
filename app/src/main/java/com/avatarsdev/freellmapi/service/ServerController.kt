package com.avatarsdev.freellmapi.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.avatarsdev.freellmapi.core.gateway.GatewayEngine
import com.avatarsdev.freellmapi.core.gateway.GatewayStore
import com.avatarsdev.freellmapi.core.server.GatewayApi
import com.avatarsdev.freellmapi.core.server.MiniHttpServer
import com.avatarsdev.freellmapi.core.server.ServerPolicy
import com.avatarsdev.freellmapi.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.BindException
import java.net.Inet4Address
import java.net.NetworkInterface

data class ServerState(
    val running: Boolean = false,
    val port: Int = 0,
    val lanEnabled: Boolean = false,
    val localUrl: String = "",
    val lanUrls: List<String> = emptyList(),
    val startedAt: Long = 0,
    val error: String? = null,
)

/** Owns the local API server. The foreground [GatewayService] keeps the process alive while it runs. */
class ServerController(
    private val context: Context,
    engine: GatewayEngine,
    store: GatewayStore,
    private val settings: SettingsRepository,
) {
    @Volatile
    private var gatewayKey: String = ""
    private val api = GatewayApi(engine, store, policy = { ServerPolicy(gatewayKey, settings.current.allowLan) })
    private var server: MiniHttpServer? = null

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    /** Starts the HTTP listener only (used by tests and by [start]). */
    @Synchronized
    fun startListener(port: Int = settings.current.serverPort): Result<Unit> {
        if (server?.isRunning == true) return Result.success(Unit)
        val cfg = settings.current
        gatewayKey = settings.gatewayKey()
        val host = if (cfg.allowLan) "0.0.0.0" else "127.0.0.1"
        return try {
            val srv = MiniHttpServer(api::handle)
            srv.start(host, port)
            server = srv
            val bound = srv.boundPort
            _state.value = ServerState(
                running = true,
                port = bound,
                lanEnabled = cfg.allowLan,
                localUrl = "http://127.0.0.1:$bound/v1",
                lanUrls = if (cfg.allowLan) lanAddresses().map { "http://$it:$bound/v1" } else emptyList(),
                startedAt = System.currentTimeMillis(),
            )
            Result.success(Unit)
        } catch (e: BindException) {
            _state.value = ServerState(error = "Port $port is already in use. Pick another port.")
            Result.failure(e)
        } catch (e: Exception) {
            _state.value = ServerState(error = "Could not start server: ${e.message}")
            Result.failure(e)
        }
    }

    @Synchronized
    fun stopListener() {
        server?.stop()
        server = null
        _state.value = ServerState()
    }

    fun start(): Result<Unit> {
        val result = startListener()
        if (result.isSuccess) {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
        }
        return result
    }

    fun stop() {
        stopListener()
        context.stopService(Intent(context, GatewayService::class.java))
    }

    /** Applies a changed port / LAN setting by restarting if needed. */
    fun restartIfRunning() {
        if (_state.value.running) {
            stopListener()
            start()
        }
    }

    /** Call after the gateway key was regenerated so the running server accepts only the new key. */
    fun reloadKey() {
        gatewayKey = settings.gatewayKey()
    }

    companion object {
        fun lanAddresses(): List<String> = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
