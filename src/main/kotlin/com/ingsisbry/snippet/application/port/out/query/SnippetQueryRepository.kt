package com.ingsisbry.snippet.application.port.out.query

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem

interface SnippetQueryRepository {
    fun findAll(query: ListSnippetsQuery): PageResult<SnippetListItem>
}
