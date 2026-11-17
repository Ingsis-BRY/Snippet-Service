package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import java.util.UUID

fun interface DeleteSnippetTest {
    fun execute(
        snippetId: UUID,
        testId: UUID,
        userId: String,
    ): Result<SnippetError, Unit>
}
