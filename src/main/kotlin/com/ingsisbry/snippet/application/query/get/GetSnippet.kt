package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.domain.Snippet

fun interface GetSnippet {
    fun execute(query: GetSnippetQuery): Snippet
}
