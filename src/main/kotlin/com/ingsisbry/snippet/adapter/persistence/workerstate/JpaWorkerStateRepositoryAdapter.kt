package com.ingsisbry.snippet.adapter.persistence.workerstate

import com.ingsisbry.snippet.adapter.persistence.json.inResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.worker.WorkerStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * Joins the caller's transaction when there is one. A concurrent insert of the same key fails on the primary key;
 * the caller gets a failure and finds the value already set on its next check.
 */
@Repository
class JpaWorkerStateRepositoryAdapter(
    private val repository: JpaWorkerStateRepository,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : WorkerStateRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun get(key: String): Result<SnippetError, String?> =
        transactionTemplate.inResult(logger, "read worker state $key") {
            repository.findById(key).map { it.stateValue }.orElse(null)
        }

    override fun compareAndSet(
        key: String,
        expected: String?,
        new: String,
    ): Result<SnippetError, Boolean> =
        transactionTemplate.inResult(logger, "set worker state $key") {
            val at = Instant.now(clock)
            val changed =
                if (expected == null) {
                    if (repository.existsById(key)) 0 else repository.insert(key, new, at)
                } else {
                    repository.replaceIf(key, expected, new, at)
                }
            changed == 1
        }
}
