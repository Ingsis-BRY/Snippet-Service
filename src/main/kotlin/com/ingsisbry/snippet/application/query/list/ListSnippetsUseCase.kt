package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.query.SnippetFilterValidator
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import org.springframework.stereotype.Service

@Service
class ListSnippetsUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val filterValidator: SnippetFilterValidator,
) : ListSnippets {
    override fun execute(query: ListSnippetsQuery): Result<SnippetError, PageResult<SnippetListItem>> {
        val violation =
            when {
                query.page < 0 -> "Page must be greater than or equal to 0"
                query.size !in 1..MAXIMUM_SIZE -> "Size must be between 1 and $MAXIMUM_SIZE"
                else -> null
            }

        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            filterValidator.validate(query.filters).map { snippetQueryRepository.findAll(query) }
        }
    }

    private companion object {
        const val MAXIMUM_SIZE = 100
    }
}
