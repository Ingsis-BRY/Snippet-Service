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

        require(query.size >= 1) {
            "Size must be greater than or equal to 1"
        }

        return snippetQueryRepository.findAll(query)
    }
}
