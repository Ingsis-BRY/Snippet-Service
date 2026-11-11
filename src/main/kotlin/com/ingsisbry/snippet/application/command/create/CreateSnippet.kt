package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.Snippet

fun interface CreateSnippet {
    fun execute(command: CreateSnippetCommand): Result<SnippetError, Snippet>
}
