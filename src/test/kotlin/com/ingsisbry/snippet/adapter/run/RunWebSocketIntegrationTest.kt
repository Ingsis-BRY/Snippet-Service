package com.ingsisbry.snippet.adapter.run

import com.ingsisbry.snippet.adapter.language.EngineInteractiveRunner
import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.run.StartRun
import com.ingsisbry.snippet.application.command.run.StartRunUseCase
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.tests.ListSnippetTests
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The run socket end to end, on a real server: the browser's token in `Sec-WebSocket-Protocol`, the handshake
 * authorization, and the relay to an engine. The fake engine is a WebSocket on the same server, reached through
 * the real [EngineInteractiveRunner].
 */
@SpringBootTest(
    classes = [RunWebSocketIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "AUTH0_DOMAIN=auth0.test.invalid",
        "AUTH0_AUDIENCE=https://snippet-searcher-api",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration," +
            // embedded-azurite's container shutdown hook; this test runs no container
            "com.playtika.testcontainer.common.spring.EmbeddedContainersShutdownAutoConfiguration",
    ],
)
class RunWebSocketIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var engine: FakeEngine

    @BeforeEach
    fun reset() = engine.reset()

    @Test
    fun `a handshake without a token is refused with 401 before the upgrade`() {
        val failure = assertThrows<ExecutionException> { connect(token = null) }

        assertTrue(
            failure.cause
                ?.message
                .orEmpty()
                .contains("401"),
            "got ${failure.cause}",
        )
    }

    @Test
    fun `a handshake with an invalid token is refused with 401`() {
        val failure = assertThrows<ExecutionException> { connect(token = "forged") }

        assertTrue(
            failure.cause
                ?.message
                .orEmpty()
                .contains("401"),
            "got ${failure.cause}",
        )
    }

    @Test
    fun `a user without READ gets a 403 error frame and close 4403`() {
        val client = connect(token = STRANGER_TOKEN)

        assertEquals(
            """{"type":"error","status":403,"title":"AccessDenied",""" +
                """"detail":"You are not allowed to perform this action on snippet $SNIPPET_ID"}""",
            client.next(),
        )
        assertEquals(CloseStatus(4403, "AccessDenied"), client.closed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(engine.starts.isEmpty())
    }

    @Test
    fun `an interactive run streams output, asks for input and ends with done`() {
        val client = connect(token = OWNER_TOKEN)
        assertEquals(RunProtocol.SUBPROTOCOL, client.session.acceptedProtocol)

        client.send("""{"type":"start"}""")
        assertEquals("""{"type":"output","line":"Hello"}""", client.next())
        assertEquals("""{"type":"awaiting-input","prompt":"Name:"}""", client.next())

        client.send("""{"type":"input","value":"Ana"}""")
        assertEquals("""{"type":"output","line":"Hi Ana"}""", client.next())
        assertEquals("""{"type":"done","outputs":["Hello","Hi Ana"],"error":null,"passed":null}""", client.next())

        assertEquals(listOf("printscript"), engine.languages)
        assertEquals(
            listOf("""{"type":"start","mode":"interactive","code":"$CODE_JSON","version":"1.1"}"""),
            engine.starts,
        )
        assertEquals(emptyList<String>(), engine.authorizations)
    }

    @Test
    fun `closing the browser socket closes the engine socket`() {
        val client = connect(token = OWNER_TOKEN)
        client.send("""{"type":"start"}""")
        assertEquals("""{"type":"output","line":"Hello"}""", client.next())

        client.session.close(CloseStatus.NORMAL)

        assertTrue(engine.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun connect(token: String?): BrowserClient {
        val headers = WebSocketHttpHeaders()
        headers.secWebSocketProtocol =
            listOfNotNull(RunProtocol.SUBPROTOCOL, token?.let { RunProtocol.BEARER_PREFIX + it })
        val browser = BrowserClient()
        browser.session =
            StandardWebSocketClient()
                .execute(browser, headers, URI("ws://localhost:$port/snippets/$SNIPPET_ID/run"))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return browser
    }

    private class BrowserClient : TextWebSocketHandler() {
        lateinit var session: WebSocketSession
        private val messages = LinkedBlockingQueue<String>()
        val closed = CompletableFuture<CloseStatus>()

        fun send(payload: String) = session.sendMessage(TextMessage(payload))

        fun next(): String {
            val message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertNotNull(message, "no message from the server")
            return message
        }

        override fun handleTextMessage(
            session: WebSocketSession,
            message: TextMessage,
        ) {
            messages += message.payload
        }

        override fun afterConnectionClosed(
            session: WebSocketSession,
            status: CloseStatus,
        ) {
            closed.complete(status)
        }
    }

    /** Answers like the engine: greets, asks for a name, then says hi and finishes. */
    class FakeEngine : TextWebSocketHandler() {
        private val json = JsonMapper.builder().build()
        val languages = CopyOnWriteArrayList<String>()
        val authorizations = CopyOnWriteArrayList<String>()
        val starts = CopyOnWriteArrayList<String>()
        var closed = CountDownLatch(1)
            private set

        fun reset() {
            languages.clear()
            authorizations.clear()
            starts.clear()
            closed = CountDownLatch(1)
        }

        override fun afterConnectionEstablished(session: WebSocketSession) {
            languages += session.handshakeHeaders.getFirst("X-Language").orEmpty()
            session.handshakeHeaders["Authorization"]?.let { authorizations += it }
        }

        override fun handleTextMessage(
            session: WebSocketSession,
            message: TextMessage,
        ) {
            val request = json.readTree(message.payload)
            when (request.get("type").asString()) {
                "start" -> {
                    starts += message.payload
                    session.sendMessage(TextMessage("""{"type":"output","line":"Hello"}"""))
                    session.sendMessage(TextMessage("""{"type":"awaiting-input","prompt":"Name:"}"""))
                }
                "input" -> {
                    val name = request.get("value").asString()
                    session.sendMessage(TextMessage("""{"type":"output","line":"Hi $name"}"""))
                    session.sendMessage(
                        TextMessage("""{"type":"done","outputs":["Hello","Hi $name"],"error":null,"passed":null}"""),
                    )
                }
            }
        }

        override fun afterConnectionClosed(
            session: WebSocketSession,
            status: CloseStatus,
        ) {
            closed.countDown()
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(
        SecurityConfiguration::class,
        RunWebSocketConfiguration::class,
        SnippetRunWebSocketHandler::class,
        SnippetRunHandshakeInterceptor::class,
        SnippetErrorMapper::class,
    )
    class TestApplication : WebSocketConfigurer {
        private val engine = FakeEngine()

        @Bean
        fun fakeEngine(): FakeEngine = engine

        override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
            registry.addHandler(engine, "/run/interactive")
        }

        /** The real engine is unauthenticated and internal; the fake one must be reachable without a token. */
        @Bean
        @Order(0)
        fun fakeEngineSecurity(http: HttpSecurity): SecurityFilterChain =
            http
                .securityMatcher("/run/interactive")
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .csrf { it.disable() }
                .build()

        @Bean
        fun jwtDecoder(): JwtDecoder =
            JwtDecoder { token ->
                when (token) {
                    OWNER_TOKEN -> jwt(token, OWNER)
                    STRANGER_TOKEN -> jwt(token, STRANGER)
                    else -> throw BadJwtException("Invalid token")
                }
            }

        @Bean
        fun getSnippet(): GetSnippet =
            GetSnippet { query ->
                if (query.snippetId == SNIPPET_ID && query.userId == OWNER) {
                    Result.Success(SNIPPET)
                } else {
                    Result.Failure(AccessDenied(query.snippetId, query.userId))
                }
            }

        @Bean
        fun listSnippetTests(): ListSnippetTests = ListSnippetTests { _, _ -> Result.Success(emptyList()) }

        /** The engine URL is this server, whose port is only known once it started. */
        @Bean
        fun startRun(
            environment: Environment,
            json: JsonMapper,
        ): StartRun {
            val runner by lazy {
                EngineInteractiveRunner("http://localhost:${environment.getProperty("local.server.port")}", json)
            }
            return StartRun { snippet, listener -> StartRunUseCase(runner).execute(snippet, listener) }
        }

        private fun jwt(
            token: String,
            subject: String,
        ): Jwt =
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .subject(subject)
                .claim("scope", "read:snippets")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
    }

    private companion object {
        const val OWNER = "auth0|franco"
        const val STRANGER = "auth0|stranger"
        const val OWNER_TOKEN = "owner-token"
        const val STRANGER_TOKEN = "stranger-token"
        const val TIMEOUT_SECONDS = 5L
        const val CODE = "println(\"Hello\"); let name: string = readInput(\"Name:\"); println(\"Hi \" + name);"
        const val CODE_JSON =
            """println(\"Hello\"); let name: string = readInput(\"Name:\"); println(\"Hi \" + name);"""
        val SNIPPET_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val SNIPPET =
            Snippet.create(
                id = SNIPPET_ID,
                name = "hello",
                description = "",
                language = "printscript",
                languageVersion = "1.1",
                owner = OWNER,
                content = CODE,
                versionId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                createdAt = Instant.parse("2026-11-17T00:00:00Z"),
            )
    }
}
