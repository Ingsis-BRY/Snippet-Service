package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.UpdateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UpdateSnippetEndpointTest {
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

    private fun createTestSnippet(): Snippet {
        val snippetId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        return Snippet(
            id = snippetId,
            name = "Example",
            description = "Example snippet",
            language = "PrintScript",
            languageVersion = "1.0",
            owner = "Anonymous",
            currentVersion =
                SnippetVersion.first(
                    id = versionId,
                    snippetId = snippetId,
                    originalContent = "println(1)",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
        )
    }

    private fun updateRequest() =
        UpdateSnippetRequest(
            name = "Updated example",
            description = "Updated description",
            language = "PrintScript",
            languageVersion = "1.0",
            content = "println(2)",
        )

    private fun commandFor(
        snippetId: UUID,
        request: UpdateSnippetRequest,
    ) = UpdateSnippetCommand(
        id = snippetId,
        name = request.name,
        description = request.description,
        language = request.language,
        languageVersion = request.languageVersion,
        content = request.content,
    )

    @Test
    fun `update returns 200 with the updated snippet`() {
        val snippet = createTestSnippet()
        val request = updateRequest()
        val command = commandFor(snippet.id, request)

        val updatedSnippet =
            snippet.copy(
                name = request.name,
                description = request.description,
                language = request.language,
                languageVersion = request.languageVersion,
            )

        Mockito
            .`when`(updateSnippet.execute(command))
            .thenReturn(Result.Success(updatedSnippet))

        val response = controller.update(snippet.id, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(UpdateSnippetResponse.from(updatedSnippet), response.body)

        Mockito.verify(updateSnippet).execute(command)
    }

    @Test
    fun `update maps a missing snippet to 404`() {
        val snippetId = UUID.randomUUID()
        val request = updateRequest()
        val command = commandFor(snippetId, request)

        Mockito
            .`when`(updateSnippet.execute(command))
            .thenReturn(Result.Failure(SnippetNotFound(snippetId)))

        val response = controller.update(snippetId, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)

        Mockito.verify(updateSnippet).execute(command)
    }
}
