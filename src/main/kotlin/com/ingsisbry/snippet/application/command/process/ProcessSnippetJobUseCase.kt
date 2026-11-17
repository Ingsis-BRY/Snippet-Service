package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.language.SnippetLinter
import com.ingsisbry.snippet.application.port.out.language.SnippetTestRunner
import com.ingsisbry.snippet.application.port.out.language.TestOutcome
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfig
import com.ingsisbry.snippet.application.query.tests.TestResult
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Lint and format use the snippet owner's rules, as everywhere else. */
@Service
@Suppress("LongParameterList")
class ProcessSnippetJobUseCase(
    private val analysis: SnippetAnalysisRepository,
    private val snippets: SnippetQueryRepository,
    private val validator: SnippetContentValidator,
    private val linter: SnippetLinter,
    private val formatter: SnippetFormatter,
    private val testRunner: SnippetTestRunner,
    private val tests: SnippetTestRepository,
    private val contentStorage: SnippetContentStorage,
    private val ruleConfig: ResolveRuleConfig,
    private val clock: Clock,
) : ProcessSnippetJob {
    override fun execute(job: SnippetJob): Result<SnippetError, JobOutcome> =
        if (analysis.currentVersionId(job.snippetId) != job.versionId) {
            Result.Success(JobOutcome.SKIPPED)
        } else {
            when (val loaded = snippets.findById(job.snippetId)) {
                is Result.Failure ->
                    if (loaded.error is SnippetNotFound) Result.Success(JobOutcome.SKIPPED) else loaded
                is Result.Success ->
                    if (loaded.value.currentVersion.id == job.versionId) {
                        process(job, loaded.value)
                    } else {
                        Result.Success(JobOutcome.SKIPPED)
                    }
            }
        }

    private fun process(
        job: SnippetJob,
        snippet: Snippet,
    ): Result<SnippetError, JobOutcome> =
        validate(job, snippet).flatMap { parses ->
            // code that no longer parses is neither linted nor formatted; validate already stored why
            val tasks = if (parses) job.tasks else job.tasks - setOf(SnippetTask.LINT, SnippetTask.FORMAT)
            whenTask(SnippetTask.LINT in tasks) { lint(snippet) }
                .flatMap { whenTask(SnippetTask.FORMAT in tasks) { format(snippet) } }
                .flatMap { whenTask(SnippetTask.TEST in tasks) { test(snippet, job.testIds) } }
                .map { JobOutcome.PROCESSED }
        }

    /** Success(false) when the code breaks a rule of the language (e.g. after a parser upgrade). */
    private fun validate(
        job: SnippetJob,
        snippet: Snippet,
    ): Result<SnippetError, Boolean> {
        val version = snippet.currentVersion
        return if (SnippetTask.VALIDATE !in job.tasks) {
            Result.Success(true)
        } else {
            when (val result = validator.validate(snippet.language, snippet.languageVersion, version.originalContent)) {
                is Result.Success -> analysis.saveValidation(version.id, ValidationStatus.VALID, null).map { true }
                is Result.Failure -> {
                    val error = result.error
                    if (error is InvalidSnippetContent) {
                        markInvalid(version.id, CodeIssue(error.rule, error.message, error.span)).map { false }
                    } else {
                        result
                    }
                }
            }
        }
    }

    /** Invalid code is not compliant and has no formatted form, so the list needs one column to tell validity. */
    private fun markInvalid(
        versionId: UUID,
        issue: CodeIssue,
    ): Result<SnippetError, Unit> =
        analysis
            .saveValidation(versionId, ValidationStatus.INVALID, issue)
            .flatMap { analysis.saveLint(versionId, LintStatus.NOT_COMPLIANT, emptyList()) }
            .flatMap { analysis.saveFormat(versionId, FormatStatus.FAILED, null) }

    private fun lint(snippet: Snippet): Result<SnippetError, Unit> =
        linter
            .lint(
                snippet.language,
                snippet.languageVersion,
                snippet.currentVersion.originalContent,
                ruleConfig.execute(snippet.owner, RuleKind.LINT),
            ).flatMap { outcome ->
                val status = if (outcome.valid) LintStatus.COMPLIANT else LintStatus.NOT_COMPLIANT
                analysis.saveLint(snippet.currentVersion.id, status, outcome.issues)
            }

    private fun format(snippet: Snippet): Result<SnippetError, Unit> {
        val version = snippet.currentVersion
        return formatter
            .format(
                snippet.language,
                snippet.languageVersion,
                version.originalContent,
                ruleConfig.execute(snippet.owner, RuleKind.FORMAT),
            ).flatMap { outcome ->
                if (outcome.error != null) {
                    analysis.saveFormat(version.id, FormatStatus.FAILED, null)
                } else {
                    // the same key every time: running the job again overwrites the blob instead of leaking one
                    val key = formattedContentKey(snippet.id, version.id)
                    contentStorage
                        .store(key, outcome.formatted ?: version.originalContent)
                        .flatMap { analysis.saveFormat(version.id, FormatStatus.FINISHED, key) }
                }
            }
    }

    private fun test(
        snippet: Snippet,
        testIds: List<UUID>,
    ): Result<SnippetError, Unit> {
        val selected =
            tests
                .findAll(snippet.id)
                .map { it.test }
                .filter { testIds.isEmpty() || it.id in testIds }
        return if (selected.isEmpty()) {
            Result.Success(Unit)
        } else {
            testRunner
                .run(
                    snippet.language,
                    snippet.languageVersion,
                    snippet.currentVersion.originalContent,
                    selected.map { TestSpec(it.id.toString(), it.inputs, it.expectedOutputs) },
                ).flatMap { outcomes -> saveTestResults(snippet.currentVersion.id, outcomes) }
        }
    }

    private fun saveTestResults(
        versionId: UUID,
        outcomes: List<TestOutcome>,
    ): Result<SnippetError, Unit> {
        val at = Instant.now(clock)
        val initial: Result<SnippetError, Unit> = Result.Success(Unit)
        return outcomes.fold(initial) { previous, outcome ->
            previous.flatMap {
                tests.saveResult(
                    UUID.fromString(outcome.id),
                    TestResult(outcome.status(), outcome.actualOutputs, outcome.error, versionId, at),
                )
            }
        }
    }

    private fun TestOutcome.status(): TestStatus =
        when {
            passed -> TestStatus.PASSED
            error != null -> TestStatus.ERROR
            else -> TestStatus.FAILED
        }

    private fun whenTask(
        enabled: Boolean,
        task: () -> Result<SnippetError, Unit>,
    ): Result<SnippetError, Unit> = if (enabled) task() else Result.Success(Unit)

    companion object {
        /** Where the worker stores the formatted content of a version. */
        fun formattedContentKey(
            snippetId: UUID,
            versionId: UUID,
        ): String = "snippets/$snippetId/versions/$versionId/formatted"
    }
}
