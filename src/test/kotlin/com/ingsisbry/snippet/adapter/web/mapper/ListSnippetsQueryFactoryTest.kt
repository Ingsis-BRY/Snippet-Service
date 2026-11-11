package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListSnippetsQueryFactoryTest {
    private val factory = ListSnippetsQueryFactory()

    @Test
    fun `defaults are used when no parameters are given`() {
        val result = factory.create(emptyMap(), "anonymous")

        assertEquals(Result.Success(ListSnippetsQuery(user = "anonymous")), result)
    }

    @Test
    fun `reserved parameters are parsed and the rest become filters`() {
        val result =
            factory.create(
                mapOf(
                    "relationship" to "owned",
                    "page" to "2",
                    "size" to "5",
                    "sortBy" to "language",
                    "direction" to "desc",
                    "language" to "Java",
                ),
                "anonymous",
            )

        assertEquals(
            Result.Success(
                ListSnippetsQuery(
                    user = "anonymous",
                    relationship = SnippetRelationship.OWNED,
                    filters = mapOf("language" to "Java"),
                    page = 2,
                    size = 5,
                    sortBy = SnippetSortField.LANGUAGE,
                    direction = SortDirection.DESC,
                ),
            ),
            result,
        )
    }

    @Test
    fun `a non integer page is an invalid input`() {
        val result = factory.create(mapOf("page" to "abc"), "anonymous")

        assertEquals(Result.Failure(InvalidInput("Parameter 'page' must be an integer")), result)
    }

    @Test
    fun `an unknown enum value is an invalid input`() {
        val result = factory.create(mapOf("direction" to "sideways"), "anonymous")

        assertEquals(
            Result.Failure(InvalidInput("Invalid value 'sideways' for parameter 'direction'")),
            result,
        )
    }
}
