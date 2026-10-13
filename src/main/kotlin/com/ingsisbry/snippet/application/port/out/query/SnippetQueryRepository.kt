package com.ingsisbry.snippet.application.port.out.query

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface SnippetQueryRepository {
    fun findAll(query: ListSnippetsQuery): PageResult<SnippetListItem>

    fun findById(id: UUID): Snippet?
}
