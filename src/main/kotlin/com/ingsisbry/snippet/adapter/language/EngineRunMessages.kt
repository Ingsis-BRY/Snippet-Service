package com.ingsisbry.snippet.adapter.language

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.run.RunEvent
import com.ingsisbry.snippet.application.port.out.run.RunIssue
import tools.jackson.databind.json.JsonMapper

/** Any message the engine sends on `/run/interactive`; `type` says which fields matter. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class EngineRunMessage(
    val type: String?,
    val line: String?,
    val prompt: String?,
    val outputs: List<String>?,
    val error: EngineIssue?,
    val title: String?,
    val detail: String?,
    val properties: Map<String, Any?>?,
    val passed: Boolean? = null,
)

internal object EngineRunMessages {
    /**
     * Turns an engine frame into a [RunEvent], or null for a `type` this service doesn't know.
     * Throws Jackson's exception when the frame isn't JSON.
     */
    fun parse(
        json: JsonMapper,
        payload: String,
    ): RunEvent? {
        val message = json.readValue(payload, EngineRunMessage::class.java)
        return when (message.type) {
            "output" -> RunEvent.Output(message.line.orEmpty())
            "awaiting-input" -> RunEvent.AwaitingInput(message.prompt.orEmpty())
            "done" -> RunEvent.Done(message.outputs.orEmpty(), message.error?.toRunIssue(), message.passed)
            "rejected" ->
                RunEvent.Rejected(
                    title = message.title.orEmpty(),
                    detail = message.detail.orEmpty(),
                    properties = message.properties.orEmpty(),
                )
            else -> null
        }
    }

    fun start(
        code: String,
        version: String,
    ): Map<String, Any> =
        linkedMapOf(
            "type" to "start",
            "mode" to "interactive",
            "code" to code,
            "version" to version,
        )

    fun startTest(
        code: String,
        version: String,
        test: TestSpec,
    ): Map<String, Any> =
        linkedMapOf(
            "type" to "start",
            "mode" to "test",
            "code" to code,
            "version" to version,
            "test" to
                linkedMapOf(
                    "id" to test.id,
                    "inputs" to test.inputs,
                    "expectedOutputs" to test.expectedOutputs,
                ),
        )

    fun input(value: String): Map<String, Any> = linkedMapOf("type" to "input", "value" to value)

    private fun EngineIssue.toRunIssue(): RunIssue = RunIssue(rule, message, span?.toSourceSpan())
}
