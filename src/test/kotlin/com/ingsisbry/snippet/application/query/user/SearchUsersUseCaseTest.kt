package com.ingsisbry.snippet.application.query.user

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchUsersUseCaseTest {
    private var lastSearch: List<Any>? = null
    private val page = UserPage(listOf(UserSummary("auth0|2", "Usario Dos")), 0, 10, 1)
    private val directory =
        UserDirectory { text, excluded, page, size ->
            lastSearch = listOf(text, excluded, page, size)
            Result.Success(this.page)
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
