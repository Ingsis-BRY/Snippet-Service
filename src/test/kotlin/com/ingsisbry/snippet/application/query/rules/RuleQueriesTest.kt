package com.ingsisbry.snippet.application.query.rules

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting
import com.ingsisbry.snippet.support.FakeUserRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleQueriesTest {
    private val repository = FakeUserRuleRepository()

    private fun resolver(definitions: RuleDefinitions = RuleDefinitions { Result.Success(CATALOG + IDENTIFIER_RULE) }) =
        ResolveRuleConfigUseCase(repository, definitions)

    @Test
    fun `the engine config has every enabled rule, true for boolean ones, and leaves disabled ones out`() {
        repository.replace(
            USER,
            RuleKind.FORMAT,
            listOf(
                RuleSetting("space-around-colon", true, null),
                RuleSetting("indent-inside-block", true, 4),
                RuleSetting("identifier_format", true, "snake case"),
                RuleSetting("space-around-equals", false, null),
            ),
        )

        val config = resolver().execute(USER, RuleKind.FORMAT)

        assertEquals(
            mapOf<String, Any>(
                "space-around-colon" to true,
                "indent-inside-block" to 4,
                "identifier_format" to "snake case",
            ),
            config,
        )
    }

    @Test
    fun `rules the engine no longer has are not sent to it`() {
        repository.replace(
            USER,
            RuleKind.FORMAT,
            listOf(
                RuleSetting("space-around-colon", true, null),
                RuleSetting("a-rule-the-engine-dropped", true, null),
            ),
        )

        val config = resolver(RuleDefinitions { Result.Success(CATALOG) }).execute(USER, RuleKind.FORMAT)

        assertEquals(mapOf<String, Any>("space-around-colon" to true), config)
    }

    @Test
    fun `when the catalog can't be read every stored rule is sent`() {
        repository.replace(USER, RuleKind.FORMAT, listOf(RuleSetting("a-rule-the-engine-dropped", true, null)))
        val unavailable = RuleDefinitions { Result.Failure(LanguageServiceUnavailable("engine down")) }

        val config = resolver(unavailable).execute(USER, RuleKind.FORMAT)

        assertEquals(mapOf<String, Any>("a-rule-the-engine-dropped" to true), config)
    }

    @Test
    fun `a user who never saved rules has an empty config`() {
        assertEquals(emptyMap<String, Any>(), resolver().execute(USER, RuleKind.LINT))
    }

    @Test
    fun `rules are the catalog in order with the user's choices, defaults otherwise`() {
        repository.replace(
            USER,
            RuleKind.FORMAT,
            listOf(
                RuleSetting("indent-inside-block", true, 2),
                RuleSetting("space-around-colon", true, null),
                RuleSetting("a-rule-the-engine-dropped", true, null),
            ),
        )
        val useCase = GetRulesUseCase(RuleDefinitions { Result.Success(CATALOG) }, repository)

        val views = (useCase.execute(USER, RuleKind.FORMAT) as Result.Success).value

        assertEquals(
            listOf(
                RuleView(CATALOG[0], active = true, value = 2),
                RuleView(CATALOG[1], active = true, value = null),
                RuleView(CATALOG[2], active = false, value = 1),
            ),
            views,
        )
    }

    @Test
    fun `an engine outage fails the rule list`() {
        val definitions = RuleDefinitions { Result.Failure(LanguageServiceUnavailable("down")) }
        val useCase = GetRulesUseCase(definitions, repository)

        assertEquals(Result.Failure(LanguageServiceUnavailable("down")), useCase.execute(USER, RuleKind.LINT))
    }

    private companion object {
        const val USER = "auth0|franco"
        val CATALOG =
            listOf(
                RuleDefinition("indent-inside-block", RuleType.INTEGER, 4, "1.1", "Indentation.", null, 0, 16),
                RuleDefinition("space-around-colon", RuleType.BOOLEAN, false, "1.0", "Spaces.", null, null, null),
                RuleDefinition("line-breaks-before-println", RuleType.INTEGER, 1, "1.0", "Breaks.", null, 0, 2),
            )
        val IDENTIFIER_RULE =
            RuleDefinition(
                "identifier_format",
                RuleType.ENUM,
                "camel case",
                "1.0",
                "Names.",
                listOf("camel case", "snake case"),
                null,
                null,
            )
    }
}
