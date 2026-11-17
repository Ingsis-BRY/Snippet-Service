package com.ingsisbry.snippet.application.port.out.worker

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/** Small key/value state the workers share through the database (e.g. the last parser version they saw). */
interface WorkerStateRepository {
    /** Null when the key was never set. */
    fun get(key: String): Result<SnippetError, String?>

    /**
     * Sets [key] to [new] only if it still holds [expected] (null = the key must not exist yet). Success(false)
     * means another worker changed it first.
     */
    fun compareAndSet(
        key: String,
        expected: String?,
        new: String,
    ): Result<SnippetError, Boolean>
}
