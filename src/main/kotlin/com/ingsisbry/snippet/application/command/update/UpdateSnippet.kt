package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

interface UpdateSnippet {
    fun execute(
        id: UUID,
        request: UpdateSnippetRequest,
    ): Snippet?
}
