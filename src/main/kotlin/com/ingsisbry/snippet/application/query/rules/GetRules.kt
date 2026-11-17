package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.rules.RuleKind

/** The user's format or lint rules (US#11, #14). */
fun interface GetRules {
    fun execute(
        userId: String,
        kind: RuleKind,
    ): Result<SnippetError, List<RuleView>>
}
