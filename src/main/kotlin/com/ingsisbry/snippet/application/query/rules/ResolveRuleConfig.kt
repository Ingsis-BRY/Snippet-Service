package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.domain.rules.RuleKind

/** The config the engine gets for a user's snippets: every enabled rule, `true` for boolean ones. */
fun interface ResolveRuleConfig {
    fun execute(
        userId: String,
        kind: RuleKind,
    ): Map<String, Any>
}
