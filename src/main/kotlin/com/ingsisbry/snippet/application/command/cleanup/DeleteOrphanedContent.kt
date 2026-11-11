package com.ingsisbry.snippet.application.command.cleanup

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface DeleteOrphanedContent {
    /** Deletes stored blobs no snippet version references; returns how many were deleted. */
    fun execute(): Result<SnippetError, Int>
}
