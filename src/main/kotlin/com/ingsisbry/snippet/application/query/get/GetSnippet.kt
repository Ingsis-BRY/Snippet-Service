package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.Snippet

fun interface GetSnippet {
    fun execute(query: GetSnippetQuery): Result<SnippetError, Snippet>
}
