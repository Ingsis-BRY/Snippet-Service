package com.ingsisbry.snippet.application.command.rules

import com.ingsisbry.snippet.application.command.common.SnippetChangeNotifier
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import com.ingsisbry.snippet.application.port.out.language.LintOutcome
import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.language.SnippetLinter
import com.ingsisbry.snippet.application.query.rules.RuleView
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetJobPublisher
import com.ingsisbry.snippet.support.FakeSnippetTestRepository
import com.ingsisbry.snippet.support.FakeUserRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UpdateRulesUseCaseTest {
    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val repository = FakeUserRuleRepository()
    private val analysis = FakeSnippetAnalysisRepository()
    private val publisher = FakeSnippetJobPublisher()
    private val lintChecks = mutableListOf<Pair<String, Map<String, Any>>>()
    private val formatChecks = mutableListOf<Pair<String, Map<String, Any>>>()
    private var engineAnswer: Result<SnippetError, Unit> = Result.Success(Unit)

    private val linter =
        SnippetLinter { _, version, _, config ->
            lintChecks += version to config
            engineAnswer.map { LintOutcome(true, emptyList()) }
        }
    private val formatter =
        SnippetFormatter { _, version, _, config ->
            formatChecks += version to config
            engineAnswer.map { FormatOutcome("", null, emptyList()) }
        }
    private val catalog =
        LanguageCatalog {
            Result.Success(listOf(LanguageInfo("printscript", listOf("prs"), listOf("1.0", "1.1"), "1.0", "1.1.3")))
        }
    private val definitions =
        RuleDefinitions { kind -> Result.Success(if (kind == RuleKind.LINT) LINT_RULES else FORMAT_RULES) }

    private val useCase =
        UpdateRulesUseCase(
            definitions = definitions,
            configCheck = RuleConfigCheck(catalog, linter, formatter),
            repository = repository,
            analysisRepository = analysis,
            changes = SnippetChangeNotifier(FakeSnippetTestRepository(), publisher),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    @Test
    fun `stores every catalog rule, missing ones disabled, and returns the new state`() {
        val result =
            useCase.execute(
                UpdateRulesCommand(USER, RuleKind.LINT, listOf(RuleUpdate("identifier_format", true, "snake case"))),
            )

        assertEquals(
            listOf(
                RuleSetting("identifier_format", true, "snake case"),
                RuleSetting("mandatory-variable-or-literal-in-println", false, null),
            ),
            repository.find(USER, RuleKind.LINT),
        )
        val views = (result as Result.Success).value
        assertEquals(listOf(true, false), views.map(RuleView::active))
        assertEquals(listOf<Any?>("snake case", null), views.map(RuleView::value))
    }

    @Test
    fun `the engine checks the enabled rules with the newest version before anything is stored`() {
        useCase.execute(
            UpdateRulesCommand(
                USER,
                RuleKind.FORMAT,
                listOf(RuleUpdate("indent-inside-block", true, 4), RuleUpdate("space-around-colon", true, null)),
            ),
        )

        val expected = mapOf<String, Any>("indent-inside-block" to 4, "space-around-colon" to true)
        assertEquals(listOf("1.1" to expected), formatChecks)
        assertTrue(lintChecks.isEmpty())
    }

    @Test
    fun `a config the engine refuses is a 400 and nothing is stored or reprocessed`() {
        engineAnswer = Result.Failure(InvalidInput("Rules 'a' and 'b' contradict each other"))

        val result = update(RuleKind.FORMAT, RuleUpdate("space-around-colon", true, null))

        assertEquals(Result.Failure(InvalidInput("Rules 'a' and 'b' contradict each other")), result)
        assertTrue(repository.settings.isEmpty())
        assertTrue(analysis.pendingMarks.isEmpty())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `an engine outage is passed on`() {
        engineAnswer = Result.Failure(LanguageServiceUnavailable("down"))

        val result = useCase.execute(UpdateRulesCommand(USER, RuleKind.LINT, emptyList()))

        assertEquals(Result.Failure(LanguageServiceUnavailable("down")), result)
    }

    @Test
    fun `an unknown rule is rejected`() {
        val result = update(RuleKind.LINT, RuleUpdate("no-such-rule", true, null))

        assertEquals(Result.Failure(InvalidInput("Unknown rule 'no-such-rule'")), result)
        assertTrue(lintChecks.isEmpty())
    }

    @Test
    fun `a rule sent twice is rejected`() {
        val twice = listOf(RuleUpdate("space-around-colon", true, null), RuleUpdate("space-around-colon", false, null))

        val result = useCase.execute(UpdateRulesCommand(USER, RuleKind.FORMAT, twice))

        assertEquals(Result.Failure(InvalidInput("Rule 'space-around-colon' appears more than once")), result)
    }

    @Test
    fun `an active integer rule needs an integer within its bounds`() {
        listOf<Any?>(17, -1, "4", null, 2.5).forEach { value ->
            val result = update(RuleKind.FORMAT, RuleUpdate("indent-inside-block", true, value))

            assertEquals(
                Result.Failure(InvalidInput("Rule 'indent-inside-block' needs an integer between 0 and 16")),
                result,
                "value $value",
            )
        }
    }

    @Test
    fun `an active enum rule needs one of its options`() {
        val result = update(RuleKind.LINT, RuleUpdate("identifier_format", true, "kebab"))

        val reason = "Rule 'identifier_format' needs one of: camel case, snake case"
        assertEquals(Result.Failure(InvalidInput(reason)), result)
    }

    @Test
    fun `an inactive rule keeps a valid value and drops an invalid one`() {
        useCase.execute(
            UpdateRulesCommand(
                USER,
                RuleKind.FORMAT,
                listOf(RuleUpdate("indent-inside-block", false, 2), RuleUpdate("space-around-colon", false, "x")),
            ),
        )

        assertEquals(
            listOf(RuleSetting("indent-inside-block", false, 2), RuleSetting("space-around-colon", false, null)),
            repository.find(USER, RuleKind.FORMAT),
        )
        assertEquals(listOf("1.1" to emptyMap<String, Any>()), formatChecks)
    }

    @Test
    fun `saving marks the owner's snippets pending and publishes one job per snippet`() {
        val first = UUID.randomUUID() to UUID.randomUUID()
        val second = UUID.randomUUID() to UUID.randomUUID()
        analysis.addSnippet(first.first, first.second, USER)
        analysis.addSnippet(second.first, second.second, USER)
        analysis.addSnippet(UUID.randomUUID(), UUID.randomUUID(), "auth0|someone-else")

        useCase.execute(UpdateRulesCommand(USER, RuleKind.LINT, emptyList()))

        assertEquals(listOf(Triple(USER, setOf(SnippetTask.LINT), now)), analysis.pendingMarks)
        assertEquals(
            listOf(
                SnippetJob(first.first, first.second, setOf(SnippetTask.LINT), JobReason.LINT_RULES_CHANGED),
                SnippetJob(second.first, second.second, setOf(SnippetTask.LINT), JobReason.LINT_RULES_CHANGED),
            ),
            publisher.published,
        )
    }

    @Test
    fun `format rules reprocess only the format`() {
        val snippetId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        analysis.addSnippet(snippetId, versionId, USER)

        useCase.execute(UpdateRulesCommand(USER, RuleKind.FORMAT, emptyList()))

        assertEquals(
            listOf(SnippetJob(snippetId, versionId, setOf(SnippetTask.FORMAT), JobReason.FORMAT_RULES_CHANGED)),
            publisher.published,
        )
    }

    @Test
    fun `a lost publish does not fail the request`() {
        analysis.addSnippet(UUID.randomUUID(), UUID.randomUUID(), USER)
        publisher.failure = IllegalStateException("stream down")

        val result = useCase.execute(UpdateRulesCommand(USER, RuleKind.LINT, emptyList()))

        assertTrue(result is Result.Success)
    }

    @Test
    fun `if the snippets can't be marked pending the request fails`() {
        analysis.failure = PersistenceUnavailable("down")

        val result = useCase.execute(UpdateRulesCommand(USER, RuleKind.LINT, emptyList()))

        assertEquals(Result.Failure(PersistenceUnavailable("down")), result)
    }

    private fun update(
        kind: RuleKind,
        rule: RuleUpdate,
    ) = useCase.execute(UpdateRulesCommand(USER, kind, listOf(rule)))

    private companion object {
        const val USER = "auth0|franco"

        val LINT_RULES =
            listOf(
                RuleDefinition(
                    "identifier_format",
                    RuleType.ENUM,
                    "camel case",
                    "1.0",
                    "How identifiers are written.",
                    listOf("camel case", "snake case"),
                    null,
                    null,
                ),
                RuleDefinition(
                    "mandatory-variable-or-literal-in-println",
                    RuleType.BOOLEAN,
                    false,
                    "1.0",
                    "println only takes a variable or a literal.",
                    null,
                    null,
                    null,
                ),
            )

        val FORMAT_RULES =
            listOf(
                RuleDefinition("indent-inside-block", RuleType.INTEGER, null, "1.1", "Indentation.", null, 0, 16),
                RuleDefinition("space-around-colon", RuleType.BOOLEAN, false, "1.0", "Spaces.", null, null, null),
            )
    }
}
