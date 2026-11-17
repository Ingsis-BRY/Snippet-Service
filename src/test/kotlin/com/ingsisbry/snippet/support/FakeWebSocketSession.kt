package com.ingsisbry.snippet.support

import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketExtension
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.net.URI
import java.security.Principal

/** A server-side WebSocket session that records what is sent on it and how it was closed. */
class FakeWebSocketSession(
    private val attributes: MutableMap<String, Any> = mutableMapOf(),
) : WebSocketSession {
    val sent = mutableListOf<String>()
    var closeStatus: CloseStatus? = null
        private set

    override fun getId(): String = "session-1"

    override fun getUri(): URI? = null

    override fun getHandshakeHeaders(): HttpHeaders = HttpHeaders()

    override fun getAttributes(): MutableMap<String, Any> = attributes

    override fun getPrincipal(): Principal? = null

    override fun getLocalAddress(): InetSocketAddress? = null

    override fun getRemoteAddress(): InetSocketAddress? = null

    override fun getAcceptedProtocol(): String = "snippet-run.v1"

    override fun setTextMessageSizeLimit(messageSizeLimit: Int) = Unit

    override fun getTextMessageSizeLimit(): Int = Int.MAX_VALUE

    override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) = Unit

    override fun getBinaryMessageSizeLimit(): Int = Int.MAX_VALUE

    override fun getExtensions(): List<WebSocketExtension> = emptyList()

    override fun sendMessage(message: WebSocketMessage<*>) {
        check(isOpen) { "closed" }
        sent += (message as TextMessage).payload
    }

    override fun isOpen(): Boolean = closeStatus == null

    override fun close() = close(CloseStatus.NORMAL)

    override fun close(status: CloseStatus) {
        closeStatus = status
    }
}
