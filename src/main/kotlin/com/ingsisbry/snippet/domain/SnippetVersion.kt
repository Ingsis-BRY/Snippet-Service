package com.ingsisbry.snippet.domain

import java.time.Instant
import java.util.UUID

data class SnippetVersion(
    val id: UUID,
    val snippetId: UUID,
    val versionNumber: Int,
    val originalContent: String,
    val formattedContent: String?,
    val createdAt: Instant,
) {
    companion object {
        fun first(
            id: UUID,
            snippetId: UUID,
            originalContent: String,
            createdAt: Instant,
        ): SnippetVersion =
            SnippetVersion(
                id = id,
                snippetId = snippetId,
                versionNumber = 1,
                originalContent = originalContent,
                formattedContent = null,
                createdAt = createdAt,
            )
    }
}
