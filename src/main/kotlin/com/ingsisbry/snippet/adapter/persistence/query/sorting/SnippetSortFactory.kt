package com.ingsisbry.snippet.adapter.persistence.query.sorting

import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection
import org.springframework.data.domain.Sort

object SnippetSortFactory {
    fun create(
        sortBy: SnippetSortField,
        direction: SortDirection,
    ): Sort {
        val property =
            when (sortBy) {
                SnippetSortField.NAME -> "name"
                SnippetSortField.LANGUAGE -> "language"
                // alphabetical on the stored status: COMPLIANT, FAILED, NOT_COMPLIANT, PENDING
                SnippetSortField.VALID -> "currentVersion.lintStatus"
            }

        val sortDirection =
            when (direction) {
                SortDirection.ASC -> Sort.Direction.ASC
                SortDirection.DESC -> Sort.Direction.DESC
            }

        return Sort
            .by(sortDirection, property)
            .and(Sort.by(Sort.Direction.ASC, "id"))
    }
}
