package com.ingsisbry.snippet.application.port.out.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting

/** Each user's enabled flag and value for every rule they configured. */
interface UserRuleRepository {
    fun find(
        userId: String,
        kind: RuleKind,
    ): List<RuleSetting>

    /** Replaces all the user's settings of [kind] in one transaction. */
    fun replace(
        userId: String,
        kind: RuleKind,
        settings: List<RuleSetting>,
    ): Result<SnippetError, Unit>
}
