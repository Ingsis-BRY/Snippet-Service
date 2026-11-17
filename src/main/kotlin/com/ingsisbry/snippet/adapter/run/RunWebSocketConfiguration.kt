package com.ingsisbry.snippet.adapter.run

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean

@Configuration
@Profile("!worker")
@EnableWebSocket
class RunWebSocketConfiguration(
    private val handler: SnippetRunWebSocketHandler,
    private val handshakeInterceptor: SnippetRunHandshakeInterceptor,
) : WebSocketConfigurer {
    /**
     * Any origin: the browser authenticates with an explicit bearer token, never with a cookie, so a page on
     * another site can't open a socket as the user (no cross-site WebSocket hijacking).
     */
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(handler, RunProtocol.PATH_PATTERN)
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns("*")
    }

    @Bean
    fun webSocketContainer(): ServletServerContainerFactoryBean =
        ServletServerContainerFactoryBean().apply {
            // same as nginx's proxy_read_timeout for the run location; the engine gives up on input after 5 min
            setMaxSessionIdleTimeout(MAX_SESSION_IDLE_MS)
            // client messages are tiny: `start` carries no code
            setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES)
        }

    private companion object {
        const val MAX_SESSION_IDLE_MS = 360_000L
        const val MAX_TEXT_MESSAGE_BYTES = 65_536
    }
}
