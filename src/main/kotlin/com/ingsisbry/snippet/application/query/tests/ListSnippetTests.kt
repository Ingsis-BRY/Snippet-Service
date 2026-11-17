package com.ingsisbry.snippet.application.query.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import java.util.UUID

/** The snippet's tests; anyone who can read the snippet can see them (US#7, #8). */
fun interface ListSnippetTests {
    fun execute(
        snippetId: UUID,
        userId: String,
    ): Result<SnippetError, List<SnippetTestView>>
}
