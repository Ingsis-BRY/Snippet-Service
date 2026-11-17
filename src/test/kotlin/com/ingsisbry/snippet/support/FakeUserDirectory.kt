package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import com.ingsisbry.snippet.application.query.user.UserPage

/** Knows the names in [names]; any other id maps to itself, like the real directory. */
class FakeUserDirectory(
    var names: Map<String, String> = emptyMap(),
    var failure: SnippetError? = null,
) : UserDirectory {
    val lookups = mutableListOf<Set<String>>()

    override fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        size: Int,
    ): Result<SnippetError, UserPage> = Result.Success(UserPage(emptyList(), page, size, 0))

    override fun namesOf(ids: Set<String>): Result<SnippetError, Map<String, String>> {
        lookups += ids
        val error = failure
        return if (error != null) Result.Failure(error) else Result.Success(ids.associateWith { names[it] ?: it })
    }
}
