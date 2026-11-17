package com.ingsisbry.snippet.adapter.run

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan
import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunIssue

/**
 * What the browser sends: `{"type":"start"}` (optionally `"mode":"interactive"`), `{"type":"start","mode":"test",
 * "testId":"..."}` or `{"type":"input","value":"..."}`. It never sends code, a version or a test's definition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientRunMessage(
    val type: String?,
    val mode: String?,
    val value: String?,
    val testId: String? = null,
)

/** What the server sends to the browser, as JSON-ready maps so the field order and the nulls are explicit. */
internal object ServerRunMessages {
    fun of(event: RunEvent): Map<String, Any?> =
        when (event) {
            is RunEvent.Output -> linkedMapOf("type" to "output", "line" to event.line)
            is RunEvent.AwaitingInput -> linkedMapOf("type" to "awaiting-input", "prompt" to event.prompt)
            is RunEvent.Done ->
                linkedMapOf(
                    "type" to "done",
                    "outputs" to event.outputs,
                    "error" to event.error?.let(::issue),
                    "passed" to event.passed,
                )
            is RunEvent.Rejected -> rejected(event.title, event.detail, event.properties)
        }

    fun rejected(
        title: String,
        detail: String,
        properties: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        linkedMapOf(
            "type" to "rejected",
            "title" to title,
            "detail" to detail,
            "properties" to properties,
        )

    fun error(
        status: Int,
        title: String,
        detail: String,
    ): Map<String, Any?> =
        linkedMapOf(
            "type" to "error",
            "status" to status,
            "title" to title,
            "detail" to detail,
        )

    private fun issue(issue: RunIssue): Map<String, Any?> =
        linkedMapOf("rule" to issue.rule, "message" to issue.message, "span" to issue.span?.let(::span))

    private fun span(span: SourceSpan): Map<String, Any?> =
        linkedMapOf(
            "start" to position(span.start),
            "end" to position(span.end),
        )

    private fun position(position: SourcePosition): Map<String, Int> =
        linkedMapOf(
            "line" to position.line,
            "column" to position.column,
        )
}
