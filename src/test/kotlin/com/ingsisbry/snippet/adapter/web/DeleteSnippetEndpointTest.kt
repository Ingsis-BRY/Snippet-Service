package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContent
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DeleteSnippetEndpointTest {
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
                updateSnippetContent = Mockito.mock(UpdateSnippetContent::class.java),
                deleteSnippet = deleteSnippet,
                listSnippets = listSnippets,
                queryFactory = queryFactory,
                downloadSnippet = downloadSnippet,
                snippetDownloadMapper = snippetDownloadMapper,
                errorMapper = SnippetErrorMapper(),
            )
    }

    @Test
    fun `delete returns 204 when snippet exists`() {
        val snippetId = UUID.randomUUID()

        Mockito
            .`when`(deleteSnippet.execute(snippetId))
            .thenReturn(Result.Success(Unit))

        val response = controller.delete(snippetId)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        Mockito.verify(deleteSnippet).execute(snippetId)
    }

    @Test
    fun `delete maps a missing snippet to 404`() {
        val snippetId = UUID.randomUUID()

        Mockito
            .`when`(deleteSnippet.execute(snippetId))
            .thenReturn(Result.Failure(SnippetNotFound(snippetId)))

        val response = controller.delete(snippetId)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)

        Mockito.verify(deleteSnippet).execute(snippetId)
    }
}
