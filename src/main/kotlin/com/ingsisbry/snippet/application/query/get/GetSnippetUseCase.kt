package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service

@Service
class GetSnippetUseCase(
    private val snippetRepository: SnippetRepository,
) : GetSnippet {
    override fun execute(query: GetSnippetQuery): Snippet =
        snippetRepository.findById(query.snippetId)
            ?: throw NoSuchElementException("Snippet ${query.snippetId} not found")
}
