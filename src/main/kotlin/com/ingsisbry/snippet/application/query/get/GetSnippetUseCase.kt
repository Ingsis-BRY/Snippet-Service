package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service

@Service
class GetSnippetUseCase(
    private val repository: SnippetQueryRepository,
) : GetSnippet {
    override fun execute(query: GetSnippetQuery): Result<SnippetError, Snippet> = repository.findById(query.snippetId)
}
