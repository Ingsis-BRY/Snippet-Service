package com.ingsisbry.snippet.application.port.out.query

import java.util.UUID

/** Snippet data that lives in the database only (no blob content is downloaded to build it). */
data class SnippetMetadata(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val currentVersionNumber: Int,
)
