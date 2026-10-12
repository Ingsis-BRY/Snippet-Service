package com.ingsisbry.snippet.application.query.list

import java.util.UUID

data class SnippetListItem(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val valid: Boolean,
)
