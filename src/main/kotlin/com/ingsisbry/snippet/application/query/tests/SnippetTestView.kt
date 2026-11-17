package com.ingsisbry.snippet.application.query.tests

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.domain.test.SnippetTest
import java.time.Instant
import java.util.UUID

/** The result of one run of a test, against snippet version [versionId]. */
data class TestResult(
    val status: TestStatus,
    val actualOutputs: List<String>,
    val error: CodeIssue?,
    val versionId: UUID,
    val at: Instant,
)

/** A test with the result of its last automatic run. */
data class SnippetTestView(
    val test: SnippetTest,
    val status: TestStatus,
    val actualOutputs: List<String>?,
    val error: CodeIssue?,
    val lastRunAt: Instant?,
)
