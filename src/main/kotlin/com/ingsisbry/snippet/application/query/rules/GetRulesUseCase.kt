package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.rules.UserRuleRepository
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.stereotype.Service

@Service
class GetRulesUseCase(
    private val definitions: RuleDefinitions,
    private val repository: UserRuleRepository,
) : GetRules {
    override fun execute(
        userId: String,
        kind: RuleKind,
    ): Result<SnippetError, List<RuleView>> {
        val catalog = definitions.rules(kind)
        return catalog.map { rules -> RuleView.of(rules, repository.find(userId, kind)) }
    }
}
