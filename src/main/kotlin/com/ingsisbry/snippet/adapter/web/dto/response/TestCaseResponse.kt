package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import java.time.Instant
import java.util.UUID

/** The UI's `TestCase`: [input] are the ordered inputs, [output] the ordered expected lines. */
data class TestCaseResponse(
    val id: UUID,
    val snippetId: UUID,
    val name: String,
    val input: List<String>,
    val output: List<String>,
    val status: String,
    val actualOutput: List<String>?,
    val error: IssueResponse?,
    val lastRunAt: Instant?,
) {
    companion object {
        fun from(view: SnippetTestView): TestCaseResponse =
            TestCaseResponse(
                id = view.test.id,
                snippetId = view.test.snippetId,
                name = view.test.name,
                input = view.test.inputs,
                output = view.test.expectedOutputs,
                status = view.status.toJsonValue(),
                actualOutput = view.actualOutputs,
                error = view.error?.let(IssueResponse::from),
                lastRunAt = view.lastRunAt,
            )
    }
}
