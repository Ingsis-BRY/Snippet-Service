package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.Snippet

interface UpdateSnippet {
    fun execute(command: UpdateSnippetCommand): Result<SnippetError, Snippet>
}
