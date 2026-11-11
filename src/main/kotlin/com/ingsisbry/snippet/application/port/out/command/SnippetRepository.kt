package com.ingsisbry.snippet.application.port.out.command

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface SnippetRepository {
    fun save(
        snippet: Snippet,
        originalContentKey: String,
    ): Result<SnippetError, Snippet>

    fun update(
        snippet: Snippet,
        originalContentKey: String,
    ): Result<SnippetError, Snippet>

    /** Deletes the snippet and returns the content keys that were referenced by its versions. */
    fun deleteById(id: UUID): Result<SnippetError, List<String>>
}
