package com.ingsisbry.snippet.application.command.share

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError

fun interface ShareSnippet {
    fun execute(command: ShareSnippetCommand): Result<SnippetError, Unit>
}
