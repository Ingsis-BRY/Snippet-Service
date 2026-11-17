package com.ingsisbry.snippet.adapter.persistence.transaction

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/** The repository adapters' own transactions join this one, so their writes commit or roll back together. */
@Component
class SpringTransactionRunner(
    private val transactionTemplate: TransactionTemplate,
) : TransactionRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Suppress("TooGenericExceptionCaught")
    override fun <T> inTransaction(block: () -> Result<SnippetError, T>): Result<SnippetError, T> =
        try {
            transactionTemplate.execute { status ->
                block().also { result -> if (result is Result.Failure) status.setRollbackOnly() }
            } ?: Result.Failure(PersistenceUnavailable("The transaction returned no result"))
        } catch (exception: RuntimeException) {
            logger.error("Transaction failed", exception)
            Result.Failure(PersistenceUnavailable(exception.message))
        }
}
