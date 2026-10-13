package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.domain.Snippet
import java.util.UUID

data class GetSnippetResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val currentVersion: SnippetVersionResponse,
) {
    companion object {
        fun from(snippet: Snippet): GetSnippetResponse =
            GetSnippetResponse(
                id = snippet.id,
                name = snippet.name,
                description = snippet.description,
                language = snippet.language,
                languageVersion = snippet.languageVersion,
                owner = snippet.owner,
                currentVersion = SnippetVersionResponse.from(snippet.currentVersion),
            )
    }
}
