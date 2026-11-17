package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

/** The created snippet, the same shape as [UpdateSnippetResponse]. */
data class CreateSnippetResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val versionNumber: Int,
    val content: String,
) {
    companion object {
        fun from(snippet: Snippet): CreateSnippetResponse =
            CreateSnippetResponse(
                id = snippet.id,
                name = snippet.name,
                description = snippet.description,
                language = snippet.language,
                languageVersion = snippet.languageVersion,
                owner = snippet.owner,
                versionNumber = snippet.currentVersion.versionNumber,
                content = snippet.currentVersion.originalContent,
            )
    }
}
