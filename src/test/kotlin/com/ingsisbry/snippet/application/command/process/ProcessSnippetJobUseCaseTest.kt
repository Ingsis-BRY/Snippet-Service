package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.LintOutcome
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.language.SnippetLinter
import com.ingsisbry.snippet.application.port.out.language.SnippetTestRunner
import com.ingsisbry.snippet.application.port.out.language.TestOutcome
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfig
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.test.SnippetTest
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetTestRepository
import com.ingsisbry.snippet.support.InMemoryContentStorage
import com.ingsisbry.snippet.support.SAMPLE_INVALID_CONTENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProcessSnippetJobUseCaseTest {
    private val analysis = FakeSnippetAnalysisRepository()
    private val tests = FakeSnippetTestRepository()
    private val storage = InMemoryContentStorage()
    private val queryRepository: SnippetQueryRepository = Mockito.mock(SnippetQueryRepository::class.java)
    private val snippet =
        Snippet.create(
            id = UUID.randomUUID(),
            name = "adder",
            description = "adds",
            language = "printscript",
            languageVersion = "1.1",
            owner = OWNER,
            content = CODE,
            versionId = UUID.randomUUID(),
            createdAt = NOW,
        )
    private val versionId = snippet.currentVersion.id

    private var validation: Result<SnippetError, Unit> = Result.Success(Unit)
    private var lintResult: Result<SnippetError, LintOutcome> = Result.Success(LintOutcome(true, emptyList()))
    private var formatResult: Result<SnippetError, FormatOutcome> =
        Result.Success(FormatOutcome(FORMATTED, null, emptyList()))
    private var testResult: (List<TestSpec>) -> Result<SnippetError, List<TestOutcome>> =
        { specs -> Result.Success(specs.map { TestOutcome(it.id, true, it.expectedOutputs, null) }) }
    private val engineCalls = mutableListOf<String>()
    private val configs = mutableListOf<Map<String, Any>>()
    private val testRuns = mutableListOf<List<TestSpec>>()

    private val useCase =
        ProcessSnippetJobUseCase(
            analysis = analysis,
            snippets = queryRepository,
            validator =
                SnippetContentValidator { _, _, _ ->
                    engineCalls += "validate"
                    validation
                },
            linter =
                SnippetLinter { _, _, _, config ->
                    engineCalls += "lint"
                    configs += config
                    lintResult
                },
            formatter =
                SnippetFormatter { _, _, _, config ->
                    engineCalls += "format"
                    configs += config
                    formatResult
                },
            testRunner =
                SnippetTestRunner { _, _, _, specs ->
                    engineCalls += "test"
                    testRuns += specs
                    testResult(specs)
                },
            tests = tests,
            contentStorage = storage,
            ruleConfig = ResolveRuleConfig { userId, kind -> ownerConfig(userId, kind) },
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )

    @BeforeEach
    fun setUp() {
        analysis.addSnippet(snippet.id, versionId, OWNER)
        Mockito.`when`(queryRepository.findById(snippet.id)).thenReturn(Result.Success(snippet))
    }

    @Test
    fun `a job for a version that is no longer current is skipped without calling the engine`() {
        analysis.current[snippet.id] = UUID.randomUUID()

        assertEquals(Result.Success(JobOutcome.SKIPPED), useCase.execute(job(SnippetTask.LINT)))
        assertEquals(emptyList<String>(), engineCalls)
    }

    @Test
    fun `a job for a deleted snippet is skipped`() {
        analysis.current.remove(snippet.id)

        assertEquals(Result.Success(JobOutcome.SKIPPED), useCase.execute(job(SnippetTask.LINT)))

        analysis.current[snippet.id] = versionId
        Mockito.`when`(queryRepository.findById(snippet.id)).thenReturn(Result.Failure(SnippetNotFound(snippet.id)))
        assertEquals(Result.Success(JobOutcome.SKIPPED), useCase.execute(job(SnippetTask.LINT)))
        assertEquals(emptyList<String>(), engineCalls)
    }

    @Test
    fun `compliant code is stored as compliant, linted with the owner's lint rules`() {
        val result = useCase.execute(job(SnippetTask.LINT))

        assertEquals(Result.Success(JobOutcome.PROCESSED), result)
        assertEquals(LintStatus.COMPLIANT, analysis.analyses.getValue(versionId).lintStatus)
        assertEquals(listOf(mapOf<String, Any>("kind" to RuleKind.LINT.name)), configs)
    }

    @Test
    fun `lint violations make the version not compliant and are stored`() {
        lintResult = Result.Success(LintOutcome(false, listOf(ISSUE)))

        useCase.execute(job(SnippetTask.LINT))

        val stored = analysis.analyses.getValue(versionId)
        assertEquals(LintStatus.NOT_COMPLIANT, stored.lintStatus)
        assertEquals(listOf(ISSUE), stored.lintIssues)
    }

    @Test
    fun `formatting stores the blob under the version's key and marks the format finished`() {
        val result = useCase.execute(job(SnippetTask.FORMAT))

        val key = "snippets/${snippet.id}/versions/$versionId/formatted"
        assertEquals(Result.Success(JobOutcome.PROCESSED), result)
        assertEquals(FORMATTED, storage.blobs[key])
        assertEquals(key, analysis.formattedKeys[versionId])
        assertEquals(FormatStatus.FINISHED, analysis.analyses.getValue(versionId).formatStatus)
        assertEquals(listOf(mapOf<String, Any>("kind" to RuleKind.FORMAT.name)), configs)
    }

    @Test
    fun `running the same format job twice overwrites the same blob`() {
        useCase.execute(job(SnippetTask.FORMAT))
        formatResult = Result.Success(FormatOutcome("$FORMATTED\n", null, emptyList()))
        useCase.execute(job(SnippetTask.FORMAT))

        assertEquals(1, storage.blobs.size)
        assertEquals("$FORMATTED\n", storage.blobs.values.single())
    }

    @Test
    fun `code the formatter can't parse marks the format failed and stores nothing`() {
        formatResult = Result.Success(FormatOutcome(null, ISSUE, emptyList()))

        useCase.execute(job(SnippetTask.FORMAT))

        assertEquals(FormatStatus.FAILED, analysis.analyses.getValue(versionId).formatStatus)
        assertTrue(storage.blobs.isEmpty())
    }

    @Test
    fun `code that no longer validates is invalid, not compliant and not formatted, and lint is skipped`() {
        validation = Result.Failure(SAMPLE_INVALID_CONTENT)

        val result = useCase.execute(job(SnippetTask.VALIDATE, SnippetTask.LINT, SnippetTask.FORMAT))

        assertEquals(Result.Success(JobOutcome.PROCESSED), result)
        assertEquals(
            SnippetAnalysis(
                validationStatus = ValidationStatus.INVALID,
                validationError =
                    CodeIssue(SAMPLE_INVALID_CONTENT.rule, SAMPLE_INVALID_CONTENT.message, SAMPLE_INVALID_CONTENT.span),
                lintStatus = LintStatus.NOT_COMPLIANT,
                lintIssues = emptyList(),
                formatStatus = FormatStatus.FAILED,
            ),
            analysis.analyses.getValue(versionId),
        )
        assertEquals(listOf("validate"), engineCalls)
    }

    @Test
    fun `code that still validates is valid and then linted`() {
        analysis.analyses[versionId] = SnippetAnalysis.NEW.copy(validationStatus = ValidationStatus.PENDING)

        useCase.execute(job(SnippetTask.VALIDATE, SnippetTask.LINT))

        assertEquals(ValidationStatus.VALID, analysis.analyses.getValue(versionId).validationStatus)
        assertEquals(listOf("validate", "lint"), engineCalls)
    }

    @Test
    fun `tests are stored as passed, failed or error by their outcome`() {
        val passing = addTest("passes")
        val failing = addTest("fails")
        val erroring = addTest("errors")
        testResult = {
            Result.Success(
                listOf(
                    TestOutcome(passing.id.toString(), true, listOf("3"), null),
                    TestOutcome(failing.id.toString(), false, listOf("4"), null),
                    TestOutcome(erroring.id.toString(), false, emptyList(), ISSUE),
                ),
            )
        }

        useCase.execute(job(SnippetTask.TEST))

        assertEquals(TestStatus.PASSED, tests.tests.getValue(passing.id).status)
        assertEquals(TestStatus.FAILED, tests.tests.getValue(failing.id).status)
        assertEquals(listOf("4"), tests.tests.getValue(failing.id).actualOutputs)
        assertEquals(TestStatus.ERROR, tests.tests.getValue(erroring.id).status)
        assertEquals(ISSUE, tests.tests.getValue(erroring.id).error)
        assertEquals(
            listOf(TestSpec(passing.id.toString(), listOf("1", "2"), listOf("3"))),
            testRuns.single().take(1),
        )
    }

    @Test
    fun `a job naming tests runs only those`() {
        addTest("other")
        val named = addTest("named")

        useCase.execute(job(SnippetTask.TEST).copy(testIds = listOf(named.id)))

        assertEquals(listOf(named.id.toString()), testRuns.single().map { it.id })
    }

    @Test
    fun `a snippet without tests does not call the engine for them`() {
        useCase.execute(job(SnippetTask.TEST))

        assertEquals(emptyList<String>(), engineCalls)
    }

    @Test
    fun `a transient engine failure is returned unchanged, keeping what was already saved`() {
        val down = LanguageServiceUnavailable("engine down")
        formatResult = Result.Failure(down)

        val result = useCase.execute(job(SnippetTask.LINT, SnippetTask.FORMAT))

        assertEquals(Result.Failure(down), result)
        assertEquals(LintStatus.COMPLIANT, analysis.analyses.getValue(versionId).lintStatus)
        assertEquals(FormatStatus.PENDING, analysis.analyses.getValue(versionId).formatStatus)
    }

    @Test
    fun `a permanent failure is returned too, for the consumer to give the job up`() {
        lintResult = Result.Failure(InvalidInput("Unknown rule 'x'"))

        assertEquals(Result.Failure(InvalidInput("Unknown rule 'x'")), useCase.execute(job(SnippetTask.LINT)))
    }

    private fun addTest(name: String): SnippetTest =
        SnippetTest(UUID.randomUUID(), snippet.id, name, listOf("1", "2"), listOf("3"), NOW).also { tests.add(it) }

    private fun job(vararg tasks: SnippetTask): SnippetJob {
        val reason = JobReason.UPDATED
        return SnippetJob(snippet.id, versionId, tasks.toSet(), reason)
    }

    /** Tells which config the engine got: only the owner has rules. */
    private fun ownerConfig(
        userId: String,
        kind: RuleKind,
    ): Map<String, Any> = if (userId == OWNER) mapOf("kind" to kind.name) else emptyMap()

    private companion object {
        const val OWNER = "auth0|owner"
        const val CODE = "let a: number = 1;"
        const val FORMATTED = "let a: number = 1;\n"
        val NOW: Instant = Instant.parse("2026-11-17T10:00:00Z")
        val ISSUE = CodeIssue("no-println-expressions", "println takes an identifier or a literal", null)
    }
}
