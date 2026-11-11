package com.ingsisbry.snippet.application.command.delete

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import java.util.UUID

interface DeleteSnippet {
    fun execute(id: UUID): Result<SnippetError, Unit>
}
