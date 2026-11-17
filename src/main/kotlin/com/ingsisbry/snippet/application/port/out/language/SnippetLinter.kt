package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** [issues] = the rule violations plus the error, if the code did not parse. */
data class LintOutcome(
    val valid: Boolean,
    val issues: List<CodeIssue>,
)

fun interface SnippetLinter {
    fun lint(
        language: String,
        version: String,
        code: String,
        config: Map<String, Any>,
    ): Result<SnippetError, LintOutcome>
}
