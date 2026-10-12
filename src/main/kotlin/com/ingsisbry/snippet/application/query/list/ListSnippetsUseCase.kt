package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import org.springframework.stereotype.Service

@Service
class ListSnippetsUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
) : ListSnippets {
    override fun execute(query: ListSnippetsQuery): PageResult<SnippetListItem> {
        require(query.page >= 0) {
            "Page must be greater than or equal to 0"
        }

        val maximumSize = 100
        require(query.size in 1..maximumSize) {
            "Size must be between 1 and 100"
        }

        return snippetQueryRepository.findAll(query)
    }
}
