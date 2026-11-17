package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.TestResult
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/** Only pending results change: whatever the job already finished stays. */
@Service
class FailSnippetJobUseCase(
    private val analysis: SnippetAnalysisRepository,
    private val tests: SnippetTestRepository,
    private val clock: Clock,
) : FailSnippetJob {
    override fun execute(
        job: SnippetJob,
        reason: String,
    ): Result<SnippetError, Unit> =
        // a superseded version is history: its tests now belong to the newer version's job
        if (analysis.currentVersionId(job.snippetId) != job.versionId) {
            Result.Success(Unit)
        } else {
            analysis
                .find(job.versionId)
                .flatMap { current -> failAnalysis(job, current) }
                .flatMap { failTests(job, reason) }
        }

    private fun failAnalysis(
        job: SnippetJob,
        current: SnippetAnalysis,
    ): Result<SnippetError, Unit> {
        val tasks = job.tasks
        val ok: Result<SnippetError, Unit> = Result.Success(Unit)
        return ok
            .flatMap {
                if (SnippetTask.VALIDATE in tasks && current.validationStatus == ValidationStatus.PENDING) {
                    analysis.saveValidation(job.versionId, ValidationStatus.FAILED, null)
                } else {
                    ok
                }
            }.flatMap {
                if (SnippetTask.LINT in tasks && current.lintStatus == LintStatus.PENDING) {
                    analysis.saveLint(job.versionId, LintStatus.FAILED, emptyList())
                } else {
                    ok
                }
            }.flatMap {
                if (SnippetTask.FORMAT in tasks && current.formatStatus == FormatStatus.PENDING) {
                    analysis.saveFormat(job.versionId, FormatStatus.FAILED, null)
                } else {
                    ok
                }
            }
    }

    private fun failTests(
        job: SnippetJob,
        reason: String,
    ): Result<SnippetError, Unit> {
        val initial: Result<SnippetError, Unit> = Result.Success(Unit)
        if (SnippetTask.TEST !in job.tasks) return initial
        val at = Instant.now(clock)
        val issue = CodeIssue(rule = PROCESSING_FAILED, message = reason, span = null)
        return tests
            .findAll(job.snippetId)
            .filter { it.status == TestStatus.PENDING }
            .filter { job.testIds.isEmpty() || it.test.id in job.testIds }
            .fold(initial) { previous, view ->
                previous.flatMap {
                    tests.saveResult(view.test.id, TestResult(TestStatus.ERROR, emptyList(), issue, job.versionId, at))
                }
            }
    }

    companion object {
        /** The rule of the issue a test gets when its run was given up. */
        const val PROCESSING_FAILED = "ProcessingFailed"
    }
}
