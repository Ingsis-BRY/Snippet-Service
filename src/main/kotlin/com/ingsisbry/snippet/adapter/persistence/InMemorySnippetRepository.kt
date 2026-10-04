package com.ingsisbry.snippet.adapter.persistence

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemorySnippetRepository : SnippetRepository {
    private val snippets = ConcurrentHashMap<UUID, Snippet>()

    override fun save(snippet: Snippet): Snippet {
        snippets[snippet.id] = snippet
        return snippet
    }
}
