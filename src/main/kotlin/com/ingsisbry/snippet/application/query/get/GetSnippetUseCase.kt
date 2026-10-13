package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service

@Service
class GetSnippetUseCase(
    private val snippetVersionRepository: SnippetQueryRepository,
) : GetSnippet {
    override fun execute(query: GetSnippetQuery): Snippet =
        snippetVersionRepository.findById(query.snippetId)
            ?: throw NoSuchElementException("Snippet ${query.snippetId} not found")
}
