package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

data class TestSpec(
    val id: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
)

data class TestOutcome(
    val id: String,
    val passed: Boolean,
    val actualOutputs: List<String>,
    val error: CodeIssue?,
)

fun interface SnippetTestRunner {
    fun run(
        language: String,
        version: String,
        code: String,
        tests: List<TestSpec>,
    ): Result<SnippetError, List<TestOutcome>>
}
