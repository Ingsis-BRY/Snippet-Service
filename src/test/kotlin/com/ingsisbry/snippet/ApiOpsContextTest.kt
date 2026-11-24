package com.ingsisbry.snippet

import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.sun.net.httpserver.HttpServer
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The real API (default profile) on in-memory H2: the health endpoint Swarm probes, and the trace context carried
 * by the clients of the permission service and the engine. Both are pointed at a local HTTP server that records
 * the headers it receives. Redis is not contacted (Lettuce connects on first use), so its health check is off.
 */
@SpringBootTest(
    classes = [SnippetApplication::class],
    // a real server: the run socket's endpoint needs a WebSocket container
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:api-ops-context;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.health.redis.enabled=false",
        "AUTH0_DOMAIN=auth0.test.invalid",
        "AUTH0_AUDIENCE=https://snippet-searcher-api",
        "AUTH0_M2M_CLIENT_ID=test",
        "AUTH0_M2M_CLIENT_SECRET=test",
        "azure.storage.connection-string=" +
            "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=a2V5;" +
            "BlobEndpoint=http://127.0.0.1:1/devstoreaccount1;",
        "embedded.containers.enabled=false",
        "embedded.azurite.enabled=false",
    ],
)
@AutoConfigureMockMvc
class ApiOpsContextTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tracer: Tracer

    @Autowired
    private lateinit var permissionServiceClient: RestClient

    @Autowired
    @Qualifier("engineClient")
    private lateinit var engineClient: RestClient

    @MockitoBean
    private lateinit var contentStorage: SnippetContentStorage

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun reset() = traceparents.clear()

    @Test
    fun `health and its probes answer UP without a token, with no details`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            mockMvc.get(path).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components") { doesNotExist() }
                jsonPath("$.details") { doesNotExist() }
            }
        }
    }

    @Test
    fun `no other actuator endpoint is reachable`() {
        mockMvc.get("/actuator/env").andExpect { status { isUnauthorized() } }
        mockMvc.get("/actuator").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `calls to the permission service and the engine carry the current trace`() {
        val span = tracer.nextSpan().name("test").start()
        tracer.withSpan(span).use {
            permissionServiceClient
                .get()
                .uri("/permissions/check")
                .retrieve()
                .toBodilessEntity()
            engineClient
                .get()
                .uri("/versions")
                .retrieve()
                .toBodilessEntity()
        }
        span.end()

        assertEquals(2, traceparents.size)
        traceparents.forEach { header ->
            assertNotNull(header)
            // W3C: version-traceid-parentid-flags
            assertTrue(header!!.startsWith("00-${span.context().traceId()}-"), header)
        }
    }

    companion object {
        private val traceparents = CopyOnWriteArrayList<String?>()

        private val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    traceparents += exchange.requestHeaders.getFirst("traceparent")
                    exchange.sendResponseHeaders(NO_CONTENT, -1)
                    exchange.close()
                }
                start()
            }

        private const val NO_CONTENT = 204

        @JvmStatic
        @DynamicPropertySource
        fun downstreams(registry: DynamicPropertyRegistry) {
            val url = "http://127.0.0.1:${server.address.port}"
            registry.add("permission-service.url") { url }
            registry.add("engine.url") { url }
        }

        @JvmStatic
        @AfterAll
        fun stop() = server.stop(0)
    }
}
