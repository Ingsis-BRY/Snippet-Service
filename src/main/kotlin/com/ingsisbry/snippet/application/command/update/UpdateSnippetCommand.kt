package com.ingsisbry.snippet.application.command.update

import java.util.UUID

data class UpdateSnippetCommand(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val content: String,
)
