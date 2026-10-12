package com.ingsisbry.snippet.adapter.web.dto

import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection

data class ListSnippetsRequest(
    val relationship: SnippetRelationship = SnippetRelationship.ALL,
    val name: String? = null,
    val language: String? = null,
    val valid: Boolean? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: SnippetSortField = SnippetSortField.NAME,
    val direction: SortDirection = SortDirection.ASC,
)
