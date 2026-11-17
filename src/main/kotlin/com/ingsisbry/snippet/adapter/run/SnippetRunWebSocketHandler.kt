package com.ingsisbry.snippet.adapter.run

import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.run.StartRun
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.domain.Snippet
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.SessionLimitExceededException
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * `/snippets/{id}/run` (US#9, US#10): relays an interactive run, or a run of one of the snippet's tests, between
 * the browser and the engine.
 *
 * The browser only says `start` (naming a test in test mode) and answers prompts; the code is the snippet's current
 * version and the tests are the snippet's own, both loaded and authorized at handshake time. Test runs started
 * here are not stored. Errors that end the connection are sent as an `error` frame followed by a
 * close with code `4000 + status`. Only the snippet id and the user id are logged, never the token or the code.
 */
@Component
@Profile("!worker")
class SnippetRunWebSocketHandler(
    private val startRun: StartRun,
    private val errorMapper: SnippetErrorMapper,
    private val json: JsonMapper,
) : TextWebSocketHandler(),
    SubProtocolCapable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val connections = ConcurrentHashMap<String, RunConnection>()

    override fun getSubProtocols(): List<String> = listOf(RunProtocol.SUBPROTOCOL)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val connection =
            RunConnection(
                out = RunOutbox(ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT)),
                snippet = session.attributes[RunAttributes.SNIPPET] as? Snippet,
                tests = testsOf(session),
                snippetId = session.attributes[RunAttributes.SNIPPET_ID]?.toString(),
                userId = session.attributes[RunAttributes.USER_ID]?.toString(),
            )
        val error = session.attributes[RunAttributes.ERROR] as? SnippetError
        when {
            error != null -> connection.fail(error)
            connection.snippet == null -> connection.failUnexpectedly()
            else -> {
                connections[session.id] = connection
                logger.info("Run socket opened for snippet {} by {}", connection.snippetId, connection.userId)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val connection = connections[session.id] ?: return
        try {
            connection.handle(message.payload)
        } catch (exception: RuntimeException) {
            logger.error(
                "Run socket failed for snippet {} by {}",
                connection.snippetId,
                connection.userId,
                exception,
            )
            connection.failUnexpectedly()
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        connections.remove(session.id)?.let { connection ->
            logger.info(
                "Run socket closed ({}) for snippet {} by {}",
                status.code,
                connection.snippetId,
                connection.userId,
            )
            connection.close()
        }
    }

    private fun testsOf(session: WebSocketSession): List<SnippetTestView> =
        (session.attributes[RunAttributes.TESTS] as? List<*>).orEmpty().filterIsInstance<SnippetTestView>()

    /** One browser socket and, after the first `start`, its engine session. */
    private inner class RunConnection(
        private val out: RunOutbox,
        val snippet: Snippet?,
        private val tests: List<SnippetTestView>,
        val snippetId: String?,
        val userId: String?,
    ) : RunListener {
        private val lock = Any()

        @Volatile
        private var runSession: RunSession? = null
        private var closed = false

        fun handle(payload: String) {
            val request =
                try {
                    json.readValue(payload, ClientRunMessage::class.java)
                } catch (_: JacksonException) {
                    return out.reject(MALFORMED_MESSAGE, "The message is not valid JSON.")
                }
            when (request.type) {
                "start" -> start(request.mode, request.testId)
                "input" -> input(request.value)
                else -> out.reject(MALFORMED_MESSAGE, "'type' must be 'start' or 'input'.")
            }
        }

        private fun start(
            mode: String?,
            testId: String?,
        ) {
            when (mode) {
                null, INTERACTIVE_MODE -> (runSession ?: open())?.start()
                TEST_MODE -> startTest(testId)
                else -> out.reject(MALFORMED_MESSAGE, "'mode' must be 'interactive' or 'test'.")
            }
        }

        private fun startTest(testId: String?) {
            if (testId == null) {
                return out.reject(MALFORMED_MESSAGE, "'test' mode needs a 'testId'.")
            }
            val test =
                tests
                    .firstOrNull {
                        it.test.id
                            .toString()
                            .equals(testId, ignoreCase = true)
                    }?.test
            if (test == null) {
                return out.reject(UNKNOWN_TEST, "Test $testId does not belong to this snippet.")
            }
            (runSession ?: open())?.startTest(TestSpec(test.id.toString(), test.inputs, test.expectedOutputs))
        }

        private fun input(value: String?) {
            val session = runSession
            when {
                value == null -> out.reject(MALFORMED_MESSAGE, "'input' needs a 'value'.")
                session == null -> out.reject(NO_INTERACTIVE_RUN, "There is no run to answer; send 'start' first.")
                else -> session.input(value)
            }
        }

        /** Opens the engine session on the first `start`; null when it failed, which ends the connection. */
        private fun open(): RunSession? =
            synchronized(lock) {
                if (closed) return null
                startRun
                    .execute(checkNotNull(snippet), this)
                    .fold(
                        onSuccess = { session ->
                            runSession = session
                            session
                        },
                        onFailure = { error ->
                            fail(error)
                            null
                        },
                    )
            }

        override fun onEvent(event: RunEvent) = out.send(ServerRunMessages.of(event))

        override fun onClosed(error: SnippetError?) {
            if (error == null) return
            synchronized(lock) { runSession = null }
            fail(error)
        }

        /** Sends the error frame and closes with `4000 + status`, the error's name as the reason. */
        fun fail(error: SnippetError) {
            val response = errorMapper.toResponse(error)
            val title = error::class.simpleName ?: INTERNAL_ERROR
            logger.warn("Run socket for snippet {} by {} ends with {}", snippetId, userId, title)
            out.end(response.statusCode.value(), title, response.body?.detail.orEmpty())
        }

        fun failUnexpectedly() = out.end(INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "Unexpected error")

        /** The browser left: closing the engine socket cancels a run still in progress. */
        fun close() {
            val session =
                synchronized(lock) {
                    closed = true
                    runSession.also { runSession = null }
                }
            session?.close()
        }
    }

    /** Sends to one browser socket; a socket that is already gone is only logged. */
    private inner class RunOutbox(
        private val session: WebSocketSession,
    ) {
        fun send(message: Map<String, Any?>) {
            try {
                session.sendMessage(TextMessage(json.writeValueAsString(message)))
            } catch (exception: IOException) {
                logger.debug("Could not send to run socket {}: {}", session.id, exception.message)
            } catch (exception: SessionLimitExceededException) {
                logger.warn("Run socket {} is too slow and was closed: {}", session.id, exception.message)
            } catch (exception: IllegalStateException) {
                logger.debug("Run socket {} is already closed: {}", session.id, exception.message)
            }
        }

        fun reject(
            title: String,
            detail: String,
        ) = send(ServerRunMessages.rejected(title, detail))

        /** The `error` frame, then the close with code `4000 + status` and the title as the reason. */
        fun end(
            status: Int,
            title: String,
            detail: String,
        ) {
            send(ServerRunMessages.error(status, title, detail))
            try {
                session.close(CloseStatus(CLOSE_CODE_BASE + status, title))
            } catch (exception: IOException) {
                logger.debug("Could not close run socket {}: {}", session.id, exception.message)
            }
        }
    }

    private companion object {
        const val SEND_TIME_LIMIT_MS = 10_000
        const val SEND_BUFFER_LIMIT = 4 * 1024 * 1024
        const val CLOSE_CODE_BASE = 4000
        const val INTERNAL_SERVER_ERROR = 500
        const val INTERNAL_ERROR = "InternalError"
        const val INTERACTIVE_MODE = "interactive"
        const val TEST_MODE = "test"
        const val UNKNOWN_TEST = "UnknownTest"
        const val MALFORMED_MESSAGE = "MalformedMessage"
        const val NO_INTERACTIVE_RUN = "NoInteractiveRun"
    }
}
