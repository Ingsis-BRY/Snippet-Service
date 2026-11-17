package com.ingsisbry.snippet.application.command.rules

import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.domain.rules.RuleSetting

/** Either why the request is invalid, or one setting per catalog rule. */
internal data class ValidatedSettings(
    val violation: String?,
    val settings: List<RuleSetting>,
)

/** Turns the client's rule list into the settings to store, checked against the engine's catalog. */
internal object RuleSettingsValidator {
    /**
     * Catalog rules missing from [updates] are stored disabled (a full replace). An inactive rule keeps its
     * value when it is valid; an active one must have a valid value.
     */
    fun validate(
        catalog: List<RuleDefinition>,
        updates: List<RuleUpdate>,
    ): ValidatedSettings {
        val byKey = catalog.associateBy { it.key }
        val requested = updates.associateBy { it.id }
        val settings =
            catalog.map { definition ->
                val update = requested[definition.key]
                RuleSetting(
                    key = definition.key,
                    enabled = update?.active ?: false,
                    value = update?.let { normalized(definition, it.value) },
                )
            }
        return ValidatedSettings(violation(byKey, updates), settings)
    }

    private fun violation(
        byKey: Map<String, RuleDefinition>,
        updates: List<RuleUpdate>,
    ): String? {
        val unknown = updates.firstOrNull { it.id !in byKey }?.id
        val duplicate =
            updates
                .groupBy { it.id }
                .entries
                .firstOrNull { it.value.size > 1 }
                ?.key
        return when {
            unknown != null -> "Unknown rule '$unknown'"
            duplicate != null -> "Rule '$duplicate' appears more than once"
            else ->
                updates.firstNotNullOfOrNull { update ->
                    byKey[update.id]?.takeIf { update.active }?.let { valueViolation(it, update.value) }
                }
        }
    }

    private fun valueViolation(
        definition: RuleDefinition,
        value: Any?,
    ): String? =
        when {
            normalized(definition, value) != null || definition.type == RuleType.BOOLEAN -> null
            definition.type == RuleType.INTEGER ->
                "Rule '${definition.key}' needs an integer between ${definition.min ?: "any"} and " +
                    "${definition.max ?: "any"}"
            else -> "Rule '${definition.key}' needs one of: ${definition.options.orEmpty().joinToString()}"
        }

    /** The value to store when it is valid for the rule, else null; boolean rules never store one. */
    private fun normalized(
        definition: RuleDefinition,
        value: Any?,
    ): Any? =
        when (definition.type) {
            RuleType.BOOLEAN -> null
            RuleType.INTEGER -> integer(value)?.takeIf { it in range(definition) }
            RuleType.ENUM -> (value as? String)?.takeIf { it in definition.options.orEmpty() }
        }

    private fun range(definition: RuleDefinition): IntRange {
        val min = definition.min ?: Int.MIN_VALUE
        return min..(definition.max ?: Int.MAX_VALUE)
    }

    private fun integer(value: Any?): Int? =
        when (value) {
            is Int -> value
            is Number -> {
                val number = value.toDouble()
                number.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
            }
            else -> null
        }
}
