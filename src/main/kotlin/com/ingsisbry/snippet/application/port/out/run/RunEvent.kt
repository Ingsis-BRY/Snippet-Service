package com.ingsisbry.snippet.application.port.out.run

import com.ingsisbry.snippet.application.error.SourceSpan

/** What a running snippet reports, in the order it happens. Every run ends with exactly one [Done]. */
sealed interface RunEvent {
    /** A line the snippet printed. */
    data class Output(
        val line: String,
    ) : RunEvent

    /** The snippet is waiting for the user's answer to [prompt]. */
    data class AwaitingInput(
        val prompt: String,
    ) : RunEvent

    /**
     * The run ended; [error] is set when the code failed, timed out or waited too long for input. [passed] is set
     * only when the run was a test.
     */
    data class Done(
        val outputs: List<String>,
        val error: RunIssue?,
        val passed: Boolean? = null,
    ) : RunEvent

    /** A message was refused before anything ran (e.g. a run is already in progress); the session stays open. */
    data class Rejected(
        val title: String,
        val detail: String,
        val properties: Map<String, Any?>,
    ) : RunEvent
}

/** A problem in the code found while running it. */
data class RunIssue(
    val rule: String,
    val message: String,
    val span: SourceSpan?,
)
