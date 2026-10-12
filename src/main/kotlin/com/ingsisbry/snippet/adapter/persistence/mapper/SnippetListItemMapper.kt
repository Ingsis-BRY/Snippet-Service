package com.ingsisbry.snippet.adapter.persistence.mapper

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.application.query.list.SnippetListItem

object SnippetListItemMapper {
    fun toListItem(entity: SnippetEntity): SnippetListItem =
        SnippetListItem(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            language = entity.language,
            languageVersion = entity.languageVersion,
            owner = entity.owner,
            valid = true,
        )
}
