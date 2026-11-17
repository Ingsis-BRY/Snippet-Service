package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.run.InteractiveRunner
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import jakarta.websocket.ContainerProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs snippets on the engine's `/run/interactive` WebSocket, through the engine proxy, one engine socket per
 * [RunSession]. Like the REST client, it never sends the user's token (nor an `Origin`).
 */
@Component
class EngineInteractiveRunner(
    @Value("\${engine.url}")
    engineUrl: String,
    private val json: JsonMapper,
) : InteractiveRunner {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val target: URI = URI.create(toWebSocketUrl(engineUrl) + RUN_PATH)
    private val client =
        StandardWebSocketClient(
            ContainerProvider.getWebSocketContainer().apply {
                // a `done` frame carries every output line of the run, up to about 1 MB
                defaultMaxTextMessageBufferSize = MAX_FRAME_BYTES
            },
        )

    override fun open(
        language: String,
        languageVersion: String,
        code: String,
        listener: RunListener,
    ): Result<SnippetError, RunSession> {
        val session = connect(language, listener) ?: connect(language, listener)
        return if (session == null) {
            Result.Failure(LanguageServiceUnavailable("could not connect to the engine"))
        } else {
            Result.Success(session.withCode(code, languageVersion))
        }
    }

    /** One connection attempt; null when it failed, which is logged. */
    private fun connect(
        language: String,
        listener: RunListener,
    ): EngineConnection? {
        val handler = EngineSocketHandler(listener)
        val headers = WebSocketHttpHeaders().apply { set(LANGUAGE_HEADER, language) }
        val future = client.execute(handler, headers, target)
        return try {
            val session = future.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val decorated = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES)
            EngineConnection(decorated, handler)
        } catch (exception: ExecutionException) {
            logger.warn("Could not connect to the engine at {}: {}", target, exception.cause?.message)
            null
        } catch (exception: TimeoutException) {
            future.cancel(true)
            logger.warn("Connecting to the engine at {} timed out: {}", target, exception.message)
            null
        } catch (exception: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            logger.warn("Interrupted while connecting to the engine: {}", exception.message)
            null
        }
    }

    private inner class EngineConnection(
        private val session: WebSocketSession,
        private val handler: EngineSocketHandler,
    ) {
        fun withCode(
            code: String,
            version: String,
        ): RunSession =
            object : RunSession {
                override fun start() = send(EngineRunMessages.start(code, version))

                override fun startTest(test: TestSpec) = send(EngineRunMessages.startTest(code, version, test))

                override fun input(value: String) = send(EngineRunMessages.input(value))

                override fun close() {
                    handler.closedByUs.set(true)
                    try {
                        session.close(CloseStatus.NORMAL)
                    } catch (exception: IOException) {
                        logger.debug("Closing the engine socket failed: {}", exception.message)
                    }
                }
            }

        /** A failed send means the socket is gone; the listener hears about it through [RunListener.onClosed]. */
        private fun send(message: Map<String, Any>) {
            try {
                session.sendMessage(TextMessage(json.writeValueAsString(message)))
            } catch (exception: IOException) {
                logger.warn("Sending to the engine failed: {}", exception.message)
            } catch (exception: IllegalStateException) {
                logger.warn("Sending to the engine failed: {}", exception.message)
            }
        }
    }

    private inner class EngineSocketHandler(
        private val listener: RunListener,
    ) : TextWebSocketHandler() {
        val closedByUs = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun handleTextMessage(
            session: WebSocketSession,
            message: TextMessage,
        ) {
            val event =
                try {
                    EngineRunMessages.parse(json, message.payload)
                } catch (exception: JacksonException) {
                    logger.warn("Dropping an engine message that is not valid JSON: {}", exception.originalMessage)
                    return
                }
            if (event == null) {
                logger.warn("Dropping an engine message of an unknown type")
            } else {
                listener.onEvent(event)
            }
        }

        override fun handleTransportError(
            session: WebSocketSession,
            exception: Throwable,
        ) {
            logger.warn("Engine socket failed: {}", exception.message)
            if (session.isOpen) {
                session.close(CloseStatus.SERVER_ERROR)
            }
        }

        override fun afterConnectionClosed(
            session: WebSocketSession,
            status: CloseStatus,
        ) {
            if (!closed.compareAndSet(false, true)) return
            if (closedByUs.get()) {
                listener.onClosed(null)
            } else {
                logger.warn("The engine closed the socket unexpectedly: {}", status)
                listener.onClosed(LanguageServiceUnavailable("engine connection closed: ${status.code}"))
            }
        }
    }

    private companion object {
        const val RUN_PATH = "/run/interactive"
        const val LANGUAGE_HEADER = "X-Language"
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val SEND_TIME_LIMIT_MS = 10_000
        const val SEND_BUFFER_BYTES = 4 * 1024 * 1024
        const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    }
}

/** `http://host` → `ws://host`, `https://host` → `wss://host`, without a trailing slash. */
internal fun toWebSocketUrl(httpUrl: String): String {
    val trimmed = httpUrl.trimEnd('/')
    return when {
        trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
        else -> trimmed
    }
}
