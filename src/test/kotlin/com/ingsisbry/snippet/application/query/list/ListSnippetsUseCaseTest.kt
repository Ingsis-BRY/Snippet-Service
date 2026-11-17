package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.ProcessingStatus
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.PermissionServiceUnavailable
import com.ingsisbry.snippet.application.error.UserDirectoryUnavailable
import com.ingsisbry.snippet.application.port.out.query.SnippetFilterValidator
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.FakeUserDirectory
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

    @Mock
    private lateinit var filterValidator: SnippetFilterValidator

    private lateinit var useCase: ListSnippetsUseCase

    private val accessibleIds = setOf(UUID.randomUUID(), UUID.randomUUID())
    private val permissions = FakeSnippetPermissions(accessibleIds = accessibleIds)
    private val directory = FakeUserDirectory(names = mapOf(OLGA to "Olga Owner"))

    @BeforeEach
    fun setUp() {
        useCase = ListSnippetsUseCase(snippetQueryRepository, filterValidator, permissions, directory)
        Mockito.lenient().`when`(filterValidator.validate(Mockito.anyMap())).thenReturn(Result.Success(Unit))
    }

    @Test
    fun `execute should list only the snippets the user can access with the requested relationship`() {
        val query = ListSnippetsQuery(user = "test-user", relationship = SnippetRelationship.SHARED)
        val expected = PageResult.empty<SnippetListItem>(page = 0, size = 20)

        Mockito.`when`(snippetQueryRepository.findAll(query, accessibleIds)).thenReturn(expected)

        assertEquals(Result.Success(expected), useCase.execute(query))
        assertEquals(SnippetRelationship.SHARED, permissions.lastRelationship)
    }

    @Test
    fun `execute should fail without querying when the permission service is unavailable`() {
        permissions.failure = PermissionServiceUnavailable("down")

        val result = useCase.execute(ListSnippetsQuery(user = "test-user"))

        assertEquals(Result.Failure(PermissionServiceUnavailable("down")), result)
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `execute should fail without querying when filters are invalid`() {
        val query = ListSnippetsQuery(user = "test-user", filters = mapOf("foo" to "bar"))
        val failure = Result.Failure(InvalidInput("Unknown filter 'foo'"))

        Mockito.`when`(filterValidator.validate(query.filters)).thenReturn(failure)

        assertEquals(failure, useCase.execute(query))
        Mockito.verifyNoInteractions(snippetQueryRepository)
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
            .`when`(snippetQueryRepository.findAll(query, accessibleIds))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query, accessibleIds)
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
            .`when`(snippetQueryRepository.findAll(query, accessibleIds))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query, accessibleIds)
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
            .`when`(snippetQueryRepository.findAll(query, accessibleIds))
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
            .`when`(snippetQueryRepository.findAll(query, accessibleIds))
            .thenReturn(expectedResult)

        val result = useCase.execute(query)

        assertEquals(Result.Success(expectedResult), result)
        Mockito.verify(snippetQueryRepository).findAll(query, accessibleIds)
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

    @Test
    fun `execute should show the owners' names, looked up once per page`() {
        val query = ListSnippetsQuery(user = "test-user")
        val olgas =
            listOf(createSnippetListItem("A"), createSnippetListItem("B")).map {
                it.copy(owner = OLGA, ownerName = OLGA)
            }
        val page = PageResult(olgas, 0, 20, 2, 1)
        Mockito.`when`(snippetQueryRepository.findAll(query, accessibleIds)).thenReturn(page)

        val result = (useCase.execute(query) as Result.Success).value

        assertEquals(listOf("Olga Owner", "Olga Owner"), result.content.map { it.ownerName })
        assertEquals(listOf(setOf(OLGA)), directory.lookups)
    }

    @Test
    fun `execute should fall back to the owner id when the directory is down`() {
        directory.failure = UserDirectoryUnavailable("down")
        val query = ListSnippetsQuery(user = "test-user")
        val page = PageResult(listOf(createSnippetListItem("A")), 0, 20, 1, 1)
        Mockito.`when`(snippetQueryRepository.findAll(query, accessibleIds)).thenReturn(page)

        val result = (useCase.execute(query) as Result.Success).value

        assertEquals(listOf("test-user"), result.content.map { it.ownerName })
    }

    private fun createSnippetListItem(name: String): SnippetListItem =
        SnippetListItem(
            id = UUID.randomUUID(),
            name = name,
            description = "Test snippet",
            language = "Kotlin",
            languageVersion = "2.3",
            owner = "test-user",
            ownerName = "test-user",
            compliance = LintStatus.PENDING,
            processing = ProcessingStatus.PENDING,
        )

    private companion object {
        const val OLGA = "auth0|olga"
    }
}
