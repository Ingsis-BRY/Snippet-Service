package com.ingsisbry.snippet.application.port.out.command

import com.ingsisbry.snippet.domain.Snippet

interface SnippetRepository {
    fun save(snippet: Snippet): Snippet
}