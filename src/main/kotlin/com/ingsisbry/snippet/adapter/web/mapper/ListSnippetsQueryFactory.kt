package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
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
    ): Result<SnippetError, ListSnippetsQuery> {
        val filters = parameters.filterKeys { key -> key !in RESERVED_PARAMETERS }

        return parameters
            .enumValueOrDefault("relationship", SnippetRelationship.ALL)
            .flatMap { relationship ->
                parameters.intValueOrDefault("page", DEFAULT_PAGE).flatMap { page ->
                    parameters.intValueOrDefault("size", DEFAULT_SIZE).flatMap { size ->
                        toQuery(user, relationship, filters, page, size, parameters)
                    }
                }
            }
    }

    private fun toQuery(
        user: String,
        relationship: SnippetRelationship,
        filters: Map<String, String>,
        page: Int,
        size: Int,
        parameters: Map<String, String>,
    ): Result<SnippetError, ListSnippetsQuery> =
        parameters
            .enumValueOrDefault("sortBy", SnippetSortField.NAME)
            .flatMap { sortBy ->
                parameters.enumValueOrDefault("direction", SortDirection.ASC).map { direction ->
                    ListSnippetsQuery(
                        user = user,
                        relationship = relationship,
                        filters = filters,
                        page = page,
                        size = size,
                        sortBy = sortBy,
                        direction = direction,
                    )
                }
            }

    private inline fun <reified T : Enum<T>> Map<String, String>.enumValueOrDefault(
        key: String,
        default: T,
    ): Result<SnippetError, T> {
        val value = this[key] ?: return Result.Success(default)

        val match = enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }

        return match
            ?.let { Result.Success(it) }
            ?: Result.Failure(InvalidInput("Invalid value '$value' for parameter '$key'"))
    }

    private fun Map<String, String>.intValueOrDefault(
        key: String,
        default: Int,
    ): Result<SnippetError, Int> {
        val value = this[key] ?: return Result.Success(default)

        return value
            .toIntOrNull()
            ?.let { Result.Success(it) }
            ?: Result.Failure(InvalidInput("Parameter '$key' must be an integer"))
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20

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
