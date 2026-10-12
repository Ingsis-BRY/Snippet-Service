package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection
import org.springframework.stereotype.Component

@Component
class ListSnippetsQueryFactory {
    fun create(
        parameters: Map<String, String>,
        user: String,
    ): ListSnippetsQuery {
        val filters =
            parameters.filterKeys { key ->
                key !in RESERVED_PARAMETERS
            }

        val defaultPage = 0
        val defaultSize = 20

        return ListSnippetsQuery(
            user = user,
            relationship =
                parameters.enumValueOrDefault(
                    key = "relationship",
                    default = SnippetRelationship.ALL,
                ),
            filters = filters,
            page = parameters.intValueOrDefault("page", defaultPage),
            size = parameters.intValueOrDefault("size", defaultSize),
            sortBy =
                parameters.enumValueOrDefault(
                    key = "sortBy",
                    default = SnippetSortField.NAME,
                ),
            direction =
                parameters.enumValueOrDefault(
                    key = "direction",
                    default = SortDirection.ASC,
                ),
        )
    }

    private inline fun <reified T : Enum<T>> Map<String, String>.enumValueOrDefault(
        key: String,
        default: T,
    ): T =
        this[key]?.let { value ->
            enumValues<T>().firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "Invalid value '$value' for parameter '$key'",
            )
        } ?: default

    private fun Map<String, String>.intValueOrDefault(
        key: String,
        default: Int,
    ): Int =
        this[key]?.toIntOrNull()
            ?: if (containsKey(key)) {
                throw IllegalArgumentException(
                    "Parameter '$key' must be an integer",
                )
            } else {
                default
            }

    private companion object {
        val RESERVED_PARAMETERS =
            setOf(
                "relationship",
                "page",
                "size",
                "sortBy",
                "direction",
            )
    }
}
