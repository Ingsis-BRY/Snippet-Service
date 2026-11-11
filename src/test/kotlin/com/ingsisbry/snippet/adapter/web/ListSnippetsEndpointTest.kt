package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.common.pagination.PageResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ListSnippetsEndpointTest {
    @Mock
    private lateinit var listSnippets: ListSnippets

    @Mock
    private lateinit var queryFactory: ListSnippetsQueryFactory

    private lateinit var controller: SnippetController

    @Mock
    private lateinit var createSnippet: CreateSnippet

    @Mock
    private lateinit var getSnippet: GetSnippet

    @Mock
    private lateinit var updateSnippet: UpdateSnippet

    @Mock
    private lateinit var deleteSnippet: DeleteSnippet

    @Mock
    private lateinit var downloadSnippet: DownloadSnippet

    @Mock
    private lateinit var snippetDownloadMapper: SnippetDownloadMapper

    @BeforeEach
    fun setUp() {
        controller =
            SnippetController(
                createSnippet = createSnippet,
                getSnippet = getSnippet,
                updateSnippet = updateSnippet,
                deleteSnippet = deleteSnippet,
                listSnippets = listSnippets,
                queryFactory = queryFactory,
                downloadSnippet = downloadSnippet,
                snippetDownloadMapper = snippetDownloadMapper,
                errorMapper = SnippetErrorMapper(),
            )
    }

    @Test
    fun `list should return snippets with default parameters`() {
        val parameters = emptyMap<String, String>()

        val query =
            ListSnippetsQuery(
                user = "anonymous",
                relationship = SnippetRelationship.ALL,
                filters = emptyMap(),
                page = 0,
                size = 20,
                sortBy = SnippetSortField.NAME,
                direction = SortDirection.ASC,
            )

        val item =
            SnippetListItem(
                id = UUID.randomUUID(),
                name = "Test snippet",
                description = "Test description",
                language = "Java",
                languageVersion = "21",
                owner = "anonymous",
                valid = true,
            )

        val pageResult =
            PageResult(
                content = listOf(item),
                page = 0,
                size = 20,
                totalElements = 1L,
                totalPages = 1,
            )

        Mockito
            .`when`(queryFactory.create(parameters, "anonymous"))
            .thenReturn(Result.Success(query))

        Mockito
            .`when`(listSnippets.execute(query))
            .thenReturn(Result.Success(pageResult))

        val response = controller.list(parameters)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(PageResponse.from(pageResult), response.body)

        Mockito.verify(queryFactory).create(parameters, "anonymous")
        Mockito.verify(listSnippets).execute(query)
    }

    @Test
    fun `list should use custom parameters`() {
        val parameters =
            mapOf(
                "relationship" to "OWNED",
                "page" to "2",
                "size" to "5",
                "sortBy" to "LANGUAGE",
                "direction" to "DESC",
                "language" to "Java",
            )

        val query =
            ListSnippetsQuery(
                user = "anonymous",
                relationship = SnippetRelationship.OWNED,
                filters = mapOf("language" to "Java"),
                page = 2,
                size = 5,
                sortBy = SnippetSortField.LANGUAGE,
                direction = SortDirection.DESC,
            )

        val pageResult = PageResult.empty<SnippetListItem>(page = 2, size = 5)

        Mockito
            .`when`(queryFactory.create(parameters, "anonymous"))
            .thenReturn(Result.Success(query))

        Mockito
            .`when`(listSnippets.execute(query))
            .thenReturn(Result.Success(pageResult))

        val response = controller.list(parameters)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(PageResponse.from(pageResult), response.body)

        Mockito.verify(queryFactory).create(parameters, "anonymous")
        Mockito.verify(listSnippets).execute(query)
    }

    @Test
    fun `list maps invalid parameters to 400 without hitting the use case`() {
        val parameters = mapOf("page" to "abc")

        Mockito
            .`when`(queryFactory.create(parameters, "anonymous"))
            .thenReturn(Result.Failure(InvalidInput("Parameter 'page' must be an integer")))

        val response = controller.list(parameters)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        Mockito.verifyNoInteractions(listSnippets)
    }
}
