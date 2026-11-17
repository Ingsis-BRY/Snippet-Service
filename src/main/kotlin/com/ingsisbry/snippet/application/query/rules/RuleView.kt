package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.domain.rules.RuleSetting

/** A catalog rule with the user's choice: [value] is null for boolean rules, else the stored or default value. */
data class RuleView(
    val definition: RuleDefinition,
    val active: Boolean,
    val value: Any?,
) {
    companion object {
        /** Every catalog rule, in catalog order; stored keys the catalog no longer has are ignored. */
        fun of(
            definitions: List<RuleDefinition>,
            settings: List<RuleSetting>,
        ): List<RuleView> {
            val byKey = settings.associateBy { it.key }
            return definitions.map { definition ->
                val setting = byKey[definition.key]
                RuleView(
                    definition = definition,
                    active = setting?.enabled ?: false,
                    value =
                        if (definition.type == RuleType.BOOLEAN) {
                            null
                        } else {
                            setting?.value ?: definition.default
                        },
                )
            }
        }
    }
}
