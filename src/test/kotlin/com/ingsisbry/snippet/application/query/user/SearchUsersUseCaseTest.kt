package com.ingsisbry.snippet.application.query.user

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchUsersUseCaseTest {
    private var lastSearch: List<Any>? = null
    private val page = UserPage(listOf(UserSummary("auth0|2", "Usario Dos")), 0, 10, 1)
    private val directory =
        object : UserDirectory {
            override fun search(
                text: String,
                excludedUserId: String,
                page: Int,
                size: Int,
            ): Result<SnippetError, UserPage> {
                lastSearch = listOf(text, excludedUserId, page, size)
                return Result.Success(this@SearchUsersUseCaseTest.page)
            }

            override fun namesOf(ids: Set<String>): Result<SnippetError, Map<String, String>> {
                val names = ids.associateWith { it }
                return Result.Success(names)
            }
        }
    private val useCase = SearchUsersUseCase(directory)

    @Test
    fun `searches by trimmed name and never lists the requester`() {
        val result = useCase.execute(SearchUsersQuery(requesterId = "auth0|me", name = "  dos "))

        assertEquals(Result.Success(page), result)
        assertEquals(listOf("dos", "auth0|me", 0, 10), lastSearch)
    }

    @Test
    fun `rejects invalid paging and overly long names without searching`() {
        listOf(
            SearchUsersQuery("auth0|me", page = -1) to "Page must be greater than or equal to 0",
            SearchUsersQuery("auth0|me", size = 0) to "Size must be between 1 and 50",
            SearchUsersQuery("auth0|me", size = 51) to "Size must be between 1 and 50",
            SearchUsersQuery("auth0|me", name = "x".repeat(101)) to "Name must be at most 100 characters",
        ).forEach { (query, message) ->
            assertEquals(Result.Failure(InvalidInput(message)), useCase.execute(query))
        }
        assertNull(lastSearch)
    }
}
