package com.ingsisbry.snippet.application.query.user

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface SearchUsers {
    fun execute(query: SearchUsersQuery): Result<SnippetError, UserPage>
}
