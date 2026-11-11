package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.download.SnippetContentVariant
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
import org.springframework.http.ResponseEntity
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DownloadSnippetEndpointTest {
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
    fun `download returns the mapped response`() {
        val snippetId = UUID.randomUUID()
        val content = "println(1)"
        val variant = SnippetContentVariant.ORIGINAL
        val expectedResponse = ResponseEntity.ok(content.toByteArray())

        Mockito
            .`when`(snippetDownloadMapper.toVariant("original"))
            .thenReturn(Result.Success(variant))

        Mockito
            .`when`(downloadSnippet.execute(snippetId, variant))
            .thenReturn(Result.Success(content))

        Mockito
            .`when`(
                snippetDownloadMapper.toResponse(
                    snippetId = snippetId,
                    variant = variant,
                    content = content,
                ),
            ).thenReturn(expectedResponse)

        val response = controller.download(snippetId, "original")

        assertEquals(expectedResponse, response)

        Mockito.verify(downloadSnippet).execute(snippetId, variant)
    }

    @Test
    fun `download maps a missing snippet to 404`() {
        val snippetId = UUID.randomUUID()
        val variant = SnippetContentVariant.ORIGINAL

        Mockito
            .`when`(snippetDownloadMapper.toVariant("original"))
            .thenReturn(Result.Success(variant))

        Mockito
            .`when`(downloadSnippet.execute(snippetId, variant))
            .thenReturn(Result.Failure(SnippetNotFound(snippetId)))

        val response = controller.download(snippetId, "original")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)

        Mockito.verify(snippetDownloadMapper, Mockito.never()).toResponse(
            snippetId = snippetId,
            variant = variant,
            content = "",
        )
    }

    @Test
    fun `download maps an unsupported variant to 400 without hitting the use case`() {
        val snippetId = UUID.randomUUID()

        Mockito
            .`when`(snippetDownloadMapper.toVariant("pdf"))
            .thenReturn(Result.Failure(InvalidInput("Unsupported snippet content variant: pdf")))

        val response = controller.download(snippetId, "pdf")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        Mockito.verifyNoInteractions(downloadSnippet)
    }
}
