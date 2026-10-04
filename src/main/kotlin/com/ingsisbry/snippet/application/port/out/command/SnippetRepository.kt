package com.ingsisbry.snippet.application.port.out.command

import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface SnippetRepository {
    fun save(snippet: Snippet): Snippet

    fun findById(id: UUID): Snippet?
}
