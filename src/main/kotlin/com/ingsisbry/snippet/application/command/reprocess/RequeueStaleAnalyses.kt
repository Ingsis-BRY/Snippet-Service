package com.ingsisbry.snippet.application.command.reprocess

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

/**
 * Publishes again the work of snippets that have been pending for too long: their job was lost (Redis was down
 * when it was published, the API died before publishing it, or Redis lost data). Returns how many were requeued.
 */
fun interface RequeueStaleAnalyses {
    fun execute(): Result<SnippetError, Int>
}
