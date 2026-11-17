package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetJob

enum class JobOutcome {
    /** Every task ran and its result is stored. */
    PROCESSED,

    /** The snippet is gone or has a newer version, which has a job of its own. */
    SKIPPED,
}

/**
 * Runs the background tasks of one job: re-validation, lint, format and tests (US#12, #15, #16).
 *
 * Each result is stored as soon as it is known, so a crash keeps the progress, and running the same job again
 * gives the same rows and blobs. A failure is returned as it is: the caller retries it if it is transient.
 */
fun interface ProcessSnippetJob {
    fun execute(job: SnippetJob): Result<SnippetError, JobOutcome>
}
