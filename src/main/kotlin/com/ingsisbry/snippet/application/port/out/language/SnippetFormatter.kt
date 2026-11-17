package com.ingsisbry.snippet.application.port.out.language

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** Either [formatted] or [error] is set; [ignoredRules] exist only in later language versions. */
data class FormatOutcome(
    val formatted: String?,
    val error: CodeIssue?,
    val ignoredRules: List<String>,
)

fun interface SnippetFormatter {
    fun format(
        language: String,
        version: String,
        code: String,
        config: Map<String, Any>,
    ): Result<SnippetError, FormatOutcome>
}
