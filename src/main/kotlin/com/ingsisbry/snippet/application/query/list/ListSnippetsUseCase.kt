package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.query.SnippetFilterValidator
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ListSnippetsUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val filterValidator: SnippetFilterValidator,
    private val permissions: SnippetPermissions,
    private val userDirectory: UserDirectory,
) : ListSnippets {
    private val logger = LoggerFactory.getLogger(javaClass)

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
            filterValidator
                .validate(query.filters)
                .flatMap { permissions.accessibleSnippetIds(query.user, query.relationship) }
                .map { snippetIds -> withOwnerNames(snippetQueryRepository.findAll(query, snippetIds)) }
        }
    }

    /** One directory lookup for the whole page; if it fails the owner ids are shown instead of failing the list. */
    private fun withOwnerNames(page: PageResult<SnippetListItem>): PageResult<SnippetListItem> {
        if (page.content.isEmpty()) return page
        val names =
            userDirectory
                .namesOf(page.content.map { it.owner }.toSet())
                .onFailure { error -> logger.warn("Could not resolve owner names: {}", error) }
                .fold(onSuccess = { it }, onFailure = { emptyMap() })
        return page.copy(content = page.content.map { it.copy(ownerName = names[it.owner] ?: it.owner) })
    }

    private companion object {
        const val MAXIMUM_SIZE = 100
    }
}
