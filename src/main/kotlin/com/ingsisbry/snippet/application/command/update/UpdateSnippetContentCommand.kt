package com.ingsisbry.snippet.application.command.update

import java.util.UUID

data class UpdateSnippetContentCommand(
    val id: UUID,
    val content: String,
)
