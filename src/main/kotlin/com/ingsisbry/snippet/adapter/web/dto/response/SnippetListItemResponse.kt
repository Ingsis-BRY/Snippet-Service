package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import java.util.UUID

/** [valid] is whether the current version complies with its owner's lint rules. */
data class SnippetListItemResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val ownerName: String,
    val valid: Boolean,
    val compliance: String,
    val processing: String,
) {
    companion object {
        fun from(item: SnippetListItem): SnippetListItemResponse =
            SnippetListItemResponse(
                id = item.id,
                name = item.name,
                description = item.description,
                language = item.language,
                languageVersion = item.languageVersion,
                owner = item.owner,
                ownerName = item.ownerName,
                valid = item.compliance == LintStatus.COMPLIANT,
                compliance = item.compliance.toJsonValue(),
                processing = item.processing.toJsonValue(),
            )
    }
}
