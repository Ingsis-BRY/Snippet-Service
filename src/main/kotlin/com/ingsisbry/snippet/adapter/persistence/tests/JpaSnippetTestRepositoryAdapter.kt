package com.ingsisbry.snippet.adapter.persistence.tests

import com.ingsisbry.snippet.adapter.persistence.json.PersistenceJson
import com.ingsisbry.snippet.adapter.persistence.json.inResult
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.application.query.tests.TestResult
import com.ingsisbry.snippet.domain.test.SnippetTest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Repository
class JpaSnippetTestRepositoryAdapter(
    private val repository: JpaSnippetTestRepository,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : SnippetTestRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun findAll(snippetId: UUID): List<SnippetTestView> =
        repository.findAllBySnippetIdOrderByCreatedAtAscIdAsc(snippetId).map { it.toView() }

    override fun find(
        snippetId: UUID,
        testId: UUID,
    ): SnippetTestView? = repository.findByIdAndSnippetId(testId, snippetId)?.toView()

    override fun count(snippetId: UUID): Int = repository.countBySnippetId(snippetId).toInt()

    override fun save(test: SnippetTest): Result<SnippetError, SnippetTestView> =
        transactionTemplate.inResult(logger, "save test ${test.id}") {
            repository
                .save(
                    SnippetTestEntity(
                        id = test.id,
                        snippetId = test.snippetId,
                        name = test.name,
                        inputs = PersistenceJson.writeStrings(test.inputs),
                        expectedOutputs = PersistenceJson.writeStrings(test.expectedOutputs),
                        status = TestStatus.PENDING.name,
                        actualOutputs = null,
                        error = null,
                        lastRunVersionId = null,
                        lastRunAt = null,
                        createdAt = test.createdAt,
                        updatedAt = Instant.now(clock),
                    ),
                ).toView()
        }

    override fun delete(
        snippetId: UUID,
        testId: UUID,
    ): Result<SnippetError, Unit> =
        transactionTemplate
            .inResult(logger, "delete test $testId") {
                repository.findByIdAndSnippetId(testId, snippetId)?.also { repository.delete(it) } != null
            }.flatMap { deleted ->
                if (deleted) Result.Success(Unit) else Result.Failure(TestNotFound(snippetId, testId))
            }

    override fun markPending(snippetId: UUID): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "mark the tests of $snippetId pending") {
            repository.markPending(snippetId, Instant.now(clock))
            Unit
        }

    override fun saveResult(
        testId: UUID,
        result: TestResult,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "save the result of test $testId") {
            repository.saveResult(
                id = testId,
                status = result.status.name,
                outputs = PersistenceJson.writeStrings(result.actualOutputs),
                error = PersistenceJson.writeIssue(result.error),
                versionId = result.versionId,
                at = result.at,
            )
            Unit
        }

    private fun SnippetTestEntity.toView(): SnippetTestView =
        SnippetTestView(
            test =
                SnippetTest(
                    id = id,
                    snippetId = snippetId,
                    name = name,
                    inputs = PersistenceJson.readStrings(inputs).orEmpty(),
                    expectedOutputs = PersistenceJson.readStrings(expectedOutputs).orEmpty(),
                    createdAt = createdAt,
                ),
            status = TestStatus.valueOf(status),
            actualOutputs = PersistenceJson.readStrings(actualOutputs),
            error = PersistenceJson.readIssue(error),
            lastRunAt = lastRunAt,
        )
}
