package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.tests.SnippetTestView

/** Replaces a test's definition; its result goes back to pending until it runs again. */
fun interface UpdateSnippetTest {
    fun execute(command: UpdateSnippetTestCommand): Result<SnippetError, SnippetTestView>
}
