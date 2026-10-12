package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult

fun interface ListSnippets {
    fun execute(query: ListSnippetsQuery): PageResult<SnippetListItem>
}
