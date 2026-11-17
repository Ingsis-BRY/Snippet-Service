package com.ingsisbry.snippet.adapter.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.ingsisbry.snippet.application.query.get.SnippetDetails
import java.util.UUID

/** The detail page (US#6): the snippet, who owns it and what the background analysis found. */
data class GetSnippetResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val ownerName: String,
    /** `isOwner` in JSON; named apart from [owner] so Jackson keeps both. */
    @get:JsonProperty("isOwner")
    val ownedByCaller: Boolean,
    val currentVersion: SnippetVersionResponse,
    val compliance: String,
    val lintIssues: List<IssueResponse>,
    val validationStatus: String,
    val validationError: IssueResponse?,
    val formatStatus: String,
    val processing: String,
) {
    companion object {
        fun from(details: SnippetDetails): GetSnippetResponse {
            val snippet = details.snippet
            val analysis = details.analysis
            return GetSnippetResponse(
                id = snippet.id,
                name = snippet.name,
                description = snippet.description,
                language = snippet.language,
                languageVersion = snippet.languageVersion,
                owner = snippet.owner,
                ownerName = details.ownerName,
                ownedByCaller = details.isOwner,
                currentVersion = SnippetVersionResponse.from(snippet.currentVersion),
                compliance = analysis.lintStatus.toJsonValue(),
                lintIssues = analysis.lintIssues.map(IssueResponse::from),
                validationStatus = analysis.validationStatus.toJsonValue(),
                validationError = analysis.validationError?.let(IssueResponse::from),
                formatStatus = analysis.formatStatus.toJsonValue(),
                processing = analysis.processing().toJsonValue(),
            )
        }
    }
}
