package com.ingsisbry.snippet.application.port.out.query

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface SnippetQueryRepository {
    /** Lists, among [snippetIds], the snippets that match the filters of [query]. */
    fun findAll(
        query: ListSnippetsQuery,
        snippetIds: Set<UUID>,
    ): PageResult<SnippetListItem>

    /** Loads the snippet including its content from the content store. */
    fun findById(id: UUID): Result<SnippetError, Snippet>

    /** Loads only what the database knows; never touches the content store. */
    fun findMetadataById(id: UUID): Result<SnippetError, SnippetMetadata>

    /** Returns the subset of [keys] that some snippet version still references. */
    fun findReferencedContentKeys(keys: Collection<String>): Set<String>
}
