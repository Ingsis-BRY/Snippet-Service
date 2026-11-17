package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunIssue
import com.ingsisbry.snippet.support.SAMPLE_SPAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

class EngineRunMessagesTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `output and awaiting-input become their events`() {
        assertEquals(RunEvent.Output("Hello"), parse("""{"type":"output","line":"Hello"}"""))
        assertEquals(RunEvent.AwaitingInput("Name:"), parse("""{"type":"awaiting-input","prompt":"Name:"}"""))
    }

    @Test
    fun `done keeps the outputs and the issue and drops passed`() {
        val event =
            parse(
                """{"type":"done","outputs":["a","b"],"passed":null,""" +
                    """"error":{"rule":"DivisionByZero","message":"Division by zero",$SPAN_JSON}}""",
            )

        assertEquals(
            RunEvent.Done(listOf("a", "b"), RunIssue("DivisionByZero", "Division by zero", SAMPLE_SPAN)),
            event,
        )
    }

    @Test
    fun `done without an error and an issue without a span`() {
        assertEquals(RunEvent.Done(listOf("ok"), null), parse("""{"type":"done","outputs":["ok"],"error":null}"""))
        assertEquals(
            RunEvent.Done(emptyList(), RunIssue("ExecutionTimeout", "Too slow", null)),
            parse(
                """{"type":"done","outputs":[],"error":{"rule":"ExecutionTimeout","message":"Too slow","span":null}}""",
            ),
        )
    }

    @Test
    fun `rejected keeps its title, detail and properties`() {
        val event =
            parse("""{"type":"rejected","title":"TooManyInputs","detail":"Too many","properties":{"limit":100}}""")

        assertEquals(RunEvent.Rejected("TooManyInputs", "Too many", mapOf("limit" to 100)), event)
    }

    @Test
    fun `an unknown type is dropped`() {
        assertNull(parse("""{"type":"progress","percent":50}"""))
    }

    @Test
    fun `a frame that is not JSON is an error`() {
        assertThrows<JacksonException> { parse("not json") }
    }

    @Test
    fun `the engine messages carry the code and the version`() {
        assertEquals(
            """{"type":"start","mode":"interactive","code":"println(1);","version":"1.1"}""",
            json.writeValueAsString(EngineRunMessages.start("println(1);", "1.1")),
        )
        assertEquals("""{"type":"input","value":"Ana"}""", json.writeValueAsString(EngineRunMessages.input("Ana")))
    }

    @Test
    fun `the engine url becomes a websocket url`() {
        assertEquals("ws://engine-proxy:80", toWebSocketUrl("http://engine-proxy:80"))
        assertEquals("wss://engine.example", toWebSocketUrl("https://engine.example/"))
    }

    private fun parse(payload: String) = EngineRunMessages.parse(json, payload)

    private companion object {
        const val SPAN_JSON = """"span":{"start":{"line":1,"column":17},"end":{"line":1,"column":17}}"""
    }
}
