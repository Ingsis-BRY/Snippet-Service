package com.ingsisbry.snippet.application.port.out.user

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.user.UserPage

fun interface UserDirectory {
    fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        size: Int,
    ): Result<SnippetError, UserPage>
}
