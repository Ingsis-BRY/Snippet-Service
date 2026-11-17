package com.ingsisbry.snippet.adapter.run

import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.run.StartRun
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunIssue
import com.ingsisbry.snippet.application.port.out.run.RunListener
import com.ingsisbry.snippet.application.port.out.run.RunSession
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.support.FakeWebSocketSession
import com.ingsisbry.snippet.support.SAMPLE_SPAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class SnippetRunWebSocketHandlerTest {
    private val json = JsonMapper.builder().build()
    private val snippetId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val snippet =
        Snippet.create(
            id = snippetId,
            name = "hello",
            description = "",
            language = "printscript",
            languageVersion = "1.1",
            owner = USER,
            content = "println(readInput(\"Name:\"));",
            versionId = UUID.randomUUID(),
            createdAt = Instant.parse("2026-11-17T00:00:00Z"),
        )

    private val runSession = FakeRunSession()
    private var openResult: Result<SnippetError, RunSession> = Result.Success(runSession)
    private var listener: RunListener? = null
    private val opened = mutableListOf<Snippet>()
    private val startRun =
        StartRun { snippet, listener ->
            opened += snippet
            this.listener = listener
            openResult
        }
    private val handler = SnippetRunWebSocketHandler(startRun, SnippetErrorMapper(), json)

    @Test
    fun `no READ permission sends a 403 error frame and closes with 4403`() {
        val session = connect(error = AccessDenied(snippetId, USER))

        assertEquals(1, session.sent.size)
        val frame = frame(session.sent.single())
        assertEquals("error", frame["type"])
        assertEquals(403, frame["status"])
        assertEquals("AccessDenied", frame["title"])
        assertEquals(CloseStatus(4403, "AccessDenied"), session.closeStatus)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a missing snippet closes with 4404`() {
        val session = connect(error = SnippetNotFound(snippetId))

        assertEquals(404, frame(session.sent.single())["status"])
        assertEquals(CloseStatus(4404, "SnippetNotFound"), session.closeStatus)
    }

    @Test
    fun `a bad snippet id closes with 4400`() {
        val session = connect(error = InvalidInput("Invalid snippet id"))

        assertEquals("Invalid snippet id", frame(session.sent.single())["detail"])
        assertEquals(CloseStatus(4400, "InvalidInput"), session.closeStatus)
    }

    @Test
    fun `start opens the engine session with the stored snippet and starts it`() {
        val session = connect()

        send(session, """{"type":"start"}""")

        assertEquals(listOf(snippet), opened)
        assertEquals(1, runSession.starts)
        assertNull(session.closeStatus)
    }

    @Test
    fun `a second start re-runs on the same engine session`() {
        val session = connect()

        send(session, """{"type":"start","mode":"interactive"}""")
        send(session, """{"type":"start"}""")

        assertEquals(1, opened.size)
        assertEquals(2, runSession.starts)
    }

    @Test
    fun `input is forwarded to the engine session`() {
        val session = connect()
        send(session, """{"type":"start"}""")

        send(session, """{"type":"input","value":"Ana","extra":true}""")

        assertEquals(listOf("Ana"), runSession.inputs)
    }

    @Test
    fun `input before any start is rejected as NoInteractiveRun`() {
        val session = connect()

        send(session, """{"type":"input","value":"Ana"}""")

        val frame = frame(session.sent.single())
        assertEquals("rejected", frame["type"])
        assertEquals("NoInteractiveRun", frame["title"])
        assertNull(session.closeStatus)
    }

    @Test
    fun `malformed json is rejected and the connection stays open`() {
        val session = connect()

        send(session, "{not json")

        assertEquals("MalformedMessage", frame(session.sent.single())["title"])
        assertNull(session.closeStatus)
    }

    @Test
    fun `test mode is rejected`() {
        val session = connect()

        send(session, """{"type":"start","mode":"test"}""")

        assertEquals("MalformedMessage", frame(session.sent.single())["title"])
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `an unknown type is rejected`() {
        val session = connect()

        send(session, """{"type":"stop"}""")

        assertEquals("MalformedMessage", frame(session.sent.single())["title"])
    }

    @Test
    fun `engine events are relayed as the protocol's json`() {
        val session = connect()
        send(session, """{"type":"start"}""")
        val events = checkNotNull(listener)

        events.onEvent(RunEvent.Output("Hello"))
        events.onEvent(RunEvent.AwaitingInput("Name:"))
        events.onEvent(RunEvent.Rejected("RunInProgress", "A run is in progress.", mapOf("limit" to 1)))
        events.onEvent(RunEvent.Done(listOf("Hello", "Ana"), RunIssue("DivisionByZero", "Zero", SAMPLE_SPAN)))
        events.onEvent(RunEvent.Done(listOf("ok"), null))

        assertEquals(
            listOf(
                """{"type":"output","line":"Hello"}""",
                """{"type":"awaiting-input","prompt":"Name:"}""",
                """{"type":"rejected","title":"RunInProgress","detail":"A run is in progress.",""" +
                    """"properties":{"limit":1}}""",
                """{"type":"done","outputs":["Hello","Ana"],"error":{"rule":"DivisionByZero","message":"Zero",""" +
                    """"span":{"start":{"line":1,"column":17},"end":{"line":1,"column":17}}}}""",
                """{"type":"done","outputs":["ok"],"error":null}""",
            ),
            session.sent,
        )
    }

    @Test
    fun `a client close closes the engine session`() {
        val session = connect()
        send(session, """{"type":"start"}""")

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        assertTrue(runSession.closed)
    }

    @Test
    fun `an engine that drops the connection gives a 503 error frame and close 4503`() {
        val session = connect()
        send(session, """{"type":"start"}""")

        checkNotNull(listener).onClosed(LanguageServiceUnavailable("engine connection closed: 1006"))

        val frame = frame(session.sent.single())
        assertEquals("error", frame["type"])
        assertEquals(503, frame["status"])
        assertEquals("LanguageServiceUnavailable", frame["title"])
        assertEquals("Language service is unavailable", frame["detail"])
        assertEquals(CloseStatus(4503, "LanguageServiceUnavailable"), session.closeStatus)
    }

    @Test
    fun `an engine that can't be reached on start gives close 4503`() {
        openResult = Result.Failure(LanguageServiceUnavailable("could not connect to the engine"))
        val session = connect()

        send(session, """{"type":"start"}""")

        assertEquals(503, frame(session.sent.single())["status"])
        assertEquals(4503, session.closeStatus?.code)
    }

    private fun connect(error: SnippetError? = null): FakeWebSocketSession {
        val attributes = mutableMapOf<String, Any>(RunAttributes.USER_ID to USER, RunAttributes.SNIPPET_ID to snippetId)
        if (error == null) attributes[RunAttributes.SNIPPET] = snippet else attributes[RunAttributes.ERROR] = error
        val session = FakeWebSocketSession(attributes)
        handler.afterConnectionEstablished(session)
        return session
    }

    private fun send(
        session: FakeWebSocketSession,
        payload: String,
    ) = handler.handleMessage(session, TextMessage(payload))

    private fun frame(payload: String): Map<String, Any?> {
        val parsed = json.readValue(payload, Map::class.java)
        return parsed.entries.associate { (key, value) -> key.toString() to value }
    }

    private class FakeRunSession : RunSession {
        var starts = 0
        val inputs = mutableListOf<String>()
        var closed = false

        override fun start() {
            starts++
        }

        override fun input(value: String) {
            inputs += value
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val USER = "auth0|franco"
    }
}
