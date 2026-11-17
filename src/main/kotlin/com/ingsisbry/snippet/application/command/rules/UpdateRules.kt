package com.ingsisbry.snippet.application.command.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.rules.RuleView
import com.ingsisbry.snippet.domain.rules.RuleKind

/** What the user asks for one rule; [value] is whatever the client sent (an Int, a String or null). */
data class RuleUpdate(
    val id: String,
    val active: Boolean,
    val value: Any?,
)

data class UpdateRulesCommand(
    val userId: String,
    val kind: RuleKind,
    val rules: List<RuleUpdate>,
)

/** Replaces the user's rules of one kind and re-checks all their snippets in the background (US#11-#15). */
fun interface UpdateRules {
    fun execute(command: UpdateRulesCommand): Result<SnippetError, List<RuleView>>
}
