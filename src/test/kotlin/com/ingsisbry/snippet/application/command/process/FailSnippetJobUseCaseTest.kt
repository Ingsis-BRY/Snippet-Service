package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.domain.test.SnippetTest
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetTestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class FailSnippetJobUseCaseTest {
    private val analysis = FakeSnippetAnalysisRepository()
    private val tests = FakeSnippetTestRepository()
    private val useCase = FailSnippetJobUseCase(analysis, tests, Clock.fixed(NOW, ZoneOffset.UTC))
    private val snippetId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()

    @Test
    fun `only the job's tasks that are still pending become failed`() {
        analysis.addSnippet(
            snippetId,
            versionId,
            OWNER,
            SnippetAnalysis.NEW.copy(validationStatus = ValidationStatus.PENDING, lintStatus = LintStatus.COMPLIANT),
        )

        val result = useCase.execute(job(SnippetTask.VALIDATE, SnippetTask.LINT, SnippetTask.FORMAT), "engine down")

        assertEquals(Result.Success(Unit), result)
        val stored = analysis.analyses.getValue(versionId)
        assertEquals(ValidationStatus.FAILED, stored.validationStatus)
        assertEquals(LintStatus.COMPLIANT, stored.lintStatus)
        assertEquals(FormatStatus.FAILED, stored.formatStatus)
    }

    @Test
    fun `tasks outside the job stay pending`() {
        analysis.addSnippet(snippetId, versionId, OWNER)

        useCase.execute(job(SnippetTask.LINT), "engine down")

        assertEquals(LintStatus.FAILED, analysis.analyses.getValue(versionId).lintStatus)
        assertEquals(FormatStatus.PENDING, analysis.analyses.getValue(versionId).formatStatus)
    }

    @Test
    fun `pending targeted tests become errors that say why`() {
        analysis.addSnippet(snippetId, versionId, OWNER)
        val targeted = test()
        val other = test()
        val passed = test(TestStatus.PASSED)

        useCase.execute(job(SnippetTask.TEST).copy(testIds = listOf(targeted.id, passed.id)), "max deliveries exceeded")

        val failed = tests.tests.getValue(targeted.id)
        assertEquals(TestStatus.ERROR, failed.status)
        assertEquals(CodeIssue("ProcessingFailed", "max deliveries exceeded", null), failed.error)
        assertEquals(TestStatus.PENDING, tests.tests.getValue(other.id).status)
        assertEquals(TestStatus.PASSED, tests.tests.getValue(passed.id).status)
    }

    @Test
    fun `a superseded version changes nothing, its tests belong to the newer job`() {
        analysis.addSnippet(snippetId, UUID.randomUUID(), OWNER)
        val pending = test()

        assertEquals(Result.Success(Unit), useCase.execute(job(SnippetTask.TEST), "boom"))
        assertEquals(TestStatus.PENDING, tests.tests.getValue(pending.id).status)
    }

    private fun test(status: TestStatus = TestStatus.PENDING): SnippetTest =
        SnippetTest(UUID.randomUUID(), snippetId, "t", emptyList(), emptyList(), NOW).also { tests.add(it, status) }

    private fun job(vararg tasks: SnippetTask): SnippetJob {
        val reason = JobReason.UPDATED
        return SnippetJob(snippetId, versionId, tasks.toSet(), reason)
    }

    private companion object {
        const val OWNER = "auth0|owner"
        val NOW: Instant = Instant.parse("2026-11-17T10:00:00Z")
    }
}
