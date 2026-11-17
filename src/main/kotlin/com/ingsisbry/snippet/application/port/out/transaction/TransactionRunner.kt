package com.ingsisbry.snippet.application.port.out.transaction

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** Runs several repository calls as one database transaction. */
interface TransactionRunner {
    /** Commits when [block] succeeds; rolls everything back when it returns a failure or throws. */
    fun <T> inTransaction(block: () -> Result<SnippetError, T>): Result<SnippetError, T>
}
