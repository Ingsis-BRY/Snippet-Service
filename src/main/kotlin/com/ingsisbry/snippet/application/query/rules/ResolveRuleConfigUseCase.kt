package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.rules.UserRuleRepository
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting
import org.springframework.stereotype.Service

@Service
class ResolveRuleConfigUseCase(
    private val repository: UserRuleRepository,
    private val definitions: RuleDefinitions,
) : ResolveRuleConfig {
    /**
     * Only rules the engine still has are sent: a rule a newer engine dropped would make the engine refuse
     * every lint or format of this user's snippets. When the catalog can't be read, every stored rule is
     * sent and the engine call decides.
     */
    override fun execute(
        userId: String,
        kind: RuleKind,
    ): Map<String, Any> {
        val settings = repository.find(userId, kind)
        val known =
            definitions.rules(kind).fold(
                onSuccess = { catalog -> catalog.map { it.key }.toSet() },
                onFailure = { null },
            )
        return toConfig(if (known == null) settings else settings.filter { it.key in known })
    }

    companion object {
        /** Disabled rules are left out; an enabled rule without a value is a boolean rule turned on. */
        fun toConfig(settings: List<RuleSetting>): Map<String, Any> =
            settings.filter { it.enabled }.associate { it.key to (it.value ?: true) }
    }
}
