package com.ingsisbry.snippet.adapter.persistence.json

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import org.slf4j.Logger
import org.springframework.transaction.support.TransactionTemplate

/** Runs [block] in a transaction; any database failure becomes [PersistenceUnavailable]. */
@Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
internal fun <T> TransactionTemplate.inResult(
    logger: Logger,
    operation: String,
    block: () -> T,
): Result<SnippetError, T> =
    try {
        Result.Success(execute { block() } as T)
    } catch (exception: RuntimeException) {
        logger.error("Database operation {} failed", operation, exception)
        Result.Failure(PersistenceUnavailable(exception.message))
    }
