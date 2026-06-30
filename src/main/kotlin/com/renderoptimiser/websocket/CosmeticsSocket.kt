package com.renderoptimiser.websocket

import com.renderoptimiser.RenderOptimiser.logger
import com.renderoptimiser.RenderOptimiser.mc
import com.renderoptimiser.features.impl.visual.Cosmetics
import com.renderoptimiser.ui.notification.NotificationManager
import com.renderoptimiser.utils.ThreadUtils
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for the Cosmetics feature, built on the JDK's java.net.http.WebSocket
 * (no extra dependency — ktor is deliberately NOT bundled by this mod).
 *
 * Lifecycle: [start]/[stop] follow the feature's enable state; on connect we identify with a
 * "hello" packet (session name + UUID from [mc]'s User — available before any world is joined),
 * and the server replies/broadcasts full "cosmetics" maps. Dropped connections retry every 30s
 * while the feature stays enabled. All incoming payloads are marshalled to the MC thread.
 */
object CosmeticsSocket {
    private const val RETRY_DELAY_MS = 30_000L

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Tweaky-Cosmetics-WS").apply { isDaemon = true } }
    private val http: HttpClient = HttpClient.newBuilder().executor(executor).build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var wanted = false
    private val connecting = AtomicBoolean(false)

    val connected get() = socket != null

    fun start() {
        wanted = true
        connect()
    }

    fun stop() {
        wanted = false
        runCatching { socket?.sendClose(WebSocket.NORMAL_CLOSURE, "bye") }
        socket = null
    }

    /** Drops any current connection and connects again immediately (Reconnect button / address change). */
    fun reconnect() {
        runCatching { socket?.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect") }
        socket = null
        wanted = true
        connect()
    }

    /** Sends a JSON packet; returns false when not connected (caller decides how to surface that). */
    fun send(json: JsonObject): Boolean {
        val ws = socket ?: return false
        return runCatching { ws.sendText(json.toString(), true) }.isSuccess
    }

    private fun connect() {
        if (! wanted || ! connecting.compareAndSet(false, true)) return

        val url = Cosmetics.serverUrl.value.trim()
        val uri = runCatching { URI.create(url) }.getOrNull()
        if (uri == null || (uri.scheme != "ws" && uri.scheme != "wss")) {
            connecting.set(false)
            ThreadUtils.runOnMcThread { NotificationManager.error("Cosmetics", "Invalid server address: \"$url\"") }
            return
        }

        http.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(uri, SocketListener())
            .whenComplete { ws, err ->
                connecting.set(false)
                if (ws == null) {
                    logger.info("Cosmetics: connect to $url failed (${err?.cause?.message ?: err?.message}), retrying in ${RETRY_DELAY_MS / 1000}s")
                    scheduleRetry()
                }
                else {
                    socket = ws
                    Cosmetics.onSocketConnected()
                    sendHello()
                    ThreadUtils.runOnMcThread { NotificationManager.push("Cosmetics", "Connected to ${uri.host}") }
                }
            }
    }

    private fun sendHello() = runCatching {
        val user = mc.user
        send(JsonObject().apply {
            addProperty("type", "hello")
            addProperty("uuid", user.profileId.toString())
            addProperty("name", user.name)
        })
    }

    private fun scheduleRetry() {
        if (! wanted) return
        ThreadUtils.setTimeout(RETRY_DELAY_MS) { if (wanted && socket == null) connect() }
    }

    private class SocketListener: WebSocket.Listener {
        /** Text frames may arrive fragmented — accumulate until last=true. */
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val message = buffer.toString()
                buffer.setLength(0)
                ThreadUtils.runOnMcThread { Cosmetics.handleSocketMessage(message) }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            onDrop("closed ($statusCode)")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            onDrop(error.message ?: error.javaClass.simpleName)
        }

        private fun onDrop(why: String) {
            if (socket != null) logger.info("Cosmetics: disconnected — $why")
            socket = null
            scheduleRetry()
        }
    }
}
