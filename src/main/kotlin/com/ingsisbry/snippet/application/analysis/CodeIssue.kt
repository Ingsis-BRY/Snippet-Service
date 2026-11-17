package com.ingsisbry.snippet.application.analysis

import com.ingsisbry.snippet.application.error.SourceSpan

/** A problem in a snippet's code (a lint violation, a parse error, a failed run); [span] is null without a position. */
data class CodeIssue(
    val rule: String,
    val message: String,
    val span: SourceSpan?,
)
