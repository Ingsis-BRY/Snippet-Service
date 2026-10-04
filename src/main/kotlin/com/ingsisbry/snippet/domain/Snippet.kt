package com.ingsisbry.snippet.domain

import java.time.Instant
import java.util.UUID

data class Snippet(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val currentVersion: SnippetVersion,
) {
    companion object {
        fun create(
            id: UUID,
            name: String,
            description: String,
            language: String,
            languageVersion: String,
            owner: String,
            content: String,
            versionId: UUID,
            createdAt: Instant,
        ): Snippet {
            val firstVersion = SnippetVersion.first(
                id = versionId,
                snippetId = id,
                originalContent = content,
                createdAt = createdAt,
            )

            return Snippet(
                id = id,
                name = name,
                description = description,
                language = language,
                languageVersion = languageVersion,
                owner = owner,
                currentVersion = firstVersion,
            )
        }
    }
}