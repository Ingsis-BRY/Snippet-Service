package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import java.util.UUID

interface DownloadSnippet {
    fun execute(
        id: UUID,
        variant: SnippetContentVariant,
    ): Result<SnippetError, String>
}
