package com.ingsisbry.snippet.application.command.process

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.SnippetJob

/**
 * Gives up on a job: every task of it that is still pending becomes failed (tests become `ERROR`), so the snippet
 * stops showing as pending and the other snippets go on (US#12, #15).
 */
fun interface FailSnippetJob {
    fun execute(
        job: SnippetJob,
        reason: String,
    ): Result<SnippetError, Unit>
}
