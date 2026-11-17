package com.ingsisbry.snippet.adapter.persistence.query.mapper

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.query.list.SnippetListItem

object SnippetListItemMapper {
    /** The owner's name is resolved later, by the use case; until then it is the owner id. */
    fun toListItem(
        entity: SnippetEntity,
        analysis: SnippetAnalysis?,
    ): SnippetListItem {
        val current = analysis ?: SnippetAnalysis.NEW
        return SnippetListItem(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            language = entity.language,
            languageVersion = entity.languageVersion,
            owner = entity.owner,
            ownerName = entity.owner,
            compliance = current.lintStatus,
            processing = current.processing(),
        )
    }
}
