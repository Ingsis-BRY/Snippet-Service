package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface ListSnippets {
    fun execute(query: ListSnippetsQuery): Result<SnippetError, PageResult<SnippetListItem>>
}
