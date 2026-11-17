package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.tests.SnippetTestView

/** Adds a test to a snippet (US#8); only who can write the snippet can. */
fun interface CreateSnippetTest {
    fun execute(command: CreateSnippetTestCommand): Result<SnippetError, SnippetTestView>
}
