package com.ingsisbry.snippet.application.port.out.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.application.query.tests.TestResult
import com.ingsisbry.snippet.domain.test.SnippetTest
import java.util.UUID

interface SnippetTestRepository {
    /** The snippet's tests in creation order. */
    fun findAll(snippetId: UUID): List<SnippetTestView>

    fun find(
        snippetId: UUID,
        testId: UUID,
    ): SnippetTestView?

    fun count(snippetId: UUID): Int

    /** Inserts or updates the definition; the status goes back to PENDING. */
    fun save(test: SnippetTest): Result<SnippetError, SnippetTestView>

    fun delete(
        snippetId: UUID,
        testId: UUID,
    ): Result<SnippetError, Unit>

    fun markPending(snippetId: UUID): Result<SnippetError, Unit>

    fun saveResult(
        testId: UUID,
        result: TestResult,
    ): Result<SnippetError, Unit>
}
