package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ListSnippetsUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    private lateinit var useCase: ListSnippetsUseCase

    @BeforeEach
    fun setUp() {
        useCase = ListSnippetsUseCase(snippetQueryRepository)
    }

    @Test
    fun `execute should return paginated snippets`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                page = 0,
                size = 20,
            )
        val expectedResult =
            PageResult(
                content =
                    listOf(
                        createSnippetListItem(name = "Alpha"),
                        createSnippetListItem(name = "Beta"),
                    ),
                page = 0,
                size = 20,
                totalElements = 2,
                totalPages = 1,
            )

        Mockito
            .`when`(snippetQueryRepository.findAll(query))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query)
    }

    @Test
    fun `execute should forward filters and pagination options to repository`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                relationship = SnippetRelationship.SHARED,
                filters =
                    mapOf(
                        "language" to "Kotlin",
                        "valid" to "true",
                    ),
                page = 2,
                size = 10,
                sortBy = SnippetSortField.LANGUAGE,
                direction = SortDirection.DESC,
            )
        val expectedResult =
            PageResult<SnippetListItem>(
                content = emptyList(),
                page = 2,
                size = 10,
                totalElements = 0,
                totalPages = 0,
            )

        Mockito
            .`when`(snippetQueryRepository.findAll(query))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query)
    }

    @Test
    fun `execute should return empty page when no snippets match`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                filters = mapOf("language" to "Rust"),
            )
        val expectedResult =
            PageResult.empty<SnippetListItem>(
                page = query.page,
                size = query.size,
            )

        Mockito
            .`when`(snippetQueryRepository.findAll(query))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)

        val page = (result as Result.Success).value

        assertEquals(emptyList<SnippetListItem>(), page.content)
        assertEquals(0, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun `execute should accept maximum page size`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                size = 100,
            )
        val expectedResult =
            PageResult.empty<SnippetListItem>(
                page = query.page,
                size = query.size,
            )

        Mockito
            .`when`(snippetQueryRepository.findAll(query))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query)
    }

    @Test
    fun `execute should fail with invalid input when page is negative`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                page = -1,
            )

        val result = useCase.execute(query)

        assertEquals(
            Result.Failure(InvalidInput("Page must be greater than or equal to 0")),
            result,
        )
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `execute should fail with invalid input when size is zero`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                size = 0,
            )

        val result = useCase.execute(query)

        assertEquals(
            Result.Failure(InvalidInput("Size must be between 1 and 100")),
            result,
        )
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `execute should fail with invalid input when size is negative`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                size = -1,
            )

        val result = useCase.execute(query)

        assertEquals(
            Result.Failure(InvalidInput("Size must be between 1 and 100")),
            result,
        )
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `execute should fail with invalid input when size exceeds maximum`() {
        val query =
            ListSnippetsQuery(
                user = "test-user",
                size = 101,
            )

        val result = useCase.execute(query)

        assertEquals(
            Result.Failure(InvalidInput("Size must be between 1 and 100")),
            result,
        )
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    private fun createSnippetListItem(name: String): SnippetListItem =
        SnippetListItem(
            id = UUID.randomUUID(),
            name = name,
            description = "Test snippet",
            language = "Kotlin",
            languageVersion = "2.3",
            owner = "test-user",
            valid = true,
        )
}
