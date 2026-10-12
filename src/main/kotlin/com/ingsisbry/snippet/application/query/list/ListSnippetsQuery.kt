package com.ingsisbry.snippet.application.query.list

data class ListSnippetsQuery(
    val user: String,
    val relationship: SnippetRelationship = SnippetRelationship.ALL,
    val filters: Map<String, String> = emptyMap(),
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: SnippetSortField = SnippetSortField.NAME,
    val direction: SortDirection = SortDirection.ASC,
)
