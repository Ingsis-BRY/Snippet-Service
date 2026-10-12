package com.ingsisbry.snippet.application.query.list

data class ListSnippetsQuery(
    val user: String,
    val relationship: SnippetRelationship = SnippetRelationship.ALL,
    val name: String? = null,
    val language: String? = null,
    val valid: Boolean? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: SnippetSortField = SnippetSortField.NAME,
    val direction: SortDirection = SortDirection.ASC,
)
