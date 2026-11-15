package com.ingsisbry.snippet.adapter.persistence.query.specification

import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object SnippetAccessSpecifications {
    /** Restricts the query to the snippets the permission service says the user can access. */
    fun idIn(snippetIds: Set<UUID>): Specification<SnippetEntity> {
        val spec = Specification<SnippetEntity> { root, _, _ -> root.get<UUID>("id").`in`(snippetIds) }
        return spec
    }
}
