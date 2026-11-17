package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.application.query.tests.TestResult
import com.ingsisbry.snippet.domain.test.SnippetTest
import java.util.UUID

/** In-memory [SnippetTestRepository], in insertion order. */
class FakeSnippetTestRepository : SnippetTestRepository {
    val tests = linkedMapOf<UUID, SnippetTestView>()
    val markedPending = mutableListOf<UUID>()

    fun add(
        test: SnippetTest,
        status: TestStatus = TestStatus.PENDING,
    ): SnippetTestView = SnippetTestView(test, status, null, null, null).also { tests[test.id] = it }

    override fun findAll(snippetId: UUID): List<SnippetTestView> {
        val all = tests.values
        return all.filter { it.test.snippetId == snippetId }
    }

    override fun find(
        snippetId: UUID,
        testId: UUID,
    ): SnippetTestView? = tests[testId]?.takeIf { it.test.snippetId == snippetId }

    override fun count(snippetId: UUID): Int = findAll(snippetId).size

    override fun save(test: SnippetTest): Result<SnippetError, SnippetTestView> = Result.Success(add(test))

    override fun delete(
        snippetId: UUID,
        testId: UUID,
    ): Result<SnippetError, Unit> =
        if (find(snippetId, testId) == null) {
            Result.Failure(TestNotFound(snippetId, testId))
        } else {
            tests.remove(testId)
            Result.Success(Unit)
        }

    override fun markPending(snippetId: UUID): Result<SnippetError, Unit> {
        markedPending += snippetId
        findAll(snippetId).forEach { tests[it.test.id] = it.copy(status = TestStatus.PENDING) }
        return Result.Success(Unit)
    }

    override fun saveResult(
        testId: UUID,
        result: TestResult,
    ): Result<SnippetError, Unit> {
        tests[testId]?.let {
            tests[testId] = it.copy(status = result.status, actualOutputs = result.actualOutputs, error = result.error)
        }
        return Result.Success(Unit)
    }
}
