package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.rules.UserRuleRepository
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting

class FakeUserRuleRepository : UserRuleRepository {
    val settings = mutableMapOf<Pair<String, RuleKind>, List<RuleSetting>>()

    override fun find(
        userId: String,
        kind: RuleKind,
    ): List<RuleSetting> = settings[userId to kind].orEmpty()

    override fun replace(
        userId: String,
        kind: RuleKind,
        settings: List<RuleSetting>,
    ): Result<SnippetError, Unit> {
        this.settings[userId to kind] = settings
        return Result.Success(Unit)
    }
}
