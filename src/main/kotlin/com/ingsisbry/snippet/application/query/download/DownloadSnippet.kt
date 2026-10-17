package com.ingsisbry.snippet.application.query.download

import java.util.UUID

interface DownloadSnippet {
    fun execute(
        id: UUID,
        variant: SnippetContentVariant,
    ): String?
}
