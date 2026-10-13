package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.domain.SnippetVersion
import java.time.Instant
import java.util.UUID

data class SnippetVersionResponse(
    val id: UUID,
    val snippetId: UUID,
    val versionNumber: Int,
    val originalContent: String,
    val formattedContent: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(version: SnippetVersion): SnippetVersionResponse =
            SnippetVersionResponse(
                id = version.id,
                snippetId = version.snippetId,
                versionNumber = version.versionNumber,
                originalContent = version.originalContent,
                formattedContent = version.formattedContent,
                createdAt = version.createdAt,
            )
    }
}
