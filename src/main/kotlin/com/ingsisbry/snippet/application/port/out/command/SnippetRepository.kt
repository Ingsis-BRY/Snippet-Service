package com.ingsisbry.snippet.application.port.out.command

import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface SnippetRepository {
    fun save(
        snippet: Snippet,
        originalContentKey: String,
    ): Snippet

    fun deleteById(id: UUID): List<String>?
}
