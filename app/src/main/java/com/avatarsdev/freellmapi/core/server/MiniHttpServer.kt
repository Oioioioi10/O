package com.avatarsdev.freellmapi.core.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** Header names are lower-case. */
    val headers: Map<String, String>,
    val body: ByteArray,
    val remote: InetAddress,
) {
    fun header(name: String): String? = headers[name.lowercase()]
    val bodyText: String get() = String(body, Charsets.UTF_8)
}

sealed interface HttpResponse {
    val status: Int
    val headers: Map<String, String>

    class Fixed(
        override val status: Int,
        val contentType: String,
        val body: ByteArray,
        override val headers: Map<String, String> = emptyMap(),
    ) : HttpResponse

    /** Server-sent events. [writer] writes raw chunks and flushes them immediately. */
    class Stream(
        override val status: Int,
        override val headers: Map<String, String> = emptyMap(),
        val writer: suspend (send: suspend (String) -> Unit) -> Unit,
    ) : HttpResponse

    companion object {
        fun json(status: Int, json: String, headers: Map<String, String> = emptyMap()) =
            Fixed(status, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8), headers)
    }
}

class HttpParseException(val status: Int, message: String) : Exception(message)

/**
 * Minimal HTTP/1.1 server on a plain [ServerSocket]: one request per connection, `Connection: close`.
 * Enough for OpenAI-compatible clients (curl, OpenAI SDKs, LangChain…) and small enough to audit.
 */
class MiniHttpServer(
    private val handler: suspend (HttpRequest) -> HttpResponse,
    private val maxBodyBytes: Int = 8 * 1024 * 1024,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false
    val boundPort: Int get() = serverSocket?.localPort ?: -1

    @Synchronized
    fun start(host: String, port: Int) {
        check(!isRunning) { "Server already running" }
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName(host), port), 64)
        serverSocket = socket
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
        scope = s
        s.launch {
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: IOException) {
                    continue
                }
                launch { serve(client) }
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    private suspend fun serve(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            s.tcpNoDelay = true
            val input = BufferedInputStream(s.getInputStream())
            val output = s.getOutputStream()
            val response = try {
                val request = parse(input, s.inetAddress)
                handler(request)
            } catch (e: HttpParseException) {
                HttpResponse.json(e.status, """{"error":{"message":"${e.message}","type":"invalid_request_error"}}""")
            } catch (e: IOException) {
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HttpResponse.json(500, """{"error":{"message":"Internal gateway error","type":"server_error"}}""")
            }
            try {
                write(output, response)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // client went away or the stream failed mid-way; the connection is closed below
            }
        }
    }

    internal fun parse(input: InputStream, remote: InetAddress): HttpRequest = parseRequest(input, remote, maxBodyBytes)

    private suspend fun write(out: OutputStream, response: HttpResponse) {
        when (response) {
            is HttpResponse.Fixed -> {
                val head = buildString {
                    append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
                    append("Content-Type: ").append(response.contentType).append("\r\n")
                    append("Content-Length: ").append(response.body.size).append("\r\n")
                    commonHeaders(response.headers)
                    append("\r\n")
                }
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(response.body)
                out.flush()
            }
            is HttpResponse.Stream -> {
                val head = buildString {
                    append("HTTP/1.1 ").append(response.status).append(' ').append(reason(response.status)).append("\r\n")
                    append("Content-Type: text/event-stream; charset=utf-8\r\n")
                    append("Cache-Control: no-cache\r\n")
                    commonHeaders(response.headers)
                    append("\r\n")
                }
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.flush()
                response.writer { chunk ->
                    out.write(chunk.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        }
    }

    private fun StringBuilder.commonHeaders(extra: Map<String, String>) {
        append("Connection: close\r\n")
        append("X-Content-Type-Options: nosniff\r\n")
        for ((k, v) in extra) {
            if (k.any { it == '\r' || it == '\n' } || v.any { it == '\r' || it == '\n' }) continue
            append(k).append(": ").append(v).append("\r\n")
        }
    }

    companion object {
        private const val MAX_HEADER_BYTES = 32 * 1024

        fun parseRequest(input: InputStream, remote: InetAddress, maxBodyBytes: Int): HttpRequest {
            val headBytes = ByteArrayOutputStream()
            var matched = 0
            val terminator = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (matched < 4) {
                val b = input.read()
                if (b == -1) throw IOException("Connection closed before headers")
                headBytes.write(b)
                if (headBytes.size() > MAX_HEADER_BYTES) throw HttpParseException(431, "Headers too large")
                matched = if (b.toByte() == terminator[matched]) matched + 1 else if (b.toByte() == terminator[0]) 1 else 0
            }
            val head = headBytes.toString(Charsets.ISO_8859_1.name()).trimEnd('\r', '\n')
            val lines = head.split("\r\n")
            val requestLine = lines.first().split(' ')
            if (requestLine.size < 3) throw HttpParseException(400, "Malformed request line")
            val method = requestLine[0].uppercase()
            val target = requestLine[1]
            val headers = LinkedHashMap<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
            if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
                throw HttpParseException(411, "Chunked request bodies are not supported; send Content-Length")
            }
            val length = headers["content-length"]?.toLongOrNull() ?: 0L
            if (length < 0 || length > maxBodyBytes) throw HttpParseException(413, "Request body too large")
            val body = ByteArray(length.toInt())
            var read = 0
            while (read < body.size) {
                val n = input.read(body, read, body.size - read)
                if (n == -1) throw IOException("Connection closed while reading body")
                read += n
            }
            val qIndex = target.indexOf('?')
            val rawPath = if (qIndex >= 0) target.substring(0, qIndex) else target
            val query = if (qIndex >= 0) parseQuery(target.substring(qIndex + 1)) else emptyMap()
            return HttpRequest(method, rawPath, query, headers, body, remote)
        }

        private fun parseQuery(q: String): Map<String, String> = q.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val i = pair.indexOf('=')
            val k = if (i >= 0) pair.substring(0, i) else pair
            val v = if (i >= 0) pair.substring(i + 1) else ""
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

        fun reason(status: Int): String = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Status"
        }
    }
}
